/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import de.ii.ldproxy.cfg.ValueMigration.ValueMigrationContext
import de.ii.ogcapi.features.search.domain.StoredQueryExpression
import de.ii.ogcapi.features.search.domain.StoredQueryValue
import de.ii.xtraplatform.cql.app.CqlImpl
import de.ii.xtraplatform.cql.domain.Cql
import de.ii.xtraplatform.cql.domain.Cql.Format
import de.ii.xtraplatform.crs.domain.OgcCrs
import spock.lang.Specification

class StoredQueryMigrationV5Spec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()
    static final StoredQueryMigrationV5 MIGRATION = new StoredQueryMigrationV5({ type, name -> false } as ValueMigrationContext)

    static final String DEPRECATED = '''{
      "id": "places-by-name",
      "title": "Places by name",
      "collections": ["places"],
      "filter": {
        "op": "and",
        "args": [
          {"op": "in", "args": [{"property": "name"}, {"$parameter": {"$ref": "#/parameters/name"}}]},
          {"op": "=", "args": [{"property": "type"}, "city"]}
        ]
      },
      "properties": ["name", {"$parameter": {"$ref": "#/parameters/geometry_or_area"}}],
      "limit": {"$parameter": {"$ref": "#/parameters/limit"}},
      "parameters": {
        "name": {"type": "array", "items": {"type": "string"}},
        "geometry_or_area": {"type": "string", "enum": ["geometry", "area"]},
        "limit": {"type": "integer", "default": 10}
      }
    }'''

    static final String NAMED = '''{
      "id": "places-by-name",
      "title": "Places by name",
      "collections": ["places"],
      "filter": {
        "op": "and",
        "args": [
          {"op": "in", "args": [{"property": "name"}, {"$parameter": {"name": {"$ref": "#/parameters/name"}}}]},
          {"op": "=", "args": [{"property": "type"}, "city"]}
        ]
      },
      "properties": ["name", {"$parameter": {"geometry_or_area": {"$ref": "#/parameters/geometry_or_area"}}}],
      "limit": {"$parameter": {"limit": {"$ref": "#/parameters/limit"}}},
      "parameters": {
        "name": {"type": "array", "items": {"type": "string"}},
        "geometry_or_area": {"type": "string", "enum": ["geometry", "area"]},
        "limit": {"type": "integer", "default": 10}
      }
    }'''

    def "the migration applies to stored queries"() {
        expect:
        MIGRATION.getValueType() == "queries"
    }

    def "a stored query with the deprecated parameter form is applicable"() {
        expect:
        MIGRATION.isApplicable(MAPPER.readTree(DEPRECATED))
    }

    def "a stored query with the named parameter form is not applicable"() {
        expect:
        !MIGRATION.isApplicable(MAPPER.readTree(NAMED))
    }

    def "the deprecated parameter form is replaced by the named form everywhere in the query"() {
        given:
        JsonNode deprecated = MAPPER.readTree(DEPRECATED)

        when:
        JsonNode migrated = MIGRATION.migrate(deprecated)

        then:
        migrated == MAPPER.readTree(NAMED)
        !MIGRATION.isApplicable(migrated)
        deprecated == MAPPER.readTree(DEPRECATED)
    }

    def "a reference without the parameters prefix is used as the name"() {
        when:
        JsonNode migrated = MIGRATION.migrate(MAPPER.readTree('{"limit": {"$parameter": {"$ref": "limit"}}}'))

        then:
        migrated == MAPPER.readTree('{"limit": {"$parameter": {"limit": {"$ref": "limit"}}}}')
    }

    def "the deprecated and the migrated form describe the same stored query"() {
        given:
        Cql cql = new CqlImpl()

        when:
        StoredQueryValue deprecated = StoredQueryExpression.MAPPER.readValue(DEPRECATED, StoredQueryValue.class)
        StoredQueryValue migrated = StoredQueryExpression.MAPPER.readValue(
                MAPPER.writeValueAsString(MIGRATION.migrate(MAPPER.readTree(DEPRECATED))), StoredQueryValue.class)

        then:
        migrated.getCollections() == deprecated.getCollections()
        migrated.getProperties() == deprecated.getProperties()
        migrated.getLimit() == deprecated.getLimit()
        migrated.getParameters() == deprecated.getParameters()
        // the filter is kept as parsed JSON and only interpreted by the CQL2 parser
        migrated.getFilter() != deprecated.getFilter()
        cql.read(MAPPER.writeValueAsString(migrated.getFilter().get()), Format.JSON, OgcCrs.CRS84, true) ==
                cql.read(MAPPER.writeValueAsString(deprecated.getFilter().get()), Format.JSON, OgcCrs.CRS84, true)
    }
}
