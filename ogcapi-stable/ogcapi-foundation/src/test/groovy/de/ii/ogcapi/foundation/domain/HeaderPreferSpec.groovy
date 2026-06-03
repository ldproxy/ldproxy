/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Locks the parsing of the RFC 7240 {@code Prefer} request header by the foundation-level
 * helpers on {@link HeaderPrefer}: the {@code handling=…} preference, the unparameterised-token
 * check used for {@code respond-async}, and the underlying parameterised-token engine.
 *
 * <p>Contract under test:
 * <ul>
 *   <li>missing / unparseable input falls back to the caller-supplied default — no exceptions
 *   <li>token matching is case-insensitive (per RFC 7240 §2)
 *   <li>multiple comma-separated tokens in a single header value are honoured, as are repeated
 *       {@code Prefer} headers
 *   <li>conflicting mutually-exclusive values collapse to the fallback (RFC 7240 §2: "a request
 *       containing both preferences can be treated as though neither were specified")
 *   <li>parameter-name matching is exact: {@code handlingmode=strict} does not match {@code
 *       handling}
 * </ul>
 */
class HeaderPreferSpec extends Specification {

    def "parseHandling returns the fallback when the header list is null or empty"() {
        expect:
        HeaderPrefer.parseHandling(null, HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.LENIENT
        HeaderPrefer.parseHandling([], HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.LENIENT
    }

    @Unroll
    def "parseHandling parses '#header' as #expected"() {
        expect:
        HeaderPrefer.parseHandling([header], HeaderPrefer.Handling.LENIENT) == expected

        where:
        header                || expected
        "handling=strict"     || HeaderPrefer.Handling.STRICT
        "handling=lenient"    || HeaderPrefer.Handling.LENIENT
        "Handling=Strict"     || HeaderPrefer.Handling.STRICT
        "HANDLING=LENIENT"    || HeaderPrefer.Handling.LENIENT
        " handling = strict " || HeaderPrefer.Handling.STRICT
    }

    def "parseHandling picks the handling token out of a multi-value header"() {
        expect:
        HeaderPrefer.parseHandling(
                ["respond-async, return=minimal, handling=strict"],
                HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.STRICT
    }

    def "parseHandling honours repeated Prefer headers"() {
        expect:
        HeaderPrefer.parseHandling(
                ["return=minimal", "handling=strict"],
                HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.STRICT
    }

    def "parseHandling falls back when the handling value is unknown or malformed"() {
        expect:
        HeaderPrefer.parseHandling([header], HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.LENIENT

        where:
        header << [
                "handling=loose",         // unknown enum value
                "handling=",              // empty value
                "handling",               // no '=' at all
                "return=minimal",         // unrelated token
                "respond-async",          // unrelated token
                "",                       // empty string
                "  "                      // whitespace only
        ]
    }

    def "parseHandling falls back when both handling values are present (RFC 7240)"() {
        // Per RFC 7240 §2: "a request containing both preferences can be treated as though
        // neither were specified". We collapse mutually-exclusive values to the fallback.
        expect:
        HeaderPrefer.parseHandling(
                ["handling=strict, handling=lenient"],
                HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.LENIENT
        HeaderPrefer.parseHandling(
                ["handling=strict", "handling=lenient"],
                HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.LENIENT
    }

    def "parseHandling collapses repeated identical handling values to that value"() {
        // Distinct-value count is what triggers the fallback, not repetition.
        expect:
        HeaderPrefer.parseHandling(
                ["handling=strict, handling=strict"],
                HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.STRICT
    }

    def "parseHandling rejects parameterised tokens whose name only prefix-matches 'handling'"() {
        expect:
        HeaderPrefer.parseHandling(
                ["handlingmode=strict"],
                HeaderPrefer.Handling.LENIENT) == HeaderPrefer.Handling.LENIENT
    }

    def "containsToken returns false for null or empty input"() {
        expect:
        !HeaderPrefer.containsToken(null, "respond-async")
        !HeaderPrefer.containsToken([], "respond-async")
    }

    @Unroll
    def "containsToken finds '#token' in #headers"() {
        expect:
        HeaderPrefer.containsToken(headers, token)

        where:
        headers                                            | token
        ["respond-async"]                                  | "respond-async"
        ["RESPOND-ASYNC"]                                  | "respond-async"
        ["  respond-async  "]                              | "respond-async"
        ["return=minimal, respond-async, wait=10"]         | "respond-async"
        ["return=minimal", "respond-async"]                | "respond-async"
    }

    def "containsToken rejects substring or parameterised matches"() {
        // 'respond-async' must not match 'respond-async-xyz' or 'return=respond-async'
        expect:
        !HeaderPrefer.containsToken(["respond-async-xyz"], "respond-async")
        !HeaderPrefer.containsToken(["return=respond-async"], "respond-async")
    }

    def "parseReturn returns the fallback when the header list is null or empty"() {
        expect:
        HeaderPrefer.parseReturn(null, HeaderPrefer.Return.REPRESENTATION) == HeaderPrefer.Return.REPRESENTATION
        HeaderPrefer.parseReturn([], HeaderPrefer.Return.MINIMAL) == HeaderPrefer.Return.MINIMAL
    }

    @Unroll
    def "parseReturn parses '#header' as #expected"() {
        expect:
        HeaderPrefer.parseReturn([header], HeaderPrefer.Return.REPRESENTATION) == expected

        where:
        header                     || expected
        "return=none"              || HeaderPrefer.Return.NONE
        "return=minimal"           || HeaderPrefer.Return.MINIMAL
        "return=representation"    || HeaderPrefer.Return.REPRESENTATION
        "Return=None"              || HeaderPrefer.Return.NONE
        "RETURN=MINIMAL"           || HeaderPrefer.Return.MINIMAL
        " return = minimal "       || HeaderPrefer.Return.MINIMAL
    }

    def "parseReturn picks the return token out of a multi-value header"() {
        expect:
        HeaderPrefer.parseReturn(
                ["respond-async, return=minimal, wait=10"],
                HeaderPrefer.Return.REPRESENTATION) == HeaderPrefer.Return.MINIMAL
    }

    def "parseReturn honours repeated Prefer headers"() {
        expect:
        HeaderPrefer.parseReturn(
                ["respond-async", "return=none"],
                HeaderPrefer.Return.REPRESENTATION) == HeaderPrefer.Return.NONE
    }

    def "parseReturn falls back when the return value is unknown or malformed"() {
        expect:
        HeaderPrefer.parseReturn([header], HeaderPrefer.Return.REPRESENTATION) == HeaderPrefer.Return.REPRESENTATION

        where:
        header << [
                "return=full",            // unknown enum value
                "return=",                // empty value
                "return",                 // no '=' at all
                "wait=10",                // unrelated token
                "respond-async",          // unrelated token
                "",                       // empty string
                "  "                      // whitespace only
        ]
    }

    def "parseReturn falls back when conflicting return values are present (RFC 7240)"() {
        expect:
        HeaderPrefer.parseReturn(
                ["return=minimal, return=none"],
                HeaderPrefer.Return.REPRESENTATION) == HeaderPrefer.Return.REPRESENTATION
    }
}
