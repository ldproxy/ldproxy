/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import spock.lang.Specification

import java.time.Instant

class CompositeIdFormatterSpec extends Specification {

    def 'NAS-style pattern adjacent groups → id + compact timestamp'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>DE[A-Za-z0-9]{14})(?<start>\\d{8}T\\d{6}Z)$',
                null,
                'DEHE862010005DDo',
                Instant.parse('2009-10-16T07:56:37Z'), false) == 'DEHE862010005DDo20091016T075637Z'
    }

    def 'pattern with dot separator → id.compactTimestamp'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)\\.(?<start>\\d{8}T\\d{6}Z)$',
                null,
                '1',
                Instant.parse('2001-07-02T10:43:17Z'), false) == '1.20010702T104317Z'
    }

    def 'pattern with underscore separator → id_compactTimestamp'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)_(?<start>\\d{8}T\\d{6}Z)$',
                null,
                'abc',
                Instant.parse('2024-02-15T12:11:56Z'), false) == 'abc_20240215T121156Z'
    }

    def 'custom timestamp format honored'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)@(?<start>.+)$',
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                'x',
                Instant.parse('2024-02-15T12:11:56Z'), false) == 'x@2024-02-15T12:11:56Z'
    }

    def 'null/blank pattern → canonical id passthrough'() {
        expect:
        CompositeIdFormatter.format(null, null, 'abc', Instant.parse('2024-01-01T00:00:00Z'), false) == 'abc'
        CompositeIdFormatter.format('', null, 'abc', Instant.parse('2024-01-01T00:00:00Z'), false) == 'abc'
    }

    def 'null canonical → null'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)\\.(?<start>.+)$',
                null,
                null,
                Instant.parse('2024-01-01T00:00:00Z'), false) == null
    }

    def 'null instant → canonical passthrough'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)\\.(?<start>.+)$',
                null,
                'abc',
                null,
                false) == 'abc'
    }

    def 'pattern without expected named groups → canonical passthrough'() {
        expect:
        CompositeIdFormatter.format('^.+$', null, 'abc', Instant.parse('2024-01-01T00:00:00Z'), false) == 'abc'
    }

    def 'date-only start → compact date suffix by default'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)\\.(?<start>\\d{8})$',
                null,
                'abc',
                Instant.parse('2026-05-12T00:00:00Z'),
                true) == 'abc.20260512'
    }

    def 'date-only start with explicit format → explicit format wins'() {
        expect:
        CompositeIdFormatter.format(
                '^(?<id>.+?)@(?<start>.+)$',
                'yyyy-MM-dd',
                'x',
                Instant.parse('2026-05-12T00:00:00Z'),
                true) == 'x@2026-05-12'
    }
}
