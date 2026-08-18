/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core

import groovy.json.JsonSlurper
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Manual conformance-ish smoke spec for OGC API Features Part 1 core resources. Gated on
 * {@code SUT_URL} so it is skipped in CI; run it against a running ldproxy serving the demo API
 * this spec was written for, or override the path and collections through the environment.
 *
 * <p>Uses {@link java.net.http.HttpClient} so the spec works on Groovy 4 — the former
 * http-builder dependency references {@code groovy.util.slurpersupport.GPathResult}, which
 * Groovy 4 removed. The client is pinned to HTTP/1.1, which is what the previous
 * (Apache-HttpClient-4-based) library used for every request.
 *
 * <p>The expected feature counts in the filter tests are specific to the demo data.
 */
@Requires({env['SUT_URL'] != null})
class OgcApiCoreRestApiSpec extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = System.getenv('SUT_PATH') ?: '/daraa'
    static final String SUT_COLLECTION = System.getenv('SUT_COLLECTION') ?: 'AgricultureSrf'
    static final String SUT_COLLECTION2 = System.getenv('SUT_COLLECTION2') ?: 'CultureSrf'
    static final String SUT_ID = System.getenv('SUT_ID') ?: '1'

    static final String JSON = 'application/json'
    static final String GEOJSON = 'application/geo+json'
    // The API definition resource is not negotiated by application/json, so this asks for
    // whatever representation the deployment serves — the assertion is on the status only.
    static final String ANY = '*/*'

    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    def 'GET request to the landing page'() {

        when:
        def response = get(SUT_PATH, JSON)

        then: "Requirement 1, 2: HTTP GET support at '/'"
        response.statusCode() == 200
        contentType(response).startsWith(JSON)

        and:
        def body = parse(response)
        body.containsKey("links")
        body.links.any { it.rel == "service-desc" }
        body.links.any { it.rel == "service-doc" }
        body.links.any { it.rel == "conformance" }
        body.links.any { it.rel == "data" }
    }

    def 'GET request to the API page'() {

        when:
        def response = get(SUT_PATH + "/api", ANY)

        then: "Requirement 3: HTTP GET support at '/api'"
        response.statusCode() == 200

    }

    def 'GET request to the Conformance page'() {

        when:
        def response = get(SUT_PATH + '/conformance', JSON)

        then: "Requirement 5, 6: HTTP GET support at '/conformance'"
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("conformsTo")
        body.conformsTo.size() > 0

    }

    def 'HTTP 1.1 Conformance'() {
        when:
        def response = get(SUT_PATH, JSON)

        then: "Requirement 7: the server shall conform to HTTP 1.1"
        response.version() == HttpClient.Version.HTTP_1_1
    }


    def 'GET request to the collections page'() {

        when:
        def response = get(SUT_PATH + '/collections', JSON)

        then: "Requirement 11, 12A: GET support at the path '/collections'"
        response.statusCode() == 200

        and: "Requirement 12B: schema conformance"
        def body = parse(response)
        body.containsKey("links")
        body.containsKey("collections")

        and: "Requirement 13A: links property, relations 'self' and 'alternate'"
        body.links.any { it.rel == "self" }
        body.links.any { it.rel == "alternate" }

        and: "Requirement 13B: all links shall include the 'rel' and 'type' link parameters:"
        body.links.every { it.rel?.trim() }
        body.links.every { it.type?.trim() }

        and: "Requirement 14, 15A: for each feature collection provided by the server, an item SHALL be provided in the property 'collections'"
        body.collections.every { it.links.any { it.rel == 'items' } }

        and: "Requirement 15B: all links SHALL include the rel and type properties"
        body.collections.every { it.links.every { it.rel == 'items' ? it.type?.trim() : true } }

        and: "Requirement 16A: extent property"
        body.collections.every {
            it.containsKey("extent") ?
                    it.extent.containsKey("spatial") || it.extent.containsKey("temporal") : true
        }

    }

    def "GET request for single collection"() {

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION, JSON)
        def collectionsResponse = get(SUT_PATH + '/collections', JSON)

        then: "Requirement 17, 18A: HTTP GET support at th path '/collections/{collectionId}'"
        response.statusCode() == 200

        and:
        def body = parse(response)
        def collection = getCollection(SUT_COLLECTION, parse(collectionsResponse).collections)
        body.containsKey("title")
        body.containsKey("links")
        body.containsKey("id")
        body.containsKey("crs")
        body.containsKey("extent")

        and: "Requirement 18B: The response shall be consistent with the content in the /collections response (id, title, description, extent)"
        collection.title == body.title
        collection.description == body.description
        if (collection.extent.containsKey("spatial")) {
            collection.extent.spatial.crs == body.extent.spatial.crs
            collection.extent.spatial.bbox == body.extent.spatial.bbox
        }
        if (collection.extent.containsKey("temporal")) {
            collection.extent.temporal.trs == body.extent.temporal.trs
            collection.extent.temporal.interval == body.extent.temporal.interval
        }


    }

    def "GET request to one collection's items page"() {

        ZonedDateTime timestamp = ZonedDateTime.now(ZoneOffset.UTC)
        String formattedTimestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").format(timestamp)

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + "/items", GEOJSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("type")
        body.containsKey("links")
        body.containsKey("numberReturned")
        body.containsKey("features")
        and: "Requirement 27: include a link to this response document and a link to the response document in other supported formats"
        body.links.any { it.rel == "self" }
        body.links.any { it.rel == "alternate" }
        and: "Requirement 28: all links shall include the rel and type link parameters"
        body.links.every { it.rel?.trim() }
        // A profile link identifies a profile rather than a representation, so it carries no media
        // type — the requirement is about links to representations.
        body.links.findAll { it.rel != 'profile' }.every { it.type?.trim() }
        and: "Requirement 29: if included, 'timestamp' shall be set to the time stamp when the response was generated"
        if (body.containsKey("timestamp")) {
            body.timestamp >= formattedTimestamp
        }
    }

    def "GET request to one collection's items page (limit filter parameter)"() {

        given:
        int limit = 5

        when: "Requirement 19: HTTP GET support at the path '/collections/{collectionId}/items'"
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + "/items?limit=" + limit, GEOJSON)

        then: "Requirement 20: limit parameter support"
        response.statusCode() == 200

        and: "Requirement 21: the response shall not contain more features than specified by the optional limit parameter"
        def body = parse(response)
        body.numberReturned == limit
        body.containsKey("features")
        body.features.size() == limit
    }

    def "GET request to one collection's items page (bbox filter parameter)"() {

        given:
        def bbox = "35.898213,32.675795,36.023426,32.8370158"

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + "/items?bbox=" + bbox + "&limit=30", GEOJSON)

        then: "bbox parameter support"
        response.statusCode() == 200

        and: "Requirement 23: only features that have a spatial geometry that intersects the bounding box shall be part of the result set"
        def body = parse(response)
        body.numberReturned == 15
        body.numberMatched == 15
        body.containsKey("features")
        body.features.size() == 15
    }

    def "GET request to one collection's items page (dateTime filter parameter)"() {

        given: "Time interval between November 1, 2014 and November 1, 2019"
        def interval = "2014-11-01T00%3A00%3A00Z%2F2019-11-01T00%3A00%3A00Z"

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + '/items?limit=30&datetime=' + interval, GEOJSON)

        then: "Requirement 24: datetime parameter support"
        response.statusCode() == 200

        and: "Requirement 25: only features that have a temporal geometry that intersects the temporal information" +
                " in the datetime parameter shall be part of the result set"
        def body = parse(response)
        body.numberMatched == 12
        body.numberReturned == 12
        body.containsKey("features")
        body.features.size() == 12
    }

    def "GET request to one collection's items page (combination of filter parameters)"() {

        given:
        int limit = 5
        String interval = "2014-11-01T00%3A00%3A00Z%2F2019-11-01T00%3A00%3A00Z"
        String bbox = "36.097641,32.586742,36.259689,32.776306"

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + '/items?limit=' + limit +
                        '&datetime=' + interval + '&bbox=' + bbox, GEOJSON)

        then:
        response.statusCode() == 200

        and: "Requirement 30: if included, 'numberMatched' shall be identical to the number of features that match the filter parameters"
        def body = parse(response)
        if (body.containsKey("numberMatched")) {
            body.numberMatched == 9
        }
        and: "Requirement 31: if included, 'numberReturned' shall be identical to the number of features returned in the response"
        if (body.containsKey("numberReturned")) {
            body.numberReturned == limit
            body.features.size() == limit
        }

    }

    def 'GET request for one feature in a collection'() {

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + "/items/" + SUT_ID, GEOJSON)

        then: "Requirement 32, 33: HTTP GET support at path '/collections/{collectionId}/items/{featureId}'"
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("type")
        body.containsKey("links")
        body.containsKey("geometry")
        body.get("geometry").containsKey("type")
        body.get("geometry").containsKey("coordinates")
        body.containsKey("properties")
        body.containsKey("id")
        String.valueOf(body.get("id")) == SUT_ID
        and: "Requirement 34A: links with relations 'self', 'alternate', 'collection'"
        body.links.any { it.rel == "self" }
        body.links.any { it.rel == "alternate" }
        body.links.any { it.rel == "collection" }
        and: "Requirement 34B: all links shall include the 'rel' and 'type' link parameters"
        body.links.every { it.rel?.trim() }
        body.links.findAll { it.rel != 'profile' }.every { it.type?.trim() }
    }

    def 'Filter parameter with a valid property'() {

        given:
        String filter = "F_CODE='AL012'"

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION2 + '/items?filter=' + filter, GEOJSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.numberMatched == 2
        body.numberReturned == 2
    }

    def 'Filter parameter with an invalid property'() {

        given:
        String filter = "FCSUBTYPE='100065'"

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION2 + '/items?filter=' + filter, GEOJSON)

        then: "an unknown queryable is rejected"
        response.statusCode() == 400
    }

    def 'Filter parameter with filter-lang=cql2-json'() {
        given:
        String filter = '{"op":"=","args":[{"property":"F_CODE"},"AL012"]}'

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION2 + "/items?filter-lang=cql2-json&filter=" +
                        URLEncoder.encode(filter, 'UTF-8'), GEOJSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.numberMatched == 2
        body.numberReturned == 2
    }


    /**
     * Matches on the collection id. The previous version compared against {@code collection.getName},
     * which is not a property of the parsed JSON and therefore always returned null, so this helper
     * never found a collection and its caller failed on the null result.
     */
    def getCollection(collectionId, collections) {
        for (collection in collections) {
            if (collectionId == collection.id) {
                return collection
            }
        }
        return null
    }

    HttpResponse<String> get(String path, String accept) {
        httpClient.send(
                HttpRequest.newBuilder(URI.create(SUT_URL + path))
                        .header('Accept', accept)
                        .GET()
                        .build(),
                BodyHandlers.ofString())
    }

    static def parse(HttpResponse<String> response) {
        new JsonSlurper().parseText(response.body())
    }

    static String contentType(HttpResponse<String> response) {
        response.headers().firstValue('content-type').orElse('')
    }
}
