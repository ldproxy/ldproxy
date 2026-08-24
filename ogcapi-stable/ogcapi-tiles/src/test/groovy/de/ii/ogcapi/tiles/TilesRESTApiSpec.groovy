/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles

import groovy.json.JsonSlurper
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Manual smoke spec for the tiles resources, written against OGC API - Tiles - Part 1: Core
 * (OGC 20-057). Gated on {@code SUT_URL} so it is skipped in CI; run it against a running ldproxy
 * whose API has the TILES building block, overriding the path, the tiling scheme and the collection
 * through the environment if needed.
 *
 * <p>Uses {@link java.net.http.HttpClient} so the spec works on Groovy 4 — the former
 * http-builder dependency references {@code groovy.util.slurpersupport.GPathResult}, which
 * Groovy 4 removed.
 *
 * <p>Tile coordinates are derived from the {@code tileMatrixSetLimits} the tileset advertises
 * rather than hard-coded, so the spec follows the data instead of assuming a particular extent.
 * The previous version asserted the resources of an earlier draft — a {@code tileMatrixSetLinks}
 * array, {@code identifier} instead of {@code id}, an {@code item} relation on the tiling-scheme
 * list, GeoJSON tiles — none of which the standard defines.
 */
@Requires({env['SUT_URL'] != null})
class TilesRESTApiSpec extends Specification {

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = System.getenv('SUT_PATH') ?: '/daraa'
    static final String SUT_TILE_MATRIX_SET_ID = System.getenv('SUT_TILE_MATRIX_SET_ID') ?: 'WebMercatorQuad'
    static final String SUT_COLLECTION = System.getenv('SUT_COLLECTION') ?: 'AeronauticCrv'

    static final String JSON = 'application/json'
    static final String MVT = 'application/vnd.mapbox-vector-tile'

    static final String REL_TILING_SCHEME = 'http://www.opengis.net/def/rel/ogc/1.0/tiling-scheme'
    static final String REL_TILING_SCHEMES = 'http://www.opengis.net/def/rel/ogc/1.0/tiling-schemes'
    static final String REL_TILESETS_VECTOR = 'http://www.opengis.net/def/rel/ogc/1.0/tilesets-vector'

    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    def 'the tiling schemes list contains the tiling scheme under test'() {

        when:
        def response = get(SUT_PATH + '/tileMatrixSets', JSON)

        then:
        response.statusCode() == 200

        and: "each entry identifies a tiling scheme and links to it"
        def body = parse(response)
        body.containsKey('tileMatrixSets')
        body.tileMatrixSets.every { it.id?.trim() && it.title?.trim() }
        body.tileMatrixSets.every { entry -> entry.links.any { it.rel == 'self' } }

        and:
        body.tileMatrixSets.any { it.id == SUT_TILE_MATRIX_SET_ID }
    }

