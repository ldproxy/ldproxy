/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson
import de.ii.ogcapi.features.geojson.domain.FeatureEncoderGeoJson
import de.ii.xtraplatform.crs.domain.CrsTransformer
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class JsonFgWriterCrsSpec extends Specification {

    @Shared EpsgCrs DEFAULT_CRS = OgcCrs.CRS84
    @Shared EpsgCrs OTHER_CRS = EpsgCrs.of(4258)

    def "JsonFg writer CRS middleware for FeatureCollections if target is WGS84"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        boolean isCollection = true
        EpsgCrs crs = DEFAULT_CRS
        String expected = "{" + System.lineSeparator() +
                "  \""+ JsonFgWriterCrs.JSON_KEY+"\" : \""+crs.toAlternativeUriString().get()+"\"" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, isCollection, crs)
        String actual = new String(outputStream.toByteArray(), StandardCharsets.UTF_8)

        then:
        actual == expected
    }

    def "JsonFg writer CRS middleware for FeatureCollections if target is not WGS84"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        boolean isCollection = true
        EpsgCrs crs = OTHER_CRS
        String expected = "{" + System.lineSeparator() +
                "  \""+JsonFgWriterCrs.JSON_KEY+"\" : \""+crs.toUriString()+"\"" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, isCollection, crs)
        String actual = new String(outputStream.toByteArray(), StandardCharsets.UTF_8)

        then:
        actual == expected
    }


    def "JsonFg writer CRS middleware for single Features if target is WGS84"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        boolean isCollection = false
        EpsgCrs crs = DEFAULT_CRS
        String expected = "{" + System.lineSeparator() +
                "  \""+JsonFgWriterCrs.JSON_KEY+"\" : \""+crs.toAlternativeUriString().get()+"\"" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, isCollection, crs)
        String actual = new String(outputStream.toByteArray(), StandardCharsets.UTF_8)

        then:
        actual == expected
    }

    def "JsonFg writer CRS middleware for single Features if target is not WGS84"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        boolean isCollection = false
        EpsgCrs crs = OTHER_CRS
        String expected = "{" + System.lineSeparator() +
                "  \""+JsonFgWriterCrs.JSON_KEY+"\" : \""+crs.toUriString()+"\"" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, isCollection, crs)
        String actual = new String(outputStream.toByteArray(), StandardCharsets.UTF_8)

        then:
        actual == expected
    }

    private void runTransformer(ByteArrayOutputStream outputStream, boolean isCollection, EpsgCrs crs) throws IOException, URISyntaxException {
        outputStream.reset()
        CrsTransformer crsTransformer = null
        if (Objects.nonNull(crs)) {
            crsTransformer = Mock()
            crsTransformer.getTargetCrs() >> crs
        }
        EncodingAwareContextGeoJson context = JsonFgWriterSetupUtil.createTransformationContext(outputStream, isCollection, crsTransformer)
        FeatureEncoderGeoJson encoder = new FeatureEncoderGeoJson(context.encoding(), ImmutableList.of(new JsonFgWriterCrs()));

        context.encoding().getJson()
                .writeStartObject()
        encoder.onStart(context)
        context.setType("xyz")
        encoder.onFeatureStart(context)
        context.encoding().getJson()
                .writeEndObject()
        encoder.onEnd(context)
    }
}
