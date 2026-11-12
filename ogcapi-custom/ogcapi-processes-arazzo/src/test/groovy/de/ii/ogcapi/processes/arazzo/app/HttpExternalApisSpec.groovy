/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import static de.ii.ogcapi.processes.arazzo.app.FakeHttpClient.response

import spock.lang.Specification

class HttpExternalApisSpec extends Specification {

    static final String DOCUMENT = "https://api.example.com/v1/openapi.yaml"
    static final String YAML = '''
openapi: 3.1.0
info: {title: Example, version: "1"}
servers:
  - url: /{base}
    variables:
      base: {default: v1}
paths:
  /items:
    get:
      operationId: getItems
    post:
      operationId: addItem
    delete:
      operationId: deleteItems
  /special:
    servers:
      - url: https://other.example.com/v2
    get:
      operationId: getSpecial
'''

    FakeHttpClient http = new FakeHttpClient()

    HttpExternalApis apis(List<String> allowed = ["https://api.example.com/v1/"]) {
        return new HttpExternalApis(http.client(), allowed)
    }

    def setup() {
        http.responses[DOCUMENT] = { String method -> response(200, YAML, ["content-type": "application/yaml"]) }
    }

    def "the operations of an OpenAPI document have the server of the document, the path item or the operation"() {
        when:
        def api = apis().api(DOCUMENT, 1000)

        then:
        http.sent*.url == [DOCUMENT]
        http.sent[0].headers.accept.startsWith("application/vnd.oai.openapi+json")
        with(api.operation("getItems").get()) {
            apiId() == DOCUMENT
            method() == "GET"
            path() == "/items"
            server() == Optional.of("https://api.example.com/v1")
            supported
        }
        api.operation("POST", "/items").get().operationId() == "addItem"
        api.operation("getSpecial").get().server() == Optional.of("https://other.example.com/v2")
    }

    def "only GET and POST operations can be steps"() {
        expect:
        apis().api(DOCUMENT, 1000).operation(operationId).get().supported == supported

        where:
        operationId   || supported
        "getItems"    || true
        "addItem"     || true
        "deleteItems" || false
    }

    def "without servers, the server is the root of the location of the document"() {
        given:
        http.responses[DOCUMENT] = { String method -> response(200, '{"openapi": "3.0.3", "paths": {"/a": {"get": {"operationId": "a"}}}}') }

        expect:
        apis().api(DOCUMENT, 1000).operation("a").get().server() == Optional.of("https://api.example.com/")
    }

    def "a source description that is not allowed is rejected without a request"() {
        when:
        apis(allowed).api(DOCUMENT, 1000)

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.PROBLEM
        e.message.contains("not an allowed external API")
        http.sent.isEmpty()

        where:
        allowed << [[], ["https://api.example.com/v2/"], ["http://api.example.com/v1/"], ["https://api.example.com:8443/v1/"]]
    }

    def "a document that is not an OpenAPI 3 document or that cannot be read is rejected"() {
        given:
        http.responses[DOCUMENT] = { String method -> response(status, body) }

        when:
        apis().api(DOCUMENT, 1000)

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.PROBLEM
        e.message.contains(message)

        where:
        status | body                  || message
        200    | 'swagger: "2.0"'      || "not an OpenAPI 3 document"
        200    | '{"openapi": '        || "neither JSON nor YAML"
        404    | 'not found'           || "the status code is 404"
    }

    def "a step request has the URL of the server, the headers of the step and JSON content"() {
        given:
        http.responses["https://api.example.com/v1/items?q=a+b"] = { String method ->
            response(201, '{"id": 1}', ["content-type": "application/json", "location": "https://api.example.com/v1/items/1"]) }
        def operation = apis().api(DOCUMENT, 1000).operation("addItem").get()
        def request = new StepRequest(operation.apiId(), "addItem", "POST", "/items", [:], [q: "a b"], ["x-api-key": "k"], Optional.of([name: "n"]), operation.server())

        when:
        def response = apis().dispatch(request, 1000)

        then:
        with(http.sent.last()) {
            method == "POST"
            url == "https://api.example.com/v1/items?q=a+b"
            headers["x-api-key"] == "k"
            headers["content-type"] == "application/json"
            headers.accept.startsWith("application/json")
            body == '{"name":"n"}'
        }
        response.statusCode() == 201
        response.headers().location == "https://api.example.com/v1/items/1"
        response.body() == Optional.of([id: 1])
    }

    def "a step request to a URL that is not allowed is rejected without a request"() {
        given:
        def operation = apis().api(DOCUMENT, 1000).operation("getSpecial").get()
        http.sent.clear()

        when:
        apis().dispatch(new StepRequest(operation.apiId(), "getSpecial", "GET", "/special", [:], [:], [:], Optional.empty(), operation.server()), 1000)

        then:
        WorkflowException e = thrown()
        e.kind == WorkflowException.Kind.PROBLEM
        http.sent.isEmpty()
    }

