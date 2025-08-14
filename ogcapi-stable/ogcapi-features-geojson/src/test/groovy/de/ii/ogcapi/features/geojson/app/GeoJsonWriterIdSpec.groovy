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
import spock.lang.Specification

class GeoJsonWriterIdSpec extends Specification {

    def "GeoJson writer properties middleware, value types (String)"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
                "  \"features\" : [ {" + System.lineSeparator() +
                "    \"type\" : \"Feature\"," + System.lineSeparator() +
                "    \"id\" : 2" + System.lineSeparator() +
                "  } ]" + System.lineSeparator() +
                "}"

        when:
        FeatureSchema mapping = new ImmutableFeatureSchema.Builder().name("p").type(SchemaBase.Type.INTEGER).role(SchemaBase.Role.ID).build()
        runTransformer(outputStream, ["p": mapping], [[]], ["2"])
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    private static void runTransformer(ByteArrayOutputStream outputStream, Map<String, FeatureSchema> mappings,
                                       List<List<Integer>> multiplicities,
                                       List<String> values) throws IOException, URISyntaxException {
        outputStream.reset()
        EncodingAwareContextGeoJson context = GeoJsonWriterSetupUtil.createTransformationContext(outputStream, true)
        FeatureEncoderGeoJson encoder = new FeatureEncoderGeoJson(context.encoding(), ImmutableList.of(new GeoJsonWriterSkeleton(), new GeoJsonWriterId()));
        FeatureSchema featureSchema = new ImmutableFeatureSchema.Builder().name("xyz")
                .type(SchemaBase.Type.OBJECT)
                .putAllPropertyMap(mappings)
                .build();

        context.setIsUseTargetPaths(true)
                .setType("xyz")
                .setMappings(Map.of("xyz", SchemaMapping.of(featureSchema)))

        encoder.onStart(context)
        encoder.onFeatureStart(context)

        for (int i = 0; i < mappings.size(); i++) {
            context.pathTracker().track(featureSchema.getProperties().get(i).getFullPath())
            context.setIndexes(multiplicities.get(i))
            context.setValue(values.get(i))
            encoder.onValue(context)
        }

        encoder.onFeatureEnd(context)

        encoder.onEnd(context)
    }
}
