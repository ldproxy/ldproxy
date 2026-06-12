/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain

import de.ii.ogcapi.foundation.domain.QueryParameterSet
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.cql.domain.GeometryNode
import de.ii.xtraplatform.cql.domain.Parameter
import de.ii.xtraplatform.cql.domain.ScalarLiteral
import de.ii.xtraplatform.cql.domain.SpatialLiteral
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaString
import de.ii.xtraplatform.jsonschema.domain.JsonSchema
import spock.lang.Specification

class ParameterResolverCqlSpec extends Specification {

    static ParameterResolverCql resolver(Map<String, Object> values, JsonSchema schema) {
        def queryParameterSet = [getTypedValues: { values }] as QueryParameterSet
        def schemaValidator = [validate: { s, v -> Optional.empty() }] as SchemaValidator
        return new ParameterResolverCql(queryParameterSet, [aoi: schema], schemaValidator, Optional.of(OgcCrs.CRS84))
    }

    static JsonSchema geometrySchema(String format) {
        return new ImmutableJsonSchemaString.Builder().format(format).build()
    }

    def 'a WKT value of a geometry parameter resolves to a spatial literal'() {
        given:
        def resolver = resolver([aoi: "POLYGON((8 50,9 50,9 51,8 50))"], geometrySchema("geometry-polygon"))
        def parameter = Parameter.of("aoi", geometrySchema("geometry-polygon"))

        when:
        def resolved = parameter.accept(resolver)

        then:
        resolved instanceof SpatialLiteral
        ((GeometryNode) ((SpatialLiteral) resolved).getValue()).getGeometry().getType().name() == "POLYGON"
    }

    def 'the geometry type of the format is enforced'() {
        given:
        def resolver = resolver([aoi: "POINT(8 50)"], geometrySchema("geometry-polygon"))
        def parameter = Parameter.of("aoi", geometrySchema("geometry-polygon"))

        when:
        parameter.accept(resolver)

        then:
        def e = thrown IllegalArgumentException
        e.message.contains("polygon")
    }

    def 'any geometry type is accepted without a specific format'() {
        given:
        def resolver = resolver([aoi: "POINT(8 50)"], geometrySchema("geometry"))
        def parameter = Parameter.of("aoi", geometrySchema("geometry"))

        expect:
        parameter.accept(resolver) instanceof SpatialLiteral
    }

    def 'an invalid WKT value is rejected'() {
        given:
        def resolver = resolver([aoi: "POLYGON(8 50)"], geometrySchema("geometry-polygon"))
        def parameter = Parameter.of("aoi", geometrySchema("geometry-polygon"))

        when:
        parameter.accept(resolver)

        then:
        thrown IllegalArgumentException
    }

    def 'parameters without a geometry format resolve to scalars'() {
        given:
        def schema = new ImmutableJsonSchemaString.Builder().build()
        def resolver = resolver([aoi: "foo"], schema)
        def parameter = Parameter.of("aoi", schema)

        expect:
        parameter.accept(resolver) == ScalarLiteral.of("foo")
    }
}