    def 'the tiling scheme describes its tile matrices'() {

        when:
        def response = get(SUT_PATH + '/tileMatrixSets/' + SUT_TILE_MATRIX_SET_ID, JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.id == SUT_TILE_MATRIX_SET_ID
        body.containsKey('crs')
        body.containsKey('tileMatrices')
        body.tileMatrices.size() > 0

        and: "a tile matrix carries the scale and the size of the matrix"
        body.tileMatrices.every {
            it.id?.trim() && it.scaleDenominator > 0 &&
                    it.tileWidth > 0 && it.tileHeight > 0 &&
                    it.matrixWidth > 0 && it.matrixHeight > 0
        }
    }

    def 'the dataset tileset list contains a tileset for the tiling scheme'() {

        when:
        def response = get(SUT_PATH + '/tiles', JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey('tilesets')
        body.tilesets.size() > 0
        body.tilesets.every { it.tileMatrixSetId?.trim() && it.dataType?.trim() }

        and:
        body.tilesets.any { it.tileMatrixSetId == SUT_TILE_MATRIX_SET_ID }
    }

    def 'the dataset tileset describes how to fetch its tiles'() {

        when:
        def response = get(tilesetPath(), JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.tileMatrixSetId == SUT_TILE_MATRIX_SET_ID
        body.dataType == 'vector'
        body.tileMatrixSetLimits.size() > 0

        and: "the tileset links to itself and to the tiling scheme it uses"
        body.links.any { it.rel == 'self' }
        body.links.any { it.rel == REL_TILING_SCHEME && it.href.endsWith('/tileMatrixSets/' + SUT_TILE_MATRIX_SET_ID) }

        and: "and offers its tiles through a URI template"
        def item = body.links.find { it.rel == 'item' }
        item != null
        item.type == MVT
        item.href.contains('{tileMatrix}/{tileRow}/{tileCol}')
    }

    def 'a dataset tile inside the advertised limits is a vector tile'() {

        given:
        def limits = coarsestLimits(tilesetPath())

        when:
        def response = getBytes(tilePath(tilesetPath(), limits), MVT)

        then:
        response.statusCode() == 200

        and:
        contentType(response) == MVT
        response.body().length > 0
    }

    def 'a dataset tile outside the advertised limits is not found'() {

        given: "a column just past the last one the tileset advertises"
        def limits = coarsestLimits(tilesetPath())
        def path = String.format('%s/%s/%s/%s', tilesetPath(),
                limits.tileMatrix, limits.minTileRow, limits.maxTileCol + 1)

        when:
        def response = getBytes(path, MVT)

        then:
        response.statusCode() == 404
    }

    def 'the collection tileset list contains a tileset for the tiling scheme'() {

        when:
        def response = get(SUT_PATH + '/collections/' + SUT_COLLECTION + '/tiles', JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.containsKey('tilesets')
        body.tilesets.any { it.tileMatrixSetId == SUT_TILE_MATRIX_SET_ID }
    }

    def 'a collection tile inside the advertised limits is a vector tile'() {

        given: "the collection advertises its own limits, which are narrower than the dataset's"
        def limits = coarsestLimits(collectionTilesetPath())

        when:
        def response = getBytes(tilePath(collectionTilesetPath(), limits), MVT)

        then:
        response.statusCode() == 200

        and:
        contentType(response) == MVT
        response.body().length > 0
    }

    def 'the landing page advertises the tilesets and the tiling schemes'() {

        when:
        def response = get(SUT_PATH, JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.links.any { it.rel == REL_TILESETS_VECTOR }
        body.links.any { it.rel == REL_TILING_SCHEMES }
    }

    def 'the conformance declaration includes the tiles conformance classes'() {

        when:
        def response = get(SUT_PATH + '/conformance', JSON)

        then:
        response.statusCode() == 200

        and:
        def body = parse(response)
        body.conformsTo.contains('http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/core')
        body.conformsTo.contains('http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tileset')
        body.conformsTo.contains('http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tilesets-list')

        and: "the tiles are vector tiles here"
        body.conformsTo.contains('http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/mvt')
    }

    String tilesetPath() {
        SUT_PATH + '/tiles/' + SUT_TILE_MATRIX_SET_ID
    }

    String collectionTilesetPath() {
        SUT_PATH + '/collections/' + SUT_COLLECTION + '/tiles/' + SUT_TILE_MATRIX_SET_ID
    }

    /**
     * The limits of the coarsest tile matrix the tileset advertises. Reading the limits keeps the
     * spec independent of the data extent, and the coarsest level is the one where a tile is
     * certain to carry content: the limits describe the bounding rectangle of tiles that intersect
     * the data, not that every tile inside that rectangle holds features, so at a deep zoom the
     * corner tile is legitimately empty (the API answers 200 with an empty tile).
     */
    def coarsestLimits(String tilesetPath) {
        def limits = parse(get(tilesetPath, JSON)).tileMatrixSetLimits
        assert limits.size() > 0: "the tileset at ${tilesetPath} advertises no tileMatrixSetLimits"
        return limits.min { it.tileMatrix as int }
    }

    static String tilePath(String tilesetPath, limits) {
        String.format('%s/%s/%s/%s', tilesetPath, limits.tileMatrix, limits.minTileRow, limits.minTileCol)
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

    static String contentType(HttpResponse response) {
        response.headers().firstValue('content-type').orElse('').split(';')[0].trim()
    }
}
