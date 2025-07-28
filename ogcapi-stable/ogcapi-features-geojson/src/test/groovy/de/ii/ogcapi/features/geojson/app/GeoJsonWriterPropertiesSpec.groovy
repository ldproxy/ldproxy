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
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors
import java.util.stream.IntStream

@Ignore //TODO
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
                "  \"properties\" : {" + System.lineSeparator() +
                "    \"p1\" : \"val1\"," + System.lineSeparator() +
                "    \"p2\" : 2" + System.lineSeparator() +
                "  }" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, ["p1": propertyMapping, "p2": propertyMapping2],
                [[], []], [value1, value2])
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    @Ignore
    //TODO
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
                "  \"properties\" : {" + System.lineSeparator() +
                "    \"foto\" : {" + System.lineSeparator() +
                "      \"bemerkung\" : \"xyz\"," + System.lineSeparator() +
                "      \"hauptfoto\" : \"xyz\"" + System.lineSeparator() +
                "    }," + System.lineSeparator() +
                "    \"kennung\" : \"xyz\"" + System.lineSeparator() +
                "  }" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, ImmutableList.of(mapping1, mapping2, mapping3),
                ImmutableList.of(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()))
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    @Ignore
    //TODO
    def "GeoJson writer properties middleware, strategy is nested, one level depth with multiplicity"() {
        given:
        // multiple object
        FeatureSchema mapping1 = new ImmutableFeatureSchema.Builder().name("foto[foto].bemerkung")
                .type(SchemaBase.Type.STRING)
                .build()
        List<Integer> multiplicity11 = ImmutableList.of(1)
        List<Integer> multiplicity12 = ImmutableList.of(2)

        FeatureSchema mapping2 = new ImmutableFeatureSchema.Builder().name("foto[foto].hauptfoto")
                .type(SchemaBase.Type.STRING)
                .build()
        List<Integer> multiplicity21 = ImmutableList.of(1)
        List<Integer> multiplicity22 = ImmutableList.of(2)

        // multiple value
        FeatureSchema mapping3 = new ImmutableFeatureSchema.Builder().name("fachreferenz[fachreferenz]")
                .type(SchemaBase.Type.STRING)
                .build()
        List<Integer> multiplicity31 = ImmutableList.of(1)
        List<Integer> multiplicity32 = ImmutableList.of(2)

        //TODO if lastPropertyIsNested
        FeatureSchema mapping4 = new ImmutableFeatureSchema.Builder().name("kennung")
                .type(SchemaBase.Type.STRING)
                .build()

        ImmutableList<FeatureSchema> mappings = ImmutableList.of(mapping1, mapping2, mapping1, mapping2, mapping3, mapping3, mapping4)
        ImmutableList<List<Integer>> multiplicities = ImmutableList.of(multiplicity11, multiplicity21, multiplicity12, multiplicity22, multiplicity31, multiplicity32, ImmutableList.of())
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"properties\" : {" + System.lineSeparator() +
                "    \"foto\" : [ {" + System.lineSeparator() +
                "      \"bemerkung\" : \"xyz\"," + System.lineSeparator() +
                "      \"hauptfoto\" : \"xyz\"" + System.lineSeparator() +
                "    }, {" + System.lineSeparator() +
                "      \"bemerkung\" : \"xyz\"," + System.lineSeparator() +
                "      \"hauptfoto\" : \"xyz\"" + System.lineSeparator() +
                "    } ]," + System.lineSeparator() +
                "    \"fachreferenz\" : [ \"xyz\", \"xyz\" ]," + System.lineSeparator() +
                "    \"kennung\" : \"xyz\"" + System.lineSeparator() +
                "  }" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, mappings, multiplicities)
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    @Ignore
    //TODO
    def "GeoJson writer properties middleware, strategy is nested, two level depth with multiplicity"() {
        given:
        // multiple object
        FeatureSchema mapping1 = new ImmutableFeatureSchema.Builder().name("raumreferenz[raumreferenz].datumAbgleich")
                .type(SchemaBase.Type.STRING)
                .build()
        List<Integer> multiplicity11 = ImmutableList.of(1)

        FeatureSchema mapping2 = new ImmutableFeatureSchema.Builder().name("raumreferenz[raumreferenz].ortsangaben[ortsangaben].kreis")
                .type(SchemaBase.Type.STRING)
                .build()
        List<Integer> multiplicity21 = ImmutableList.of(1, 1)
        List<Integer> multiplicity22 = ImmutableList.of(1, 2)

        // multiple value
        FeatureSchema mapping3 = new ImmutableFeatureSchema.Builder().name("raumreferenz[raumreferenz].ortsangaben[ortsangaben].flurstueckskennung[ortsangaben_flurstueckskennung]")
                .type(SchemaBase.Type.STRING)
                .build()
        List<Integer> multiplicity31 = ImmutableList.of(1, 1, 1)
        List<Integer> multiplicity32 = ImmutableList.of(1, 1, 2)
        List<Integer> multiplicity33 = ImmutableList.of(1, 2, 1)

        //TODO if lastPropertyIsNested
        FeatureSchema mapping4 = new ImmutableFeatureSchema.Builder().name("kennung")
                .type(SchemaBase.Type.STRING)
                .build()

        ImmutableList<FeatureSchema> mappings = ImmutableList.of(mapping1, mapping2, mapping3, mapping3, mapping2, mapping3, mapping4)
        ImmutableList<List<Integer>> multiplicities = ImmutableList.of(multiplicity11, multiplicity21, multiplicity31, multiplicity32, multiplicity22, multiplicity33, ImmutableList.of())
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        String expected = "{" + System.lineSeparator() +
                "  \"properties\" : {" + System.lineSeparator() +
                "    \"raumreferenz\" : [ {" + System.lineSeparator() +
                "      \"datumAbgleich\" : \"xyz\"," + System.lineSeparator() +
                "      \"ortsangaben\" : [ {" + System.lineSeparator() +
                "        \"kreis\" : \"xyz\"," + System.lineSeparator() +
                "        \"flurstueckskennung\" : [ \"xyz\", \"xyz\" ]" + System.lineSeparator() +
                "      }, {" + System.lineSeparator() +
                "        \"kreis\" : \"xyz\"," + System.lineSeparator() +
                "        \"flurstueckskennung\" : [ \"xyz\" ]" + System.lineSeparator() +
                "      } ]" + System.lineSeparator() +
                "    } ]," + System.lineSeparator() +
                "    \"kennung\" : \"xyz\"" + System.lineSeparator() +
                "  }" + System.lineSeparator() +
                "}"

        when:
        runTransformer(outputStream, mappings, multiplicities)
        String actual = GeoJsonWriterSetupUtil.asString(outputStream)

        then:
        actual == expected
    }

    private static void runTransformer(ByteArrayOutputStream outputStream, Map<String, FeatureSchema> mappings,
                                       List<List<Integer>> multiplicities,
                                       List<String> values) throws IOException, URISyntaxException {
        outputStream.reset()
        EncodingAwareContextGeoJson context = GeoJsonWriterSetupUtil.createTransformationContext(outputStream, true)
        FeatureEncoderGeoJson encoder = new FeatureEncoderGeoJson(context.encoding(), ImmutableList.of(new GeoJsonWriterProperties()));
        FeatureSchema featureSchema = new ImmutableFeatureSchema.Builder().name("test")
                .type(SchemaBase.Type.OBJECT)
                .putAllPropertyMap(mappings)
                .build();

        context.setIsUseTargetPaths(true)
                .setType("test")
                .setMappings(Map.of("test", SchemaMapping.of(featureSchema)))

        context.encoding().getJson()
                .writeStartObject()

        encoder.onStart(context)
        encoder.onFeatureStart(context)

        for (int i = 0; i < mappings.size(); i++) {
            context.pathTracker().track(featureSchema.getProperties().get(i).getFullPath())
            context.setIndexes(multiplicities.get(i))
            context.setValue(values.get(i))
            encoder.onValue(context)
        }

        encoder.onFeatureEnd(context)

        context.encoding().getJson()
                .writeEndObject()
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
