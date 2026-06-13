/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import com.google.common.collect.ImmutableMap
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import de.ii.xtraplatform.features.domain.transform.WithScope
import de.ii.xtraplatform.geometries.domain.GeometryType
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaGeometry
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject
import spock.lang.Specification

/**
 * Verifies that SchemaDeriverJsonSchema emits the Part-5 combined geometry
 * building blocks (point-or-multipoint, linestring-or-multilinestring,
 * polygon-or-multipolygon, curve, surface) when a geometry property declares
 * the corresponding {@code geometryTypes} list.
 */
class SchemaDeriverGeometryTypesSpec extends Specification {

    static JsonSchemaGeometry deriveGeometry(List<GeometryType> geometryTypes) {
        def geom = new ImmutableFeatureSchema.Builder()
                .name("geometry")
                .type(SchemaBase.Type.GEOMETRY)
                .sourcePath("g")
                .role(SchemaBase.Role.PRIMARY_GEOMETRY)
                .geometryTypes(geometryTypes)
                .build()

        def feature = new ImmutableFeatureSchema.Builder()
                .name("Test")
                .type(SchemaBase.Type.OBJECT)
                .sourcePath("/test")
                .putPropertyMap("geometry", geom)
                .build()

        def withScope = new WithScope(Set.of(SchemaBase.Scope.RETURNABLE, SchemaBase.Scope.RECEIVABLE))
        def deriver = new SchemaDeriverFeatures(
                JsonSchemaDocument.VERSION.V202012, Optional.empty(),
                "test", Optional.empty(), ImmutableMap.of())

        def doc = feature.accept(withScope).accept(deriver) as JsonSchemaDocument
        return doc.properties.values().find { it instanceof JsonSchemaGeometry } as JsonSchemaGeometry
    }

    def "POINT + MULTI_POINT yields point-or-multipoint"() {
        when:
        def geom = deriveGeometry([GeometryType.POINT, GeometryType.MULTI_POINT])

        then:
        geom.format == "geometry-point-or-multipoint"
        geom.geometryTypes.get() == ["Point", "MultiPoint"]
    }

    def "LINE_STRING + MULTI_LINE_STRING yields linestring-or-multilinestring"() {
        when:
        def geom = deriveGeometry([GeometryType.LINE_STRING, GeometryType.MULTI_LINE_STRING])

        then:
        geom.format == "geometry-linestring-or-multilinestring"
    }

    def "POLYGON + MULTI_POLYGON yields polygon-or-multipolygon"() {
        when:
        def geom = deriveGeometry([GeometryType.POLYGON, GeometryType.MULTI_POLYGON])

        then:
        geom.format == "geometry-polygon-or-multipolygon"
    }

    def "LINE_STRING + CIRCULAR_STRING + COMPOUND_CURVE: format falls back to any, types are listed"() {
        when:
        def geom = deriveGeometry([
                GeometryType.LINE_STRING,
                GeometryType.CIRCULAR_STRING,
                GeometryType.COMPOUND_CURVE
        ])

        then:
        geom.format == "geometry-any"
        geom.geometryTypes.get() == ["LineString", "CircularString", "CompoundCurve"]
    }

    def "POLYGON + CURVE_POLYGON: format falls back to any, types are listed"() {
        when:
        def geom = deriveGeometry([GeometryType.POLYGON, GeometryType.CURVE_POLYGON])

        then:
        geom.format == "geometry-any"
        geom.geometryTypes.get() == ["Polygon", "CurvePolygon"]
    }

    def "single entry falls through to the single-type building block"() {
        when:
        def geom = deriveGeometry([GeometryType.MULTI_POLYGON])

        then:
        geom.format == "geometry-multipolygon"
        geom.geometryTypes.get() == ["MultiPolygon"]
    }

    def "unknown combination falls back to ANY_EXTENDED via the switch default"() {
        when:
        // POINT + POLYGON is not a known combination - effective type is ANY,
        // so the switch default kicks in and yields geometry-any.
        def geom = deriveGeometry([GeometryType.POINT, GeometryType.POLYGON])

        then:
        geom.format == "geometry-any"
        geom.geometryTypes.get() == ["Point", "Polygon"]
    }

    def "no explicit types: x-ldproxy-geometryTypes is omitted"() {
        given:
        def emptyGeom = new ImmutableFeatureSchema.Builder()
                .name("geometry")
                .type(SchemaBase.Type.GEOMETRY)
                .sourcePath("g")
                .role(SchemaBase.Role.PRIMARY_GEOMETRY)
                .build()
        def feature = new ImmutableFeatureSchema.Builder()
                .name("Test")
                .type(SchemaBase.Type.OBJECT)
                .sourcePath("/test")
                .putPropertyMap("geometry", emptyGeom)
                .build()
        def withScope = new WithScope(Set.of(SchemaBase.Scope.RETURNABLE, SchemaBase.Scope.RECEIVABLE))
        def deriver = new SchemaDeriverFeatures(
                JsonSchemaDocument.VERSION.V202012, Optional.empty(),
                "test", Optional.empty(), ImmutableMap.of())

        when:
        def doc = feature.accept(withScope).accept(deriver) as JsonSchemaDocument
        def geom = doc.properties.values().find { it instanceof JsonSchemaGeometry } as JsonSchemaGeometry

        then:
        geom.format == "geometry-any"
        geom.geometryTypes.isEmpty()
    }
}
