/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain


import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.transform.FeatureRefResolver
import de.ii.xtraplatform.features.domain.transform.WithScope
import de.ii.xtraplatform.features.domain.transform.WithTransformationsApplied
import de.ii.xtraplatform.values.domain.ValueStore
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import spock.lang.Shared
import spock.lang.Specification

class SchemaGeneratorFeatureOpenApiSpec extends Specification {

    @Shared SchemaGeneratorFeatureOpenApi schemaGenerator

    def setupSpec() {
        ValueStore valueStore = Stub()
        schemaGenerator = new SchemaGeneratorFeatureOpenApi(null, valueStore)
    }

    def 'Test Open API schema generation for QUERYABLES type'() {
        given:
        FeatureSchema featureSchema = new ImmutableFeatureSchema.Builder()
                .name("test-name")
                .description("foobar")
                .putPropertyMap("date", new ImmutableFeatureSchema.Builder()
                        .name("date")
                        .type(SchemaBase.Type.DATETIME)
                        .build())
                .putPropertyMap("string", new ImmutableFeatureSchema.Builder()
                    .name("string")
                    .type(SchemaBase.Type.STRING)
                    .build())
                .type(SchemaBase.Type.OBJECT)
                .build()
        FeatureTypeConfigurationOgcApi collectionData = new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id("test-id")
                .label("test-label")
                .build()
        when:
        Optional<Schema<?>> schema = schemaGenerator.getProperty(featureSchema, collectionData, "date")
        then:
        schema.isPresent()
        schema.get().getType() == "string"
        schema.get().getFormat() == "date-time"
    }

    def 'Test Open API schema generation for the RETURNABLES type'() {
        given:
        FeatureTypeConfigurationOgcApi collectionData = new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id("id")
                .label("foo")
                .build()
        FeatureRefResolver featureRefResolver = new FeatureRefResolver(Set.of("JSON"))
        WithTransformationsApplied withTransformationsApplied = new WithTransformationsApplied();
        WithScope withScopeSchema = new WithScope(Set.of(SchemaBase.Scope.RETURNABLE, SchemaBase.Scope.RECEIVABLE));

        when:
        FeatureSchema featureSchema = SchemaDeriverFixtures.FEATURE_SCHEMA
                .accept(featureRefResolver, [])
                .accept(withScopeSchema)
                .accept(withTransformationsApplied)
        Schema schema = schemaGenerator.getSchema(featureSchema, collectionData)
        then:
        Objects.nonNull(schema)
        schema.getRequired() == ["type", "geometry", "properties"]
        schema.getTitle() == "foo"
        schema.getDescription() == "bar"
        schema.getProperties().get("id").getType() == "integer"
        ObjectSchema properties = schema.getProperties().get("properties") as ObjectSchema
        properties.getRequired() == ["string"]
        properties.getProperties().get("string").getType() == "string"
        properties.getProperties().get("string").getTitle() == "foo"
        properties.getProperties().get("string").getDescription() == "bar"
        properties.getProperties().get("featureRef").getType() == "integer"
        properties.getProperties().get("featureRef").getTitle() == "foo"
        properties.getProperties().get("featureRef").getDescription() == "bar"
        properties.getProperties().get("featureRefs").getType() == "array"
        properties.getProperties().get("featureRefs").getTitle() == "foo"
        properties.getProperties().get("featureRefs").getDescription() == "bar"
        properties.getProperties().get("featureRefs").getItems().getType() == "integer"
        properties.getProperties().get("featureRefs").getItems().getTitle() == null
        properties.getProperties().get("featureRefs").getItems().getDescription() == null
        properties.getProperties().get("link").get$ref() == "#/components/schemas/Link"
        properties.getProperties().get("links").getType() == "array"
        properties.getProperties().get("links").getMinItems() == 1
        properties.getProperties().get("links").getMaxItems() == 5
        properties.getProperties().get("links").getItems().get$ref() == "#/components/schemas/Link"
        properties.getProperties().get("datetime").getType() == "string"
        properties.getProperties().get("datetime").getFormat() == "date-time"
        properties.getProperties().get("datetime").getTitle() == "foo"
        properties.getProperties().get("datetime").getDescription() == "bar"
        properties.getProperties().get("datetimeReadOnly").getType() == "string"
        properties.getProperties().get("datetimeReadOnly").getFormat() == "date-time"
        properties.getProperties().get("datetimeReadOnly").getTitle() == "foo"
        properties.getProperties().get("datetimeReadOnly").getDescription() == "bar"
        properties.getProperties().get("datetimeReadOnly").getReadOnly() == true
        properties.getProperties().get("datetimeWriteOnly").getType() == "string"
        properties.getProperties().get("datetimeWriteOnly").getFormat() == "date-time"
        properties.getProperties().get("datetimeWriteOnly").getTitle() == "foo"
        properties.getProperties().get("datetimeWriteOnly").getDescription() == "bar"
        properties.getProperties().get("datetimeWriteOnly").getWriteOnly() == true
        properties.getProperties().get("endLifespanVersion").getType() == "string"
        properties.getProperties().get("endLifespanVersion").getFormat() == "date-time"
        properties.getProperties().get("endLifespanVersion").getTitle() == "foo"
        properties.getProperties().get("endLifespanVersion").getDescription() == "bar"
        properties.getProperties().get("boolean").getType() == "boolean"
        properties.getProperties().get("boolean").getTitle() == "foo"
        properties.getProperties().get("boolean").getDescription() == "bar"
        properties.getProperties().get("percent").getType() == "number"
        properties.getProperties().get("percent").getTitle() == "foo"
        properties.getProperties().get("percent").getDescription() == "bar"
        properties.getProperties().get("strings").getType() == "array"
        properties.getProperties().get("strings").getTitle() == "foo"
        properties.getProperties().get("strings").getDescription() == "bar"
        properties.getProperties().get("objects").getType() == "array"
        ArraySchema objects = properties.getProperties().get("objects") as ArraySchema
        objects.getType() == "array"
        objects.getTitle() == "foo"
        objects.getDescription() == "bar"
        objects.getItems().getType() == "object"
        objects.getItems().getProperties().get("integer").getType() == "integer"
        objects.getItems().getProperties().get("integer").getTitle() == "foo"
        objects.getItems().getProperties().get("integer").getDescription() == "bar"
        objects.getItems().getProperties().get("date").getType() == "string"
        objects.getItems().getProperties().get("date").getFormat() == "date"
        objects.getItems().getProperties().get("object2").getType() == "object"
        objects.getItems().getProperties().get("object2").getProperties().get("regex").getType() == "string"
        objects.getItems().getProperties().get("object2").getProperties().get("regex").getPattern() == "'^_\\\\w+\$'"
        objects.getItems().getProperties().get("object2").getProperties().get("codelist").getType() == "string"
        objects.getItems().getProperties().get("object2").getProperties().get("enum").getType() == "string"
        objects.getItems().getProperties().get("object2").getProperties().get("strings").getType() == "array"
        schema.getProperties().get("type").getType() == "string"
        schema.getProperties().get("links").getType() == "array"
        Objects.nonNull(schema.getProperties().get("geometry"))
    }
}