    def "redirects are followed only to allowed URIs"() {
        given:
        http.responses["https://api.example.com/v1/a"] = { String method -> response(302, "", [location: "/v1/b"]) }
        http.responses["https://api.example.com/v1/b"] = { String method -> response(307, "", [location: "https://evil.example.com/c"]) }

        when:
        def response = apis().dispatch(get("/a"), 1000)

        then:
        http.sent*.url == ["https://api.example.com/v1/a", "https://api.example.com/v1/b"]
        response.statusCode() == 502
        response.body().get().detail.contains("redirect to 'https://evil.example.com/c' is not allowed")
    }

    def "a redirect to another host drops the authorization, a 303 continues with a GET"() {
        given:
        http.responses["https://api.example.com/v1/a"] = { String method -> response(303, "", [location: "https://cdn.example.com/b"]) }
        http.responses["https://cdn.example.com/b"] = { String method -> response(200, "plain") }
        def request = new StepRequest("x", "a", "POST", "/a", [:], [:], [authorization: "Bearer t"], Optional.of([a: 1]), Optional.of("https://api.example.com/v1"))

        when:
        def response = apis(["https://api.example.com/v1/", "https://cdn.example.com/"]).dispatch(request, 1000)

        then:
        http.sent[0].headers.authorization == "Bearer t"
        with(http.sent[1]) {
            method == "GET"
            body == null
            !headers.containsKey("authorization")
            !headers.containsKey("content-type")
        }
        response.statusCode() == 200
        response.body() == Optional.of("plain")
    }

    def "a request that fails, e.g. a response that is too large, is a response with the status code 502"() {
        when:
        def response = apis().dispatch(get("/unknown"), 1000)

        then:
        response.statusCode() == 502
        response.body().get().detail.contains("Connection refused")
    }

    static StepRequest get(String path) {
        return new StepRequest("x", "op", "GET", path, [:], [:], [:], Optional.empty(), Optional.of("https://api.example.com/v1"))
    }

    def "configured headers are added to the requests of their URI, a header of the step takes precedence"() {
        given:
        def configured = new HttpExternalApis(http.client(), new HttpExternalApis.Options(["https://api.example.com/v1/", "https://cdn.example.com/"],
                ["https://api.example.com/v1/": ["X-Api-Key": "server-key", "User-Agent": "ldproxy"]], [:], [:]), ExternalApiDocuments.NONE, "bag")
        http.responses["https://api.example.com/v1/items"] = { String method -> response(302, "", [location: "https://cdn.example.com/items"]) }
        http.responses["https://cdn.example.com/items"] = { String method -> response(200, "[]") }
        def request = new StepRequest("x", "getItems", "GET", "/items", [:], [:], ["user-agent": "step"], Optional.empty(), Optional.of("https://api.example.com/v1"))

        when:
        configured.api(DOCUMENT, 1000)
        configured.dispatch(request, 1000)

        then:
        http.sent[0].headers["x-api-key"] == "server-key"
        http.sent[1].headers["x-api-key"] == "server-key"
        http.sent[1].headers["user-agent"] == "step"
        // the redirect to another host drops the configured headers
        !http.sent[2].headers.containsKey("x-api-key")
        http.sent[2].headers["user-agent"] == null
    }

    def "a configured server replaces the servers of the document"() {
        given:
        def configured = new HttpExternalApis(http.client(), new HttpExternalApis.Options(["https://api.example.com/v1/"], [:], [(DOCUMENT): "https://api.example.com/v1/rules"], [:]), ExternalApiDocuments.NONE, "bag")

        when:
        def api = configured.api(DOCUMENT, 1000)

        then:
        api.operation("getItems").get().server() == Optional.of("https://api.example.com/v1/rules")
        api.operation("getSpecial").get().server() == Optional.of("https://api.example.com/v1/rules")
    }

    def "an OpenAPI document is kept as long as the configuration or its response allows"() {
        given:
        long now = 0
        def documents = new ExternalApiDocumentsImpl({ -> now })
        http.responses[DOCUMENT] = { String method -> response(200, YAML, cacheControl ? ["cache-control": cacheControl] : [:]) }
        def options = new HttpExternalApis.Options(["https://api.example.com/v1/"], [:], [:], configured)
        def apis = { String key -> new HttpExternalApis(http.client(), options, documents, key) }

        when:
        apis("bag").api(DOCUMENT, 1000)
        now = 59_000
        apis("bag").api(DOCUMENT, 1000)
        // the documents of another API are not shared
        apis("bgt").api(DOCUMENT, 1000)
        now = 61_000
        apis("bag").api(DOCUMENT, 1000)

        then:
        http.sent.size() == requests

        where:
        cacheControl                    | configured                           || requests
        "public, max-age=60"            | [:]                                  || 3
        "no-store, max-age=0"           | [:]                                  || 4
        "no-cache, max-age=60"          | [:]                                  || 4
        null                            | [:]                                  || 4
        "no-store, max-age=0"           | ["https://api.example.com/": 60]     || 3
        "public, max-age=60"            | ["https://api.example.com/v1/": 120] || 2
    }

    def "max-age is read from Cache-Control"() {
        expect:
        HttpExternalApis.maxAge(value) == seconds

        where:
        value                                              || seconds
        "public, max-age=3600, no-transform"               || 3600
        "Max-Age=\"10\""                                  || 10
        "no-cache, no-store, max-age=0, must-revalidate"   || 0
        "private"                                          || 0
        "max-age=x"                                        || 0
    }
}
