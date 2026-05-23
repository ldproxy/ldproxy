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
 * Locks the parsing of the RFC 7240 {@code Prefer} request header by {@link
 * HeaderPreferTransaction}, used by {@code EndpointTransactions} to decide:
 *
 * <ul>
 *   <li>whether a client asked for {@code respond-async} (currently answered with 501)
 *   <li>which {@code return=…} value (none / minimal / representation) shapes the response body
 *       and the echoed {@code Preference-Applied} header
 *   <li>which {@code handling=…} value (strict / lenient) decides whether the transaction
 *       feature payloads are schema-validated before any provider write; envelope structure is
 *       validated by the parser in both modes
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
class HeaderPreferTransactionSpec extends Specification {

    def "parseReturn returns the fallback when the header list is null or empty"() {
        expect:
        HeaderPreferTransaction.parseReturn(null, HeaderPreferTransaction.PreferReturn.REPRESENTATION) ==
                HeaderPreferTransaction.PreferReturn.REPRESENTATION
        HeaderPreferTransaction.parseReturn([], HeaderPreferTransaction.PreferReturn.MINIMAL) ==
                HeaderPreferTransaction.PreferReturn.MINIMAL
    }

    @Unroll
    def "parseReturn parses '#header' as #expected"() {
        expect:
        HeaderPreferTransaction.parseReturn([header], HeaderPreferTransaction.PreferReturn.REPRESENTATION) == expected

        where:
        header                     || expected
        "return=none"              || HeaderPreferTransaction.PreferReturn.NONE
        "return=minimal"           || HeaderPreferTransaction.PreferReturn.MINIMAL
        "return=representation"    || HeaderPreferTransaction.PreferReturn.REPRESENTATION
        "Return=None"              || HeaderPreferTransaction.PreferReturn.NONE
        "RETURN=MINIMAL"           || HeaderPreferTransaction.PreferReturn.MINIMAL
        " return = minimal "       || HeaderPreferTransaction.PreferReturn.MINIMAL
    }

    def "parseReturn picks the return token out of a multi-value header"() {
        expect:
        HeaderPreferTransaction.parseReturn(
                ["respond-async, return=minimal, wait=10"],
                HeaderPreferTransaction.PreferReturn.REPRESENTATION) == HeaderPreferTransaction.PreferReturn.MINIMAL
    }

    def "parseReturn honours repeated Prefer headers"() {
        expect:
        HeaderPreferTransaction.parseReturn(
                ["respond-async", "return=none"],
                HeaderPreferTransaction.PreferReturn.REPRESENTATION) == HeaderPreferTransaction.PreferReturn.NONE
    }

    def "parseReturn falls back when the return value is unknown or malformed"() {
        expect:
        HeaderPreferTransaction.parseReturn([header], HeaderPreferTransaction.PreferReturn.REPRESENTATION) ==
                HeaderPreferTransaction.PreferReturn.REPRESENTATION

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
        HeaderPreferTransaction.parseReturn(
                ["return=minimal, return=none"],
                HeaderPreferTransaction.PreferReturn.REPRESENTATION) == HeaderPreferTransaction.PreferReturn.MINIMAL
    }

    def "containsToken returns false for null or empty input"() {
        expect:
        !HeaderPreferTransaction.containsToken(null, "respond-async")
        !HeaderPreferTransaction.containsToken([], "respond-async")
    }

    @Unroll
    def "containsToken finds '#token' in #headers"() {
        expect:
        HeaderPreferTransaction.containsToken(headers, token)

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
        !HeaderPreferTransaction.containsToken(["respond-async-xyz"], "respond-async")
        !HeaderPreferTransaction.containsToken(["return=respond-async"], "respond-async")
    }

    def "parseHandling returns the fallback when the header list is null or empty"() {
        expect:
        HeaderPreferTransaction.parseHandling(null, HeaderPreferTransaction.PreferHandling.LENIENT) ==
                HeaderPreferTransaction.PreferHandling.LENIENT
        HeaderPreferTransaction.parseHandling([], HeaderPreferTransaction.PreferHandling.LENIENT) ==
                HeaderPreferTransaction.PreferHandling.LENIENT
    }

    @Unroll
    def "parseHandling parses '#header' as #expected"() {
        expect:
        HeaderPreferTransaction.parseHandling([header], HeaderPreferTransaction.PreferHandling.LENIENT) == expected

        where:
        header                || expected
        "handling=strict"     || HeaderPreferTransaction.PreferHandling.STRICT
        "handling=lenient"    || HeaderPreferTransaction.PreferHandling.LENIENT
        "Handling=Strict"     || HeaderPreferTransaction.PreferHandling.STRICT
        "HANDLING=LENIENT"    || HeaderPreferTransaction.PreferHandling.LENIENT
        " handling = strict " || HeaderPreferTransaction.PreferHandling.STRICT
    }

    def "parseHandling picks the handling token out of a multi-value header"() {
        expect:
        HeaderPreferTransaction.parseHandling(
                ["respond-async, return=minimal, handling=strict"],
                HeaderPreferTransaction.PreferHandling.LENIENT) == HeaderPreferTransaction.PreferHandling.STRICT
    }

    def "parseHandling honours repeated Prefer headers"() {
        expect:
        HeaderPreferTransaction.parseHandling(
                ["return=minimal", "handling=strict"],
                HeaderPreferTransaction.PreferHandling.LENIENT) == HeaderPreferTransaction.PreferHandling.STRICT
    }

    def "parseHandling falls back when the handling value is unknown or malformed"() {
        expect:
        HeaderPreferTransaction.parseHandling([header], HeaderPreferTransaction.PreferHandling.LENIENT) ==
                HeaderPreferTransaction.PreferHandling.LENIENT

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
        HeaderPreferTransaction.parseHandling(
                ["handling=strict, handling=lenient"],
                HeaderPreferTransaction.PreferHandling.LENIENT) == HeaderPreferTransaction.PreferHandling.STRICT
    }

    def "parseHandling rejects parameterised tokens whose name only prefix-matches 'handling'"() {
        // 'handlingmode=strict' must not be parsed as 'handling=strict'
        expect:
        HeaderPreferTransaction.parseHandling(
                ["handlingmode=strict"],
                HeaderPreferTransaction.PreferHandling.LENIENT) == HeaderPreferTransaction.PreferHandling.LENIENT
    }
}
