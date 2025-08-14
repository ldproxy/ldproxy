/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson
import de.ii.ogcapi.features.geojson.domain.FeatureEncoderGeoJson
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.SchemaMapping
import spock.lang.Ignore
import spock.lang.Specification

class GeoJsonWriterSkeletonSpec extends Specification {

    def "GeoJsonWriterSkeleton middleware given a feature collection"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
                "  \"features\" : [ {" + System.lineSeparator() +
                "    \"type\" : \"Feature\"" + System.lineSeparator() +
                "  } ]" + System.lineSeparator() +
                "}"


        when:
        writeFeature(outputStream, true)
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }


    def "GeoJsonWriterSkeleton middleware given a single feature"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"Feature\"" + System.lineSeparator() +
                "}"


        when:
        writeFeature(outputStream, false)
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }


    private static void writeFeature(ByteArrayOutputStream outputStream,
                                     boolean isCollection) throws IOException, URISyntaxException {
        EncodingAwareContextGeoJson context = GeoJsonWriterSetupUtil.createTransformationContext(outputStream, isCollection)
        FeatureEncoderGeoJson encoder = new FeatureEncoderGeoJson(context.encoding(), ImmutableList.of(new GeoJsonWriterSkeleton()));
        FeatureSchema featureSchema = new ImmutableFeatureSchema.Builder().name("test")
                .type(SchemaBase.Type.OBJECT)
                .build();
        context.setIsUseTargetPaths(true)
                .setType("test")
                .setMappings(Map.of("test", SchemaMapping.of(featureSchema)))

        encoder.onStart(context)
        encoder.onFeatureStart(context)
        encoder.onFeatureEnd(context)
        encoder.onEnd(context)
    }

}
