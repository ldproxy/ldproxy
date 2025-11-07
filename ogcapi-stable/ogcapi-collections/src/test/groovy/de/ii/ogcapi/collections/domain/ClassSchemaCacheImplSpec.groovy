/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.domain

import com.google.common.collect.ImmutableSet
import de.ii.ogcapi.common.domain.OgcApiExtent
import de.ii.ogcapi.common.domain.OgcApiExtentSpatial
import de.ii.ogcapi.common.domain.OgcApiExtentTemporal
import de.ii.ogcapi.foundation.infra.json.ClassSchemaCacheImpl
import de.ii.ogcapi.common.domain.LandingPage
import de.ii.ogcapi.common.domain.ConformanceDeclaration
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import spock.lang.Shared
import spock.lang.Specification

class ClassSchemaCacheImplSpec extends Specification {

    @Shared ClassSchemaCacheImpl schemaGenerator

    def setupSpec() {
        schemaGenerator = new ClassSchemaCacheImpl()
    }

    def 'Test Landing Page schema generation'() {
        when:
        Schema schema = schemaGenerator.getSchema(LandingPage.class)
        Map<String, Schema<?>> refSchemas = schemaGenerator.getReferencedSchemas(LandingPage.class)
        Schema extentSchema = schemaGenerator.getSchema(OgcApiExtent.class)
        Schema spatialExtentSchema = schemaGenerator.getSchema(OgcApiExtentSpatial.class)
        Schema temporalExtentSchema = schemaGenerator.getSchema(OgcApiExtentTemporal.class)
        then:
        Objects.nonNull(schema)
        schema.getType() == "object"
        schema.getProperties().get("description").getType() == "string"
        schema.getProperties().get("title").getType() == "string"
        schema.getProperties().get("links").getType() == "array"
        schema.getRequired() == ["links"]
    }

    def 'Test Conformance Declaration schema generation'() {
        when:
        Schema schema = schemaGenerator.getSchema(ConformanceDeclaration.class)
        then:
        Objects.nonNull(schema)
        schema.getType() == "object"
        schema.getRequired() == ["conformsTo", "links"]
        schema.getProperties().get("conformsTo").getType() == "array"
        schema.getProperties().get("title").getType() == "string"
        schema.getProperties().get("description").getType() == "string"
        schema.getProperties().get("links").getType() == "array"
    }

    def 'Test Collections schema generation'() {
        when:
        Schema schema = schemaGenerator.getSchema(Collections.class)
        Map<String, Schema<?>> refSchemas = schemaGenerator.getReferencedSchemas(Collections.class)
        Schema collectionSchema = schemaGenerator.getSchema(OgcApiCollection.class)
        then:
        Objects.nonNull(schema)
        schema.getType() == "object"
        schema.getProperties().get("collections").getType() == "array"
        schema.getProperties().get("crs").getType() == "array"
        schema.getProperties().get("description").getType() == "string"
        schema.getProperties().get("title").getType() == "string"
        schema.getProperties().get("links").getType() == "array"
        ArraySchema collections = schema.getProperties().get("collections") as ArraySchema
        collections.getItems().get$ref() == "#/components/schemas/Collection"
        Objects.nonNull(refSchemas)
        refSchemas.keySet() == ImmutableSet.of("Collection", "Link", "SpatialExtent", "InstantArray", "Extent", "TemporalExtent", "InstantArrayArray", "doubleArrayArray", "doubleArray")
        Objects.nonNull(collectionSchema)
        collectionSchema.getRequired() == ["id", "links"]
    }

    def 'Test OgcApiCollection schema generation'() {
        when:
        Schema schema = schemaGenerator.getSchema(OgcApiCollection.class)
        Map<String, Schema<?>> refSchemas = schemaGenerator.getReferencedSchemas(OgcApiCollection.class)
        Schema extentSchema = schemaGenerator.getSchema(OgcApiExtent.class)
        Schema spatialExtentSchema = schemaGenerator.getSchema(OgcApiExtentSpatial.class)
        Schema temporalExtentSchema = schemaGenerator.getSchema(OgcApiExtentTemporal.class)
        then:
        Objects.nonNull(schema)
        schema.getType() == "object"
        schema.getRequired() == ["id", "links"]
        schema.getProperties().get("extent").get$ref() == "#/components/schemas/Extent"
        schema.getProperties().get("crs").getType() == "array"
        schema.getProperties().get("storageCrs").getType() == "string"
        schema.getProperties().get("storageCrsCoordinateEpoch").getType() == "number"
        schema.getProperties().get("itemType").getType() == "string"
        schema.getProperties().get("id").getType() == "string"
        schema.getProperties().get("description").getType() == "string"
        schema.getProperties().get("title").getType() == "string"
        schema.getProperties().get("links").getType() == "array"
        Objects.nonNull(refSchemas)
        refSchemas.keySet() == ImmutableSet.of("Extent", "SpatialExtent", "TemporalExtent", "doubleArray", "InstantArray", "doubleArrayArray", "InstantArrayArray", "Link")
        Objects.nonNull(extentSchema)
        extentSchema.getType() == "object"
        extentSchema.getProperties().get("spatial").get$ref() == "#/components/schemas/SpatialExtent"
        extentSchema.getProperties().get("temporal").get$ref() == "#/components/schemas/TemporalExtent"
        Objects.nonNull(spatialExtentSchema)
        spatialExtentSchema.getType()  == "object"
        spatialExtentSchema.getRequired()  == ["bbox", "crs"]
        Objects.nonNull(temporalExtentSchema)
        temporalExtentSchema.getType() == "object"
        temporalExtentSchema.getRequired()  == ["interval", "trs"]
    }
}
