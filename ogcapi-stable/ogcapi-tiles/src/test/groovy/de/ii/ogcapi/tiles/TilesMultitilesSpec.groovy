/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles

import de.ii.xtraplatform.tiles.app.TileMatrixSetRepositoryImpl
import de.ii.xtraplatform.tiles.domain.TileMatrixSet
import spock.lang.Ignore
import spock.lang.Specification

import jakarta.ws.rs.NotFoundException

@Ignore
//TODO
class TilesMultitilesSpec extends Specification {

    def "Test bbox parameter parsing"() {
        when:
        TileMatrixSet tileMatrixSet = TileMatrixSetRepositoryImpl.fromWellKnownId("WebMercatorQuad")

        def bbox = MultitilesUtils.parseBbox(bboxString, tileMatrixSet)

        then:
        bbox == expectedResult

        where:
        bboxString                 | expectedResult
        "50.0, 140.0, 110.24, 175" | [50.0, 140.0, 110.24, 175.0] as double[]
        ""                         | [-20037508.3427892, -20037508.3427892, 20037508.3427892, 20037508.3427892] as double[]
        null                       | [-20037508.3427892, -20037508.3427892, 20037508.3427892, 20037508.3427892] as double[]
    }

    def "Incorrect values of bbox parameter"() {
        when:
        def bbox = MultitilesUtils.parseBbox(bboxString, TileMatrixSetRepositoryImpl.fromWellKnownId("WebMercatorQuad"))

        then:
        thrown(NotFoundException)

        where:
        bboxString                            | _
        "50.0, 140.0, 110.24"                 | _
        "50.0, 140.0, 110.24, 160.25, -10.55" | _

    }

    def "Test scaleDenominator parameter parsing"() {
        when:
        def tileMatrices = MultitilesUtils.parseScaleDenominator(scaleDenominator, TileMatrixSetRepositoryImpl.fromWellKnownId("WebMercatorQuad"))

        then:
        tileMatrices == expectedResult

        where:
        scaleDenominator | expectedResult
        "0, 0.5"         | [0]
        "0.5, 4.5"       | [1, 2, 3, 4]
        "3, 10"          | [3, 4, 5, 6, 7, 8, 9]
        null             | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]
        ""               | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23]
    }

    def "Incorrect of out-of-range values of scaleDenominator parameter"() {
        when:
        def tileMatrices = MultitilesUtils.parseScaleDenominator(scaleDenominator, TileMatrixSetRepositoryImpl.fromWellKnownId("WebMercatorQuad"))

        then:
        thrown(NotFoundException)

        where:
        scaleDenominator | _
        "0, 0"           | _
        "0, 35.5"        | _
        "-35.5, 0"       | _
        "0.1,2.2,4.4"    | _
        "5.5"            | _
    }

    def "Conversion of longitude/latitude coordinates to tile coordinates for different tile matrix(level) values"() {
        when:
        def tile = MultitilesUtils.pointToTile(lon, lat, tileMatrix, TileMatrixSetRepositoryImpl.fromWellKnownId("WebMercatorQuad"))

        then:
        tile == expectedResult

        where:
        lon           | lat           | tileMatrix | expectedResult
        -960864.3911  | 6920710.4554  | 1          | [0, 0]
        3368936.9408  | 8388097.0703  | 4          | [4, 9]
        15556463.9966 | 4256829.0788  | 9          | [201, 454]
        -152.8554     | -305.7353     | 10         | [512, 511]
        -8123599.9684 | -6627491.6467 | 13         | [5450, 2435]
        16833220.5430 | -4009537.5609 | 18         | [157299, 241183]
    }

    def "f-tile request query parameter parsing"() {
        when:
        def tileFormat = MultitilesUtils.parseTileFormat(ftileParam)

        then:
        tileFormat == expectedResult

        where:
        ftileParam | expectedResult
        null       | "json"
        ""         | "json"
        "mvt"      | "mvt"
        "json"     | "json"
    }

    def "incorrect/unsupported f-tile request parameter"() {
        when:
        MultitilesUtils.parseTileFormat(ftileParam)

        then:
        thrown(NotFoundException)

        where:
        ftileParam | _
        "gif"      | _
        "jsonx"    | _
    }

}
