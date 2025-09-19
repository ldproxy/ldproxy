/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import de.ii.ogcapi.styles.domain.MbStyleStylesheet
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.CrsTransformationException
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.tiles.domain.TileMatrix
import de.ii.xtraplatform.tiles.domain.TileMatrixSet
import de.ii.xtraplatform.tiles.domain.TileMatrixSetData
import io.dropwizard.util.Resources
import spock.lang.Specification

class AdjustZoomLevelsSpec extends Specification{

    private static ObjectMapper MAPPER =
            new ObjectMapper()
                    .registerModule(new Jdk8Module())
                    .registerModule(new GuavaModule())
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    private static TileMatrixSet adv25832 = new TileMatrixSet() {
        @Override
        String getId() {
            return "AdV_25832"
        }

        @Override
        EpsgCrs getCrs() {
            return EpsgCrs.of(25832)
        }

        @Override
        int getMaxLevel() {
            return 14
        }

        @Override
        TileMatrixSetData getTileMatrixSetData() {
            return MAPPER.readValue(
                    Resources.getResourceAsStream("/tilematrixsets/AdV_25832.json"),
                    TileMatrixSetData.class)
        }

        @Override
        double getInitialScaleDenominator() {
            return 17471320.7508974
        }

        @Override
        int getInitialWidth() {
            return 1
        }

        @Override
        int getInitialHeight() {
            return 1
        }

        @Override
        BoundingBox getBoundingBox() {
            return BoundingBox.of(
                    -46133.17,
                    5048875.26857567,
                    1206211.10142433,
                    6301219.54,
                    EpsgCrs.of(25832))
        }

        @Override
        BoundingBox getBoundingBoxCrs84(CrsTransformerFactory crsTransformerFactory) throws CrsTransformationException {
            return null
        }

        @Override
        TileMatrix getTileMatrix(int level) {
            return getTileMatrixSetData()
                    .getTileMatrices()
                    .stream()
                    .filter(tm -> tm.tileLevel == level)
                    .findFirst()
                    .orElseThrow()
        }
    }

    def 'derive style for AdV_25832'() {

        given:

        MbStyleStylesheet stylesheet = MAPPER.readValue(Resources.getResourceAsStream("/styles/asb.json"), MbStyleStylesheet.class)
        String reference_AdV25832 = MAPPER.writeValueAsString(MAPPER.readValue(Resources.getResourceAsStream("/styles/asb_AdV25832.json"), MbStyleStylesheet.class))

        when:

        String derived = MAPPER.writeValueAsString(stylesheet.adjustForTileMatrixSetIfNecessary(Optional.of(adv25832), "http://localhost:7080/strassen"))

        then:

        derived == reference_AdV25832
    }
}
