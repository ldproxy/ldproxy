/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration
import spock.lang.Specification

/**
 * Locks the identity of the compiled-XSD cache key. The key is what keeps the schema cache bounded:
 * it is derived from the configuration the schema is built from, not from the API's data, so a
 * service reload with an unchanged GML configuration reuses the compiled schema instead of parsing
 * every transitive XSD again and keeping the previous grammar pool reachable for the lifetime of
 * the process.
 *
 * <p>Building a schema for the NAS catalog parses close to a hundred XSDs, so a key that varies
 * when it should not is expensive twice over — in startup latency after every reload and in
 * retained heap.
 */
class SchemaCacheKeySpec extends Specification {

    def 'the same schemaLocations and catalog yield the same key, whatever the entry order'() {
        given: "two configurations that differ only in the order their entries were added"
        def first = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('aaa', 'https://example.com/a.xsd')
                .putSchemaLocations('bbb', 'https://example.com/b.xsd')
                .putXsdCatalog('https://example.com/a.xsd', 'mirror/a.xsd')
                .putXsdCatalog('https://example.com/b.xsd', 'mirror/b.xsd')
                .build()
        def second = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('bbb', 'https://example.com/b.xsd')
                .putSchemaLocations('aaa', 'https://example.com/a.xsd')
                .putXsdCatalog('https://example.com/b.xsd', 'mirror/b.xsd')
                .putXsdCatalog('https://example.com/a.xsd', 'mirror/a.xsd')
                .build()

        when:
        def firstKey = FeaturesFormatGml.schemaCacheKey(first)
        def secondKey = FeaturesFormatGml.schemaCacheKey(second)

        then: "they share the compiled schema"
        firstKey == secondKey
    }

    def 'a different set of schemaLocations yields a different key'() {
        given:
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('aaa', 'https://example.com/a.xsd')
                .build()
        def other = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('aaa', 'https://example.com/other.xsd')
                .build()

        expect:
        FeaturesFormatGml.schemaCacheKey(config) != FeaturesFormatGml.schemaCacheKey(other)
    }

    def 'a different catalog yields a different key even for identical schemaLocations'() {
        given: "the catalog decides which files the imports resolve to, so it is part of the identity"
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('aaa', 'https://example.com/a.xsd')
                .putXsdCatalog('https://example.com/a.xsd', 'mirror/a.xsd')
                .build()
        def other = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putSchemaLocations('aaa', 'https://example.com/a.xsd')
                .putXsdCatalog('https://example.com/a.xsd', 'other-mirror/a.xsd')
                .build()

        expect:
        FeaturesFormatGml.schemaCacheKey(config) != FeaturesFormatGml.schemaCacheKey(other)
    }

    def 'a configuration without schemaLocations still has a stable key'() {
        given:
        def config = new ImmutableGmlConfiguration.Builder().enabled(true).build()

        when:
        def key = FeaturesFormatGml.schemaCacheKey(config)

        then: "no exception, and the key does not vary between calls"
        key != null
        key == FeaturesFormatGml.schemaCacheKey(config)
    }
}
