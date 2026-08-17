/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles

import groovy.json.JsonSlurper
import spock.lang.Ignore
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Manual smoke spec for the tiles resources. Gated on {@code SUT_URL} so it is skipped in CI; run
 * it against a running ldproxy serving the demo API this spec was written for, or override the path
 * and collections through the environment.
 *
 * <p>Uses {@link java.net.http.HttpClient} so the spec works on Groovy 4 — the former
 * http-builder dependency references {@code groovy.util.slurpersupport.GPathResult}, which
 * Groovy 4 removed.
 *
 * <p>The tile coordinates and expected contents are specific to the demo data.
 */
@Requires({env['SUT_URL'] != null})
class TilesRESTApiSpec extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = System.getenv('SUT_PATH') ?: '/daraa'
    static final String SUT_TILE_MATRIX_SET_ID = System.getenv('SUT_TILE_MATRIX_SET_ID') ?: 'WebMercatorQuad'
    static final String SUT_COLLECTION = System.getenv('SUT_COLLECTION') ?: 'aeronauticcrv'
    static final String SUT_COLLECTION2 = System.getenv('SUT_COLLECTION2') ?: 'aeronauticsrf'

    static final String JSON = 'application/json'
    static final String GEOJSON = 'application/geo+json'
    static final String MVT = 'application/vnd.mapbox-vector-tile'

    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()


    def 'GET Request for the /tileMatrixSets Page'() {

        when:
        def response = get(SUT_PATH + '/tileMatrixSets', JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileMatrixSets")
        body.get("tileMatrixSets").get(0).get("id") == "WebMercatorQuad"
        body.get("tileMatrixSets").get(0).get("links").get(0).get("rel") == "item"
        body.get("tileMatrixSets").get(0).get("links").get(0).get("href") == SUT_URL + SUT_PATH + "/tileMatrixSets/WebMercatorQuad"
    }

    def 'GET Request for the tile matrix set Page from tileMatrixSets'() {

        when:
        def response = get(SUT_PATH + '/tileMatrixSets/' + SUT_TILE_MATRIX_SET_ID, JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileMatrix")
        body.containsKey("boundingBox")
        body.containsKey("identifier")
        body.containsKey("supportedCRS")
        body.containsKey("title")
        body.containsKey("type")
        body.containsKey("wellKnownScaleSet")
        body.get("identifier") == "WebMercatorQuad"
    }

    def 'GET Request for the tiles Page'() {

        when:
        def response = get(SUT_PATH + '/tiles', JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileMatrixSetLinks")
        body.get("tileMatrixSetLinks").get(0).get("tileMatrixSet") == "WebMercatorQuad"
        body.get("links").any { it.href.contains("/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}") }
        body.get("tileMatrixSetLinks").get(0).get("tileMatrixSetLimits").size() > 0
    }

    def 'GET Request for a empty tile of the dataset'() {

        when:
        def response = getBytes(SUT_PATH + '/tiles/' + SUT_TILE_MATRIX_SET_ID + '/10/413/618', MVT)

        then:
        response.statusCode() == 200

        and: "the tile carries no content"
        response.body().length == 0
    }

    def 'GET Request for a non-empty tile of the dataset'() {

        when:
        def response = getBytes(SUT_PATH + '/tiles/' + SUT_TILE_MATRIX_SET_ID + '/10/413/614', MVT)

        then:
        response.statusCode() == 200
    }

    def 'GET Request for a tiles Page from a collection'() {

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles", JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileMatrixSetLinks")
        body.get("tileMatrixSetLinks").get(0).get("tileMatrixSet") == "WebMercatorQuad"
        body.get("links").any { it.href.contains("/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}") }
        body.get("tileMatrixSetLinks").get(0).get("tileMatrixSetLimits").size() > 0
    }

    def 'GET Request for a tile of a collection in json format'() {

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/10/413/615",
                GEOJSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("type")
        body.get("type") == "FeatureCollection"
        body.containsKey("links")
        body.containsKey("numberReturned")
        body.containsKey("numberMatched")
        body.containsKey("timeStamp")
        body.containsKey("features")
        body.features.size() > 0

    }

    def 'GET Request for a tile of a collection in json format, tile matrix set WorldCRS84Quad'() {

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/WorldCRS84Quad/10/325/1231", GEOJSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("type")
        body.get("type") == "FeatureCollection"
        body.containsKey("links")
        body.containsKey("numberReturned")
        body.containsKey("numberMatched")
        body.containsKey("timeStamp")
        body.containsKey("features")
        body.features.size() > 0

    }

    def 'GET Request for a tile of a collection in json format, tile matrix set WorldMercatorWGS84Quad'() {

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/WorldMercatorWGS84Quad/10/414/615", GEOJSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("type")
        body.get("type") == "FeatureCollection"
        body.containsKey("links")
        body.containsKey("numberReturned")
        body.containsKey("numberMatched")
        body.containsKey("timeStamp")
        body.containsKey("features")
        body.features.size() > 0

    }

    def 'GET Request for a tile of a collection in mvt format'() {

        when:
        def response = getBytes(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/10/413/614", MVT)

        then:
        response.statusCode() == 200
    }

    def 'Vector tiles conformance classes'() {
        when: "request to the conformance page"
        def response = get(SUT_PATH + '/conformance', JSON)

        then: "check conformance classes"
        response.statusCode() == 200
        def body = parse(response)
        body.containsKey("conformsTo")
        body.get("conformsTo").any { it == 'http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/core' }
        body.get("conformsTo").any { it == 'http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/collections' }
    }

    def 'Landing page request'() {
        when: "request to the landing page"
        def response = get(SUT_PATH, JSON)

        then: "the response shall contain links to the tileMatrixSets page"
        parse(response).get("links").any { it.rel == "tiling-schemes" && it.href.contains("/tileMatrixSets") }

    }

    def 'Unsupported request parameters (tileMatrixSet, tileMatrix, tileRow, tileCol)'() {
        when: "request tiles for a single collection"
        def response = get(requestPath, GEOJSON)

        then: "the request is rejected with a client error"
        // The previous version only required that the library raised for a non-2xx status, so the
        // exact code was never pinned down here; it differs per case (unknown tile matrix set vs.
        // a coordinate out of range).
        response.statusCode() >= 400

        where:
        requestPath                                                                                       | _
        SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + "foobar" + "/10/413/614"                 | "unknown Tile Matrix Set"
        SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/32/413/614"   | "tileMatrix out of range"
        SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/3/413/5"      | "tileRow out of range"
        SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/3/5/614"      | "tileCol out of range"
    }

    @Ignore
    def 'Tiles multitiles request'() {

        when: "request multitiles for a single collection"
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID +
                        '?scaleDenominator=6.5,7.5' +
                        '&bbox=333469.2232,6565023.4598,815328.2182,7298818.9635' +
                        '&multiTileType=url', JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileSet")
        body.get("tileSet").size() == 8
        body.get("tileSet").get(0).containsKey("tileURL")
        body.get("tileSet").get(0).containsKey("tileMatrix")
        body.get("tileSet").get(0).containsKey("tileRow")
        body.get("tileSet").get(0).containsKey("tileCol")
        body.get("tileSet").get(0).get("tileURL").contains("f=json")
    }

    @Ignore
    def 'Tiles collection multitiles request'() {
        when: "request multitiles for a single collection"
        def response = get(
                SUT_PATH + '/tiles/' + SUT_TILE_MATRIX_SET_ID +
                        '?scaleDenominator=6.5,7.5' +
                        '&bbox=333469.2232,6565023.4598,815328.2182,7298818.9635' +
                        '&multiTileType=url' +
                        '&collections=' + SUT_COLLECTION + ',' + SUT_COLLECTION2, JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileSet")
        body.get("tileSet").size() == 8
        body.get("tileSet").get(0).containsKey("tileURL")
        body.get("tileSet").get(0).containsKey("tileMatrix")
        body.get("tileSet").get(0).containsKey("tileRow")
        body.get("tileSet").get(0).containsKey("tileCol")
        body.get("tileSet").get(0).get("tileURL").contains("collections=" + SUT_COLLECTION + "," + SUT_COLLECTION2)
    }

    def 'GET Request to the Tiles page for multitiles URI template'() {

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles", JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey("tileMatrixSetLinks")
        body.get("tileMatrixSetLinks").any { it.tileMatrixSet == "WebMercatorQuad" }
        body.get("tileMatrixSetLinks").any { it.tileMatrixSet == "WorldCRS84Quad" }
        body.get("tileMatrixSetLinks").any { it.tileMatrixSet == "WorldMercatorWGS84Quad" }


    }

    def 'filter parameter support'() {

        when:
        def response = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/WebMercatorQuad/10/413/615" +
                        "?filter=" + URLEncoder.encode('fcsubtype=100454', 'UTF-8'), GEOJSON)

        then:
        response.statusCode() == 200

        and:
        parse(response).features.size() > 0

    }

    def 'filter-lang parameter'() {

        when:
        def response_correct = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/WebMercatorQuad/10/413/615" +
                        "?filter-lang=cql-text", GEOJSON)


        then:
        response_correct.statusCode() == 200

    }

    def 'invalid filter-lang parameter'() {
        when:
        def response_incorrect = get(
                SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/WebMercatorQuad/10/413/615" +
                        "?filter-lang=foobar", GEOJSON)

        then: "the request is rejected with a client error"
        response_incorrect.statusCode() >= 400

    }

    HttpResponse<String> get(String path, String accept) {
        httpClient.send(request(path, accept), BodyHandlers.ofString())
    }

    HttpResponse<byte[]> getBytes(String path, String accept) {
        httpClient.send(request(path, accept), BodyHandlers.ofByteArray())
    }

    private static HttpRequest request(String path, String accept) {
        HttpRequest.newBuilder(URI.create(SUT_URL + path))
                .header('Accept', accept)
                .GET()
                .build()
    }

    static def parse(HttpResponse<String> response) {
        new JsonSlurper().parseText(response.body())
    }
}
