/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions

import groovy.json.JsonSlurper
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * Manual end-to-end smoke spec for the {@code POST /transactions} endpoint using {@code
 * wfs:Transaction} payloads against a running ldproxy that serves an ALKIS / NAS dataset with the
 * Transactions building block enabled and {@code wfsTransaction: true}.
 *
 * <p>Gated on environment variables so the spec is skipped in CI and in the regular unit-test run.
 * Run it locally when ldproxy is up:
 *
 * <pre>{@code
 * SUT_URL=http://localhost:7080 \
 * SUT_TX_PATH=/alkis \
 * ./gradlew :xtraplatform-spatial:... ... # build first if needed
 * ./gradlew :ldproxy:ogcapi-draft:ogcapi-transactions:test --tests "*TransactionalWfs*"
 * }</pre>
 *
 * Required env vars:
 *
 * <ul>
 *   <li>{@code SUT_URL} — base URL of the running ldproxy, e.g. {@code http://localhost:7080}
 *   <li>{@code SUT_TX_PATH} — API base path, e.g. {@code /services/alkis} (the endpoint suffix
 *       {@code /transactions} is appended automatically)
 * </ul>
 *
 * Optional:
 *
 * <ul>
 *   <li>{@code SUT_TX_CONTENT_CRS} — value for the {@code Content-Crs} request header. Defaults
 *       to {@code <http://www.opengis.net/def/crs/EPSG/0/25832>} (the storage CRS of the ALKIS
 *       fixtures). Set to empty to omit the header.
 *   <li>{@code SUT_TX_COLLECTION_ELEMENT_LOCALNAME} — XML element local name used as the
 *       collection key in the wfs:Transaction body. Defaults to {@code AX_Flurstueck}; the
 *       executor matches this case-insensitively against the API's collection ids.
 *   <li>{@code SUT_TX_ID_PREFIX} — first 8 chars of the generated test feature ids. Defaults to
 *       {@code TXTESTAA}. Pick something that cannot exist in the target dataset; the spec
 *       deletes any feature whose id starts with this prefix during cleanup.
 * </ul>
 *
 * <h3>Phases (run as a single {@link Stepwise} feature sequence)</h3>
 *
 * <ol>
 *   <li>Insert all five AX_Flurstueck fixtures in one atomic transaction.
 *   <li>Replace one of them with a payload whose properties differ slightly.
 *   <li>Apply a {@code wfs:Update} (property-level) to another and assert {@code totalUpdated == 1}.
 *   <li>Exercise the RFC 7240 {@code Prefer} header against {@code wfs:Replace} (phases 3a–3d):
 *       <ul>
 *         <li>{@code respond-async} → 501 Not Implemented (short-circuited before body parsing)
 *         <li>{@code return=minimal} → 200, {@code Preference-Applied: return=minimal}, summary
 *             populated but per-action arrays empty
 *         <li>{@code return=none} → 204 No Content, {@code Preference-Applied: return=none},
 *             empty body
 *         <li>{@code return=representation} → 200, {@code Preference-Applied: return=representation},
 *             full per-action details
 *       </ul>
 *   <li>Delete all five to restore the prior state.
 * </ol>
 *
 * <h3>Safety</h3>
 *
 * Every feature id used by the spec is rewritten on load to start with the configured
 * {@code SUT_TX_ID_PREFIX} (default {@code TXTESTAA}). {@link #cleanupSpec} issues an idempotent
 * delete for each generated id so even an interrupted run leaves the dataset in its prior state.
 */
@Requires({
    env['SUT_URL'] != null &&
            env['SUT_TX_PATH'] != null
})
@Stepwise
class TransactionalWfsRESTApiSpec extends Specification {

    static final String XML_CONTENT_TYPE = 'application/xml'
    static final String NS_WFS = 'http://www.opengis.net/wfs/2.0'
    static final String NS_FES = 'http://www.opengis.net/fes/2.0'
    static final String NS_ADV = 'http://www.adv-online.de/namespaces/adv/gid/7.1'
    static final String NS_GML = 'http://www.opengis.net/gml/3.2'

    @Shared String sutUrl = System.getenv('SUT_URL')
    @Shared String sutPath = System.getenv('SUT_TX_PATH')
    @Shared String sutContentCrs =
            System.getenv('SUT_TX_CONTENT_CRS') != null
                    ? System.getenv('SUT_TX_CONTENT_CRS')
                    : '<http://www.opengis.net/def/crs/EPSG/0/25832>'
    @Shared String collectionElement =
            System.getenv('SUT_TX_COLLECTION_ELEMENT_LOCALNAME') ?: 'AX_Flurstueck'
    /**
     * 8-char id prefix; the spec appends an 8-char index to produce 16-char ALKIS-style ids. Pick
     * a prefix that cannot collide with real data — the test deletes every feature whose id
     * starts with this value during cleanup.
     */
    @Shared String idPrefix = System.getenv('SUT_TX_ID_PREFIX') ?: 'TXTESTAA'

    @Shared List<String> testIds = (0..<5).collect { i -> String.format('%s%08d', idPrefix, i) }
    @Shared List<byte[]> fixtures = loadFixtures()

    // @Shared so the client is available in cleanupSpec — Spock does not guarantee instance-field
    // visibility from cleanupSpec, which previously caused the cleanup delete to be silently
    // dropped (NPE swallowed by deleteAllQuietly's try/catch), leaving test features in the DB.
    @Shared
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    String transactionsUrl() { sutUrl + sutPath + '/transactions' }

    HttpResponse<String> postTransaction(String body) {
        return postTransactionWithPrefer(body, [])
    }

    /**
     * POST a wfs:Transaction with one or more {@code Prefer} header values. Each entry of
     * {@code preferTokens} becomes a separate {@code Prefer} request header — that mirrors the
     * RFC 7240 wire form where multiple Prefer headers may be sent — so the executor's parser sees
     * the same multi-header shape as a real client. Pass an empty list to omit the header
     * entirely.
     */
    HttpResponse<String> postTransactionWithPrefer(String body, List<String> preferTokens) {
        def builder = HttpRequest.newBuilder(URI.create(transactionsUrl()))
                .header('Content-Type', XML_CONTENT_TYPE)
                .header('Accept', 'application/json, application/problem+json')
        if (sutContentCrs != null && !sutContentCrs.isEmpty()) {
            builder.header('Content-Crs', sutContentCrs)
        }
        preferTokens.each { token -> builder.header('Prefer', token) }
        builder.POST(BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
        return httpClient.send(builder.build(), BodyHandlers.ofString())
    }

    static byte[] loadResource(String path) {
        def s = TransactionalWfsRESTApiSpec.classLoader.getResourceAsStream(path)
        if (s == null) throw new IllegalStateException("Missing test resource: ${path}")
        return s.bytes
    }

    static List<byte[]> loadFixtures() {
        return [
                'nas/AX_Flurstueck/DENW36AL10000Ehc.xml',
                'nas/AX_Flurstueck/DENW36AL10000Eis.xml',
                'nas/AX_Flurstueck/DENW36AL10000Eiu.xml',
                'nas/AX_Flurstueck/DENW36AL10000EjP.xml',
                'nas/AX_Flurstueck/DENW36AL10000Ejb.xml'
        ].collect { loadResource(it) }
    }

    /**
     * Rewrites a fixture's gml:id and gml:identifier (urn:adv:oid:…) so the inserted feature
     * cannot collide with any real ALKIS feature. Also strips the XML prolog so the payload can
     * be nested inside a wfs:Insert without producing an illegal double prolog.
     */
    static String rewriteFixtureId(byte[] original, String originalId, String newId) {
        String text = new String(original, 'UTF-8')
        text = text.replaceFirst(/<\?xml[^?]*\?>/, '').trim()
        text = text.replaceAll(java.util.regex.Pattern.quote(originalId), newId)
        return text
    }

    static final List<String> originalIds = [
            'DENW36AL10000Ehc',
            'DENW36AL10000Eis',
            'DENW36AL10000Eiu',
            'DENW36AL10000EjP',
            'DENW36AL10000Ejb'
    ]

    /** Per-id rewritten feature payload (no XML prolog, gml:id replaced with the test id). */
    String renderFeature(int i) {
        rewriteFixtureId(fixtures[i], originalIds[i], testIds[i])
    }

    String buildInsertAll() {
        StringBuilder body = new StringBuilder()
        body << '<?xml version="1.0" encoding="UTF-8"?>\n'
        body << "<wfs:Transaction xmlns:wfs=\"${NS_WFS}\" xmlns:adv=\"${NS_ADV}\" xmlns:gml=\"${NS_GML}\">\n"
        body << '  <wfs:Insert handle="ins-all">\n'
        5.times { i -> body << '    ' << renderFeature(i) << '\n' }
        body << '  </wfs:Insert>\n'
        body << '</wfs:Transaction>'
        return body.toString()
    }

    String buildReplace(int i) {
        StringBuilder body = new StringBuilder()
        body << '<?xml version="1.0" encoding="UTF-8"?>\n'
        body << "<wfs:Transaction xmlns:wfs=\"${NS_WFS}\" xmlns:fes=\"${NS_FES}\" xmlns:adv=\"${NS_ADV}\" xmlns:gml=\"${NS_GML}\">\n"
        body << "  <wfs:Replace handle=\"rep-${i}\">\n"
        body << '    ' << renderFeature(i) << '\n'
        body << "    <fes:Filter><fes:ResourceId rid=\"${testIds[i]}\"/></fes:Filter>\n"
        body << '  </wfs:Replace>\n'
        body << '</wfs:Transaction>'
        return body.toString()
    }

    String buildUpdate(int i, String propertyValueRef, String newValue) {
        StringBuilder body = new StringBuilder()
        body << '<?xml version="1.0" encoding="UTF-8"?>\n'
        body << "<wfs:Transaction xmlns:wfs=\"${NS_WFS}\" xmlns:fes=\"${NS_FES}\" xmlns:adv=\"${NS_ADV}\">\n"
        body << "  <wfs:Update typeName=\"adv:${collectionElement}\" handle=\"upd-${i}\">\n"
        body << '    <wfs:Property>\n'
        body << "      <wfs:ValueReference>${propertyValueRef}</wfs:ValueReference>\n"
        body << "      <wfs:Value>${newValue}</wfs:Value>\n"
        body << '    </wfs:Property>\n'
        body << "    <fes:Filter><fes:ResourceId rid=\"${testIds[i]}\"/></fes:Filter>\n"
        body << '  </wfs:Update>\n'
        body << '</wfs:Transaction>'
        return body.toString()
    }

    String buildDeleteAll() {
        StringBuilder body = new StringBuilder()
        body << '<?xml version="1.0" encoding="UTF-8"?>\n'
        body << "<wfs:Transaction xmlns:wfs=\"${NS_WFS}\" xmlns:fes=\"${NS_FES}\" xmlns:adv=\"${NS_ADV}\">\n"
        body << "  <wfs:Delete typeName=\"adv:${collectionElement}\" handle=\"del-all\">\n"
        body << '    <fes:Filter>\n'
        testIds.each { id -> body << "      <fes:ResourceId rid=\"${id}\"/>\n" }
        body << '    </fes:Filter>\n'
        body << '  </wfs:Delete>\n'
        body << '</wfs:Transaction>'
        return body.toString()
    }

    /** Best-effort delete of every generated id; tolerates partial state from a failed run. */
    void deleteAllQuietly() {
        try {
            postTransaction(buildDeleteAll())
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    def cleanupSpec() {
        // Always try to delete the generated features after the spec, even on failure, so the
        // ALKIS dataset is left in its prior state.
        deleteAllQuietly()
    }

    // -----------------------------------------------------------------------------------------
    // Phases
    // -----------------------------------------------------------------------------------------

    def 'phase 1: insert all five AX_Flurstueck features in one atomic wfs:Transaction'() {
        when:
        def r = postTransaction(buildInsertAll())

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.semantic == 'atomic'
        doc.summary.totalInserted == 5
        doc.summary.totalReplaced == 0
        doc.summary.totalUpdated == 0
        doc.summary.totalDeleted == 0
        doc.insertResults instanceof List
        doc.insertResults.size() == 5
    }

    def 'phase 2: replace the first inserted feature'() {
        when:
        def r = postTransaction(buildReplace(0))

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.summary.totalReplaced == 1
        doc.replaceResults instanceof List
        doc.replaceResults.size() == 1
        doc.replaceResults[0].toString().endsWith('/items/' + testIds[0])
    }

    def 'phase 3: wfs:Update succeeds and reports one updated feature'() {
        when:
        def r = postTransaction(buildUpdate(1, 'flurstueckskennzeichen', '999999'))

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.semantic == 'atomic'
        doc.summary.totalUpdated == 1
        doc.updateResults instanceof List
        doc.updateResults.size() == 1
        doc.updateResults[0].toString().endsWith('/items/' + testIds[1])
    }

    // ---- Prefer header phases ----------------------------------------------------------------
    //
    // These run a wfs:Replace of one of the inserted fixtures with a different Prefer value each,
    // so we can lock both the Preference-Applied response header and the shape of the response
    // body. respond-async is intentionally first: its 501 short-circuit happens before the body
    // is read, so it doesn't disturb the dataset.

    def 'phase 3a: Prefer: respond-async returns 501 Not Implemented without parsing the body'() {
        when:
        // The endpoint short-circuits before reading the request body, so any payload is fine.
        def r = postTransactionWithPrefer('<irrelevant-not-even-xml/>', ['respond-async'])

        then:
        r.statusCode() == 501
        r.headers().firstValue('Content-Type').orElse('').startsWith('text/plain')
        r.body().contains('Asynchronous')
    }

    def 'phase 3b: Prefer: return=minimal echoes Preference-Applied and omits per-action arrays'() {
        when:
        def r = postTransactionWithPrefer(buildReplace(2), ['return=minimal'])

        then:
        r.statusCode() == 200
        r.headers().firstValue('Preference-Applied').orElse('') == 'return=minimal'
        def doc = new JsonSlurper().parseText(r.body())
        doc.semantic == 'atomic'
        doc.summary.totalReplaced == 1
        // The endpoint strips the four per-action arrays entirely (rather than emitting empty
        // arrays) when return=minimal — see EndpointTransactions.renderBody().
        !doc.containsKey('insertResults')
        !doc.containsKey('replaceResults')
        !doc.containsKey('updateResults')
        !doc.containsKey('deleteResults')
    }

    def 'phase 3c: Prefer: return=none returns 204 No Content with Preference-Applied and empty body'() {
        when:
        def r = postTransactionWithPrefer(buildReplace(3), ['return=none'])

        then:
        r.statusCode() == 204
        r.headers().firstValue('Preference-Applied').orElse('') == 'return=none'
        r.body().isEmpty()
    }

    def 'phase 3d: Prefer: return=representation echoes Preference-Applied with full details'() {
        when:
        def r = postTransactionWithPrefer(buildReplace(4), ['return=representation'])

        then:
        r.statusCode() == 200
        r.headers().firstValue('Preference-Applied').orElse('') == 'return=representation'
        def doc = new JsonSlurper().parseText(r.body())
        doc.summary.totalReplaced == 1
        doc.replaceResults instanceof List
        doc.replaceResults.size() == 1
        doc.replaceResults[0].toString().endsWith('/items/' + testIds[4])
    }

    def 'phase 4: delete all five test features to restore the prior state'() {
        when:
        def r = postTransaction(buildDeleteAll())

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.summary.totalDeleted == 5
        doc.deleteResults instanceof List
        doc.deleteResults.size() == 5
    }
}
