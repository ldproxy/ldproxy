/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import com.google.common.collect.ImmutableMap
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.transform.FeatureRefResolver
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation
import de.ii.xtraplatform.features.domain.transform.OnlyQueryables
import de.ii.xtraplatform.features.domain.transform.WithScope
import de.ii.xtraplatform.features.domain.transform.WithTransformationsApplied
import spock.lang.Specification

import java.util.function.Predicate

class SchemaDeriverJsonSpec extends Specification {

    def 'Returnables/Receivables schema derivation, JSON Schema draft #version'() {
        given:
        FeatureRefResolver featureRefResolver = new FeatureRefResolver(Set.of("JSON"))
        WithTransformationsApplied withTransformationsApplied = new WithTransformationsApplied();
        WithScope withScopeSchema = new WithScope(Set.of(SchemaBase.Scope.RETURNABLE, SchemaBase.Scope.RECEIVABLE));
        SchemaDeriverFeatures schemaDeriver = new SchemaDeriverFeatures(version, Optional.empty(), "foo", Optional.empty(), ImmutableMap.of())

        when:
        JsonSchemaDocument document = SchemaDeriverFixtures.FEATURE_SCHEMA
                .accept(featureRefResolver, [])
                .accept(withScopeSchema)
                .accept(withTransformationsApplied)
                .accept(schemaDeriver) as JsonSchemaDocument

        then:
        document == expected

        where:
        version                            || expected
        JsonSchemaDocument.VERSION.V202012 || EXPECTED_SCHEMA
    }

    def 'Queryables schema derivation, JSON Schema draft #version'() {

        given:
        FeatureRefResolver featureRefResolver = new FeatureRefResolver(Set.of("JSON"))
        List<String> queryables = ["geometry", "datetime", "featureRef" /*, "objects.date" */]
        Predicate<String> excludeConnectors = path -> path.matches(".+?\\[[^=\\]]+].+");
        OnlyQueryables queryablesSelector = new OnlyQueryables(queryables, List.of(), ".", excludeConnectors, false);
        WithTransformationsApplied schemaFlattener = new WithTransformationsApplied(ImmutableMap.of("*", new ImmutablePropertyTransformation.Builder().flatten(".").build()))
        SchemaDeriverJsonSchema schemaDeriver = new SchemaDeriverCollectionProperties(version, Optional.empty(), "test-label", Optional.empty(), ImmutableMap.of(), queryables)

        when:
        JsonSchemaDocument document = SchemaDeriverFixtures.FEATURE_SCHEMA
                .accept(featureRefResolver, [])
                .accept(queryablesSelector)
                .accept(schemaFlattener)
                .accept(schemaDeriver) as JsonSchemaDocument

        then:
        document == expected

        where:
        version                            || expected
        JsonSchemaDocument.VERSION.V202012 || EXPECTED_QUERYABLES
    }

