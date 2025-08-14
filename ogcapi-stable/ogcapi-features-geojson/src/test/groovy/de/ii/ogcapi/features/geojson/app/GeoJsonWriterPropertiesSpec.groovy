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
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.IntStream

class GeoJsonWriterPropertiesSpec extends Specification {

    @Shared
    FeatureSchema propertyMapping = new ImmutableFeatureSchema.Builder().name("p1").build()

    @Shared
    FeatureSchema propertyMapping2 = new ImmutableFeatureSchema.Builder().name("p2")
            .type(SchemaBase.Type.INTEGER)
            .build()

    @Shared
    String value1 = "val1"
    @Shared
    String value2 = "2"

    def "GeoJson writer properties middleware, value types (String)"() {
        given:
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
                "  \"features\" : [ {" + System.lineSeparator() +
                "    \"type\" : \"Feature\"," + System.lineSeparator() +
                "    \"properties\" : {" + System.lineSeparator() +
                "      \"p1\" : \"val1\"," + System.lineSeparator() +
                "      \"p2\" : 2" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  } ]" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, ["p1": propertyMapping, "p2": propertyMapping2],
                [[], []], [value1, value2])
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    // TODO update to current logic, add tests for objects and arrays
    def "GeoJson writer properties middleware, strategy is nested, one level depth"() {
        given:
        FeatureSchema mapping1 = new ImmutableFeatureSchema.Builder().name("foto.bemerkung")
                .type(SchemaBase.Type.STRING)
                .build()
        FeatureSchema mapping2 = new ImmutableFeatureSchema.Builder().name("foto.hauptfoto")
                .type(SchemaBase.Type.STRING)
                .build()
        FeatureSchema mapping3 = new ImmutableFeatureSchema.Builder().name("kennung")
                .type(SchemaBase.Type.STRING)
                .build()
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
                "  \"features\" : [ {" + System.lineSeparator() +
                "    \"type\" : \"Feature\"," + System.lineSeparator() +
                "    \"properties\" : {" + System.lineSeparator() +
                "      \"foto.bemerkung\" : \"xyz\"," + System.lineSeparator() +
                "      \"foto.hauptfoto\" : \"xyz\"," + System.lineSeparator() +
                "      \"kennung\" : \"xyz\"" + System.lineSeparator() +
                "    }" + System.lineSeparator() +
                "  } ]" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, ["foto.bemerkung": mapping1, "foto.hauptfoto": mapping2, "kennung": mapping3],
                ImmutableList.of(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()))
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    private static void runTransformer(ByteArrayOutputStream outputStream, Map<String, FeatureSchema> mappings,
                                       List<List<Integer>> multiplicities,
                                       List<String> values) throws IOException, URISyntaxException {
        outputStream.reset()
        EncodingAwareContextGeoJson context = GeoJsonWriterSetupUtil.createTransformationContext(outputStream, true)
        FeatureEncoderGeoJson encoder = new FeatureEncoderGeoJson(context.encoding(), ImmutableList.of(new GeoJsonWriterSkeleton(), new GeoJsonWriterProperties()));
        FeatureSchema featureSchema = new ImmutableFeatureSchema.Builder().name("test")
                .type(SchemaBase.Type.OBJECT)
                .putAllPropertyMap(mappings)
                .build();

        context.setIsUseTargetPaths(true)
                .setType("test")
                .setMappings(Map.of("test", SchemaMapping.of(featureSchema)))

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

    private static void runTransformer(ByteArrayOutputStream outputStream, Map<String, FeatureSchema> mappings,
                                       List<List<Integer>> multiplicities) throws IOException, URISyntaxException {
        String value = "xyz"
        runTransformer(outputStream, mappings, multiplicities, IntStream.range(0, mappings.size())
                .mapToObj { i -> value }
                .collect(Collectors.toList()))
    }
}
