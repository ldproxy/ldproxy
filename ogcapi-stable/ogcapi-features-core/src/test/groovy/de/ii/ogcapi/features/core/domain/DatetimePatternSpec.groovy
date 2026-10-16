/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import spock.lang.Specification

import java.util.regex.Pattern

class DatetimePatternSpec extends Specification {

    def 'the pattern only accepts dates, seconds and offsets that the server can parse'() {
        given:
        Pattern pattern = Pattern.compile(AbstractQueryParameterDatetime.getCopyOfBaseSchema().getPattern())

        expect:
        pattern.matcher(value).find() == valid

        where:
        value                              || valid
        "2020-01-01T10:20:59Z"             || true
        "2020-01-01T10:20:59+07:14"        || true
        "2020-01-01T10:20:60Z"             || false
        "5076-05-24T20:02:60+07:14"        || false
        "2020-01-01T10:20:00Z/.."          || true
        "2020-01-01T10:20:00+18:00"        || true
        "2020-01-01T10:20:00-17:59"        || true
        "2020-01-01T10:20:00+18:01"        || false
        "8270-10-14T20:07:46.3188+22:22"   || false
        "2020-09-30"                       || true
        "7430-09-31T05:00:00Z"             || false
        "2024-02-29"                       || true
        "2023-02-29"                       || false
        "2000-02-29T00:00:00Z"             || true
        "1900-02-29T00:00:00Z"             || false
        "2020-12-31/2021-01-01"            || true
        "2020-01-01T10:20:00.123456789Z"   || true
        "2020-01-01T10:20:00.1234567891Z"  || false
    }

    def 'a timestamp with an offset is converted to UTC, other values are not changed'() {
        expect:
        AbstractQueryParameterDatetime.inUtc(value) == expected

        where:
        value                          || expected
        "2020-01-01T10:00:00+01:00"    || "2020-01-01T09:00:00Z"
        "2020-01-01T10:00:00.5-08:20"  || "2020-01-01T18:20:00.500Z"
        "2020-01-01T10:00:00Z"         || "2020-01-01T10:00:00Z"
        "2020-01-01"                   || "2020-01-01"
        ".."                           || ".."
        "now"                          || "now"
    }
}
