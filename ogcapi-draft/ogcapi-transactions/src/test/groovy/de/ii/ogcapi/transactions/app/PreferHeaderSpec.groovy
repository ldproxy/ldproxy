/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Locks the parsing of the RFC 7240 {@code Prefer} request header by {@link PreferHeader}, the
 * helper used by {@code EndpointTransactions} to decide:
 *
 * <ul>
 *   <li>whether a client asked for {@code respond-async} (currently answered with 501)
 *   <li>which {@code return=…} value (none / minimal / representation) shapes the response body
 *       and the echoed {@code Preference-Applied} header
 *   <li>which {@code handling=…} value (strict / lenient) decides whether the transaction
 *       envelope and feature payloads are schema-validated before any provider write
 * </ul>
 *
 * The contract under test:
 *
 * <ul>
 *   <li>missing / unparseable input falls back to the caller-supplied default — no exceptions
 *   <li>token matching is case-insensitive (per RFC 7240 §2)
 *   <li>multiple comma-separated tokens in a single header value are honoured, as are repeated
 *       {@code Prefer} headers
 *   <li>unknown {@code return=…} values are ignored (fallback wins) rather than throwing
 * </ul>
 */
class PreferHeaderSpec extends Specification {

    def "parseReturn returns the fallback when the header list is null or empty"() {
        expect:
        PreferHeader.parseReturn(null, PreferHeader.PreferReturn.REPRESENTATION) ==
                PreferHeader.PreferReturn.REPRESENTATION
        PreferHeader.parseReturn([], PreferHeader.PreferReturn.MINIMAL) ==
                PreferHeader.PreferReturn.MINIMAL
    }

    @Unroll
    def "parseReturn parses '#header' as #expected"() {
        expect:
        PreferHeader.parseReturn([header], PreferHeader.PreferReturn.REPRESENTATION) == expected

        where:
        header                     || expected
        "return=none"              || PreferHeader.PreferReturn.NONE
        "return=minimal"           || PreferHeader.PreferReturn.MINIMAL
        "return=representation"    || PreferHeader.PreferReturn.REPRESENTATION
        "Return=None"              || PreferHeader.PreferReturn.NONE
        "RETURN=MINIMAL"           || PreferHeader.PreferReturn.MINIMAL
        " return = minimal "       || PreferHeader.PreferReturn.MINIMAL
    }

    def "parseReturn picks the return token out of a multi-value header"() {
        expect:
        PreferHeader.parseReturn(
                ["respond-async, return=minimal, wait=10"],
                PreferHeader.PreferReturn.REPRESENTATION) == PreferHeader.PreferReturn.MINIMAL
    }

    def "parseReturn honours repeated Prefer headers"() {
        expect:
        PreferHeader.parseReturn(
                ["respond-async", "return=none"],
                PreferHeader.PreferReturn.REPRESENTATION) == PreferHeader.PreferReturn.NONE
    }

    def "parseReturn falls back when the return value is unknown or malformed"() {
        expect:
        PreferHeader.parseReturn([header], PreferHeader.PreferReturn.REPRESENTATION) ==
                PreferHeader.PreferReturn.REPRESENTATION

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

    def "parseReturn returns the first recognised return token when several are present"() {
        // The endpoint's contract is "first match wins"; if a client sends conflicting return
        // tokens (which RFC 7240 considers undefined), we lock the deterministic behaviour.
        expect:
        PreferHeader.parseReturn(
                ["return=minimal, return=none"],
                PreferHeader.PreferReturn.REPRESENTATION) == PreferHeader.PreferReturn.MINIMAL
    }

    def "containsPreferToken returns false for null or empty input"() {
        expect:
        !PreferHeader.containsPreferToken(null, "respond-async")
        !PreferHeader.containsPreferToken([], "respond-async")
    }

    @Unroll
    def "containsPreferToken finds '#token' in #headers"() {
        expect:
        PreferHeader.containsPreferToken(headers, token)

        where:
        headers                                            | token
        ["respond-async"]                                  | "respond-async"
        ["RESPOND-ASYNC"]                                  | "respond-async"
        ["  respond-async  "]                              | "respond-async"
        ["return=minimal, respond-async, wait=10"]         | "respond-async"
        ["return=minimal", "respond-async"]                | "respond-async"
    }

    def "containsPreferToken rejects substring or parameterised matches"() {
        // 'respond-async' must not match 'respond-async-xyz' or 'return=respond-async'
        expect:
        !PreferHeader.containsPreferToken(["respond-async-xyz"], "respond-async")
        !PreferHeader.containsPreferToken(["return=respond-async"], "respond-async")
    }

    def "parseHandling returns the fallback when the header list is null or empty"() {
        expect:
        PreferHeader.parseHandling(null, PreferHeader.PreferHandling.LENIENT) ==
                PreferHeader.PreferHandling.LENIENT
        PreferHeader.parseHandling([], PreferHeader.PreferHandling.LENIENT) ==
                PreferHeader.PreferHandling.LENIENT
    }

    @Unroll
    def "parseHandling parses '#header' as #expected"() {
        expect:
        PreferHeader.parseHandling([header], PreferHeader.PreferHandling.LENIENT) == expected

        where:
        header                || expected
        "handling=strict"     || PreferHeader.PreferHandling.STRICT
        "handling=lenient"    || PreferHeader.PreferHandling.LENIENT
        "Handling=Strict"     || PreferHeader.PreferHandling.STRICT
        "HANDLING=LENIENT"    || PreferHeader.PreferHandling.LENIENT
        " handling = strict " || PreferHeader.PreferHandling.STRICT
    }

    def "parseHandling picks the handling token out of a multi-value header"() {
        expect:
        PreferHeader.parseHandling(
                ["respond-async, return=minimal, handling=strict"],
                PreferHeader.PreferHandling.LENIENT) == PreferHeader.PreferHandling.STRICT
    }

    def "parseHandling honours repeated Prefer headers"() {
        expect:
        PreferHeader.parseHandling(
                ["return=minimal", "handling=strict"],
                PreferHeader.PreferHandling.LENIENT) == PreferHeader.PreferHandling.STRICT
    }

    def "parseHandling falls back when the handling value is unknown or malformed"() {
        expect:
        PreferHeader.parseHandling([header], PreferHeader.PreferHandling.LENIENT) ==
                PreferHeader.PreferHandling.LENIENT

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

    def "parseHandling returns the first recognised handling token when several are present"() {
        expect:
        PreferHeader.parseHandling(
                ["handling=strict, handling=lenient"],
                PreferHeader.PreferHandling.LENIENT) == PreferHeader.PreferHandling.STRICT
    }

    def "parseHandling rejects parameterised tokens whose name only prefix-matches 'handling'"() {
        // 'handlingmode=strict' must not be parsed as 'handling=strict'
        expect:
        PreferHeader.parseHandling(
                ["handlingmode=strict"],
                PreferHeader.PreferHandling.LENIENT) == PreferHeader.PreferHandling.LENIENT
    }
}