    static JsonSchema EXPECTED_SCHEMA =
            ImmutableJsonSchemaDocument.builder()
                    .schema(JsonSchemaDocument.VERSION.V202012.url())
                    .title("foo")
                    .description("bar")
                    .putProperties("id", new ImmutableJsonSchemaInteger.Builder()
                            .title("foo")
                            .description("bar")
                            .role("id")
                            .propertySeq(0)
                            .readOnly(true)
                            .build())
                    .addRequired("string")
                    .putProperties("string", new ImmutableJsonSchemaString.Builder()
                            .title("foo")
                            .description("bar")
                            .propertySeq(1)
                            .build())
                    .putProperties("link", new ImmutableJsonSchemaRef.Builder()
                            .ref("#/\$defs/Link")
                            .propertySeq(2)
                            .build())
                    .putProperties("links", new ImmutableJsonSchemaArray.Builder()
                            .items(new ImmutableJsonSchemaRef.Builder()
                                    .ref("#/\$defs/Link")
                                    .build())
                            .maxItems(5)
                            .propertySeq(3)
                            .build())
                    .putProperties("featureRef", new ImmutableJsonSchemaInteger.Builder()
                            .title("foo")
                            .description("bar")
                            .role("reference")
                            .refCollectionId("foo")
                            .propertySeq(4)
                            .build())
                    .putProperties("featureRefs", new ImmutableJsonSchemaArray.Builder()
                            .title("foo")
                            .description("bar")
                            .items(new ImmutableJsonSchemaInteger.Builder()
                                    .role("reference")
                                    .refCollectionId("foo")
                                    .build())
                            .propertySeq(5)
                            .build())
                    .putProperties("geometry", new ImmutableJsonSchemaGeometry.Builder()
                            .from(JsonSchemaBuildingBlocks.MULTI_POLYGON)
                            .title("foo")
                            .description("bar")
                            .role("primary-geometry")
                            .propertySeq(6)
                            .build())
                    .putProperties("datetime", new ImmutableJsonSchemaString.Builder()
                            .format("date-time")
                            .title("foo")
                            .description("bar")
                            .role("primary-instant")
                            .propertySeq(7)
                            .build())
                    .putProperties("datetimeReadOnly", new ImmutableJsonSchemaString.Builder()
                            .format("date-time")
                            .title("foo")
                            .description("bar")
                            .readOnly(true)
                            .propertySeq(8)
                            .build())
                    .putProperties("datetimeWriteOnly", new ImmutableJsonSchemaString.Builder()
                            .format("date-time")
                            .title("foo")
                            .description("bar")
                            .writeOnly(true)
                            .propertySeq(9)
                            .build())
                    .putProperties("endLifespanVersion", new ImmutableJsonSchemaString.Builder()
                            .format("date-time")
                            .title("foo")
                            .description("bar")
                            .propertySeq(10)
                            .build())
                    .putProperties("boolean", new ImmutableJsonSchemaBoolean.Builder()
                            .title("foo")
                            .description("bar")
                            .propertySeq(11)
                            .build())
                    .putProperties("percent", new ImmutableJsonSchemaNumber.Builder()
                            .title("foo")
                            .description("bar")
                            .propertySeq(12)
                            .build())
                    .putProperties("strings", new ImmutableJsonSchemaArray.Builder()
                            .title("foo")
                            .description("bar")
                            .items(new ImmutableJsonSchemaString.Builder()
                                    .build())
                            .propertySeq(13)
                            .build())
                    .putProperties("objects", new ImmutableJsonSchemaArray.Builder()
                            .items(new ImmutableJsonSchemaRef.Builder()
                                    .ref("#/\$defs/Object")
                                    .build())
                            .propertySeq(14)
                            .build())
                    .putDefinitions("Link", JsonSchemaBuildingBlocks.LINK_JSON)
                    .putDefinitions("Object", new ImmutableJsonSchemaObject.Builder()
                            .title("foo")
                            .description("bar")
                            .putProperties("integer", new ImmutableJsonSchemaInteger.Builder()
                                    .title("foo")
                                    .description("bar")
                                    .propertySeq(0)
                                    .build())
                            .putProperties("date", new ImmutableJsonSchemaString.Builder()
                                    .format("date")
                                    .propertySeq(1)
                                    .build())
                            .putProperties("object2", new ImmutableJsonSchemaRef.Builder()
                                    .ref("#/\$defs/Object2")
                                    .propertySeq(2)
                                    .build())
                            .build())
                    .putDefinitions("Object2", new ImmutableJsonSchemaObject.Builder()
                            .putProperties("regex", new ImmutableJsonSchemaString.Builder()
                                    .pattern("'^_\\\\w+\$'")
                                    .propertySeq(0)
                                    .build())
                            .putProperties("codelist", new ImmutableJsonSchemaString.Builder()
                                    .codelistId("mycodelist")
                                    .propertySeq(1)
                                    .build())
                            .putProperties("enum", new ImmutableJsonSchemaString.Builder()
                                    .enums(List.of("foo", "bar"))
                                    .propertySeq(2)
                                    .build())
                            .putProperties("strings", new ImmutableJsonSchemaArray.Builder()
                                    .items(new ImmutableJsonSchemaString.Builder()
                                            .build())
                                    .propertySeq(3)
                                    .build())
                            .build())
                    .build();

    static JsonSchema EXPECTED_QUERYABLES =
            ImmutableJsonSchemaDocument.builder()
                    .schema(JsonSchemaDocument.VERSION.V202012.url())
                    .title("test-label")
                    .description("bar")
                    .putProperties("featureRef", new ImmutableJsonSchemaInteger.Builder()
                            .title("foo")
                            .description("bar")
                            .role("reference")
                            .refCollectionId("foo")
                            .propertySeq(0)
                            .build())
                    .putProperties("geometry", new ImmutableJsonSchemaGeometry.Builder()
                            .title("foo")
                            .description("bar")
                            .format("geometry-multipolygon")
                            .role("primary-geometry")
                            .propertySeq(1)
                            .build())
                    .putProperties("datetime", new ImmutableJsonSchemaString.Builder()
                            .format("date-time")
                            .title("foo")
                            .description("bar")
                            .role("primary-instant")
                            .propertySeq(2)
                            .build())
            /*
            .putProperties("objects.date", new ImmutableJsonSchemaArray.Builder()
                    .title("foo > date")
                    .items(new ImmutableJsonSchemaString.Builder()
                        .format("date")
                        .build())
                    .build())
             */
                    .additionalProperties(new ImmutableJsonSchemaFalse.Builder().build())
                    .build();

    //TODO: move to SchemaBase
    static Optional<FeatureSchema> getProperty(FeatureSchema schema, String name) {
        return schema.getProperties().stream().filter(property -> Objects.equals(property.getName(), name)).findFirst();
    }
}
