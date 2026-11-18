/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.Profile
import de.ii.xtraplatform.codelists.domain.Codelist
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaDocument
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument
import de.ii.xtraplatform.values.domain.Values
import spock.lang.Specification

class JsonSchemaCacheSpec extends Specification {

    static class CountingCache extends JsonSchemaCache {
        int derived = 0

        CountingCache() {
            super()
        }

        CountingCache(Values<Codelist> codelists) {
            super(codelists)
        }

        @Override
        protected JsonSchemaDocument deriveSchema(FeatureSchema schema, OgcApiDataV2 apiData,
                                                  FeatureTypeConfigurationOgcApi collectionData,
                                                  List<Profile> profiles, Optional<String> schemaUri,
                                                  JsonSchemaDocument.VERSION version) {
            derived++
            return new ImmutableJsonSchemaDocument.Builder().build()
        }
    }

    OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder().id('test').serviceType('OGC_API').build()
    FeatureTypeConfigurationOgcApi collectionData = new ImmutableFeatureTypeConfigurationOgcApi.Builder()
            .id('collection').label('Collection').build()

    // revision of the codelists in the store
    long revision = 1

    Values<Codelist> codelists = [getRevision: { -> revision }] as Values<Codelist>

    void request(JsonSchemaCache cache) {
        cache.getSchema(null, apiData, collectionData, [], Optional.empty(), [])
    }

    def "a cached schema is reused while the codelists are unchanged"() {
        given:
        def cache = new CountingCache(codelists)

        when:
        request(cache)
        request(cache)

        then:
        cache.derived == 1
    }

    def "a changed codelist revision invalidates the cached schema"() {
        given:
        def cache = new CountingCache(codelists)
        request(cache)

        when:
        revision = 2
        request(cache)
        request(cache)

        then:
        cache.derived == 2
    }

    def "a cache without codelists derives each schema once"() {
        given:
        def cache = new CountingCache()

        when:
        request(cache)
        request(cache)

        then:
        cache.derived == 1
    }
}
