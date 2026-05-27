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
 * Manual end-to-end smoke spec for the {@code POST /transactions} endpoint. Exercises both the
 * {@code wfs:Transaction} XML payload path and the {@code application/ogc-tx+json} envelope path
 * against a running ldproxy that serves an ALKIS / NAS dataset with the Transactions building
 * block enabled and {@code wfsTransaction: true}.
 *
 * <p>Gated on environment variables so the spec is skipped in CI and in the regular unit-test run.
 * Run it locally when ldproxy is up:
 *
 * <pre>{@code
 * SUT_URL=http://localhost:7080 \
 * SUT_TX_PATH=/alkis \
 * ./gradlew :ldproxy:ogcapi-draft:ogcapi-transactions:test --tests "*TransactionalREST*"
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
 *   <li>{@code SUT_TX_CONTENT_CRS} — value for the {@code Content-Crs} request header on the
 *       WFS-XML and JSON happy-path phases. Defaults to
 *       {@code <http://www.opengis.net/def/crs/EPSG/0/25832>} (the storage CRS of the ALKIS
 *       fixtures). Set to empty to omit the header. Phases 5–8 manage their own Content-Crs value
 *       independently of this default since CRS handling is what they exercise.
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
 *   <li>Exercise the RFC 7240 {@code Prefer} header against {@code wfs:Replace} (phases 3a–3d).
 *   <li>Exercise {@code Prefer: handling=strict} end-to-end (phases 3e–3i).
 *   <li>Phases 5–9 lock the Part-11 CRS / GeoJSON requirements at the {@code /transactions}
 *       boundary:
 *       <ul>
 *         <li>5: JSON insert with {@code Content-Crs: <storage CRS>} — coords round-trip unchanged
 *             (/req/features/content-crs-header).
 *         <li>6: JSON insert with {@code Content-Crs: <CRS84>} — server reprojects to storage CRS
 *             (/req/features/content-crs-header, contrast with phase 5).
 *         <li>7: JSON insert with no {@code Content-Crs} header — server defaults to CRS84 and
 *             matches phase 6 (/req/features/default-crs).
 *         <li>8: JSON insert with {@code Content-Crs: <unsupported CRS>} → 4xx
 *             (/req/features/crs-other-crs B).
 *         <li>9: GET round-trip exposes the inserted geometry as the top-level {@code geometry}
 *             member, not as a property (/req/features/geojson-geom).
 *       </ul>
 *   <li>Delete every inserted test feature to restore the prior state.
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
class TransactionalRESTApiSpec extends Specification {

    static final String XML_CONTENT_TYPE = 'application/xml'
    static final String JSON_TX_CONTENT_TYPE = 'application/ogc-tx+json'
    static final String NS_WFS = 'http://www.opengis.net/wfs/2.0'
    static final String NS_FES = 'http://www.opengis.net/fes/2.0'
    static final String NS_ADV = 'http://www.adv-online.de/namespaces/adv/gid/7.1'
    static final String NS_GML = 'http://www.opengis.net/gml/3.2'

    // CRS URIs used by the Part-11 CRS phases. Stored without angle brackets; postJson wraps them
    // for the Content-Crs header, fetchReceivableFeatureInCrs uses them bare in the crs= query.
    static final String CRS_STORAGE_URI = 'http://www.opengis.net/def/crs/EPSG/0/25832'
    static final String CRS_84_URI = 'http://www.opengis.net/def/crs/OGC/1.3/CRS84'
    // Syntactically valid CRS URI that the collection does not declare as supported. Used by
    // phase 8 to exercise the "Content-Crs declares a CRS the server does not support" branch
    // (/req/features/crs-other-crs B). Pick a clearly-bogus EPSG code so the rejection is on the
    // CRS lookup, not on URI parsing.
    static final String CRS_BOGUS_URI = 'http://www.opengis.net/def/crs/EPSG/0/99999'

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
    /** Separate test id used only by the strict-mode mixed-batch JSON phase (3i). */
    @Shared String partialOkId = String.format('%s%08d', idPrefix, 99)
    /** Three reserved ids for the CRS phases (5, 6, 7). Phase 8 does not insert anything. */
    @Shared List<String> crsTestIds = (50..<53).collect { i -> String.format('%s%08d', idPrefix, i) }
    /**
     * Id used only by phase 8 — phase 8 expects the server to reject the insert before it lands.
     * Included in the cleanup delete filter (the delete is idempotent if the id does not exist), so
     * a regression that lets the insert through still gets cleaned up. Phase 4 asserts an exact
     * deleted count, so such a regression also fails loudly there.
     */
    @Shared String phase8RejectedId = String.format('%s%08d', idPrefix, 98)
    @Shared List<byte[]> fixtures = loadFixtures()

    /**
     * Collection {@code itemCount} captured by phase {@code A0} before any test write. Set to
     * {@code null} if the collection metadata does not expose an item count, in which case the
     * metadata round-trip phases ({@code A1}, {@code A2}) skip the count assertion (but still
     * exercise the {@code lastModified} assertion when the server publishes that field).
     */
    @Shared Long baselineItemCount
    /**
     * Collection {@code lastModified} captured by phase {@code A0}. Optional in the OGC API
     * Collections response; when present, must advance after a successful write transaction
     * (proves {@code featureProvider.changes().handle(...)} ran and the listener bumped the
     * collection's last-modified clock).
     */
    @Shared String baselineLastModified

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
        return postTransactionWithPrefer(body, preferTokens, XML_CONTENT_TYPE)
    }

    HttpResponse<String> postTransactionWithPrefer(
            String body, List<String> preferTokens, String contentType) {
        def builder = HttpRequest.newBuilder(URI.create(transactionsUrl()))
                .header('Content-Type', contentType)
                .header('Accept', 'application/json, application/problem+json')
        if (sutContentCrs != null && !sutContentCrs.isEmpty()) {
            builder.header('Content-Crs', sutContentCrs)
        }
        preferTokens.each { token -> builder.header('Prefer', token) }
        builder.POST(BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
        return httpClient.send(builder.build(), BodyHandlers.ofString())
    }

    /**
     * POST an {@code application/ogc-tx+json} envelope with the {@code Content-Crs} header set to
     * the literal {@code contentCrsHeaderValue} (caller supplies the angle-bracket wrapping). Pass
     * {@code null} or empty to omit the header — that is the case Part 11
     * {@code /req/features/default-crs} cares about. No {@code Prefer} header is sent.
     */
    HttpResponse<String> postJson(String body, String contentCrsHeaderValue) {
        def builder = HttpRequest.newBuilder(URI.create(transactionsUrl()))
                .header('Content-Type', JSON_TX_CONTENT_TYPE)
                .header('Accept', 'application/json, application/problem+json')
        if (contentCrsHeaderValue != null && !contentCrsHeaderValue.isEmpty()) {
            builder.header('Content-Crs', contentCrsHeaderValue)
        }
        builder.POST(BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
        return httpClient.send(builder.build(), BodyHandlers.ofString())
    }

    static byte[] loadResource(String path) {
        def s = TransactionalRESTApiSpec.classLoader.getResourceAsStream(path)
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
        // testIds[0..4] from phase 1, partialOkId from phase 3i, crsTestIds[0..2] from phases 5–7,
        // phase8RejectedId from phase 8 (should never exist; included as a safety net).
        (testIds + [partialOkId] + crsTestIds + [phase8RejectedId]).each { id ->
            body << "      <fes:ResourceId rid=\"${id}\"/>\n"
        }
        body << '    </fes:Filter>\n'
        body << '  </wfs:Delete>\n'
        body << '</wfs:Transaction>'
        return body.toString()
    }

    /** Canonical (lowercase) collection id ldproxy uses on the JSON paths. */
    String jsonCollectionId() { collectionElement.toLowerCase(java.util.Locale.ROOT) }

    /**
     * Fetches one of the inserted test features as a write-ready GeoJSON document. Uses the
     * receivable-properties profile and the storage CRS so the body can be re-inserted without
     * dropping readOnly fields or reprojecting.
     */
    String fetchReceivableFeature(String featureId) {
        // The Content-Crs header carries the URI wrapped in angle brackets; the query parameter
        // needs the bare URI, so strip those if present.
        String storageCrsUri = sutContentCrs
                .replaceFirst(/^</, '')
                .replaceFirst(/>$/, '')
        return fetchReceivableFeatureInCrs(featureId, storageCrsUri)
    }

    /**
     * Receivable GeoJSON for {@code featureId} expressed in {@code crsUri} (bare URI, no angle
     * brackets). Used by the CRS phases to obtain the same source feature in different CRSs.
     */
    String fetchReceivableFeatureInCrs(String featureId, String crsUri) {
        // Build the URL as a single GString — multi-line string concatenation with leading `+` on
        // continuation lines is a Groovy parse trap (each leading `+` is read as unary
        // String.positive() and throws at runtime).
        String url = "${sutUrl}${sutPath}/collections/${jsonCollectionId()}/items/${featureId}" +
                "?f=json&profile=all-as-receivable&crs=${URLEncoder.encode(crsUri, 'UTF-8')}"
        def req = HttpRequest.newBuilder(URI.create(url))
                .header('Accept', 'application/geo+json, application/json')
                .GET()
                .build()
        def resp = httpClient.send(req, BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                    "GET ${url} returned ${resp.statusCode()}: ${resp.body()}")
        }
        return resp.body()
    }

    /** Wraps a single GeoJSON Feature in a one-action atomic ogc-tx+json insert envelope. */
    String jsonInsertEnvelope(String featureJson) {
        return '{"semantic":"atomic","transaction":[{"action":"insert","collection":"' +
                jsonCollectionId() + '","items":[' + featureJson + ']}]}'
    }

    /** Replace the GeoJSON feature's top-level "id" with the given value. */
    static String rewriteJsonId(String featureJson, String newId) {
        def feature = new JsonSlurper().parseText(featureJson)
        feature.id = newId
        return groovy.json.JsonOutput.toJson(feature)
    }

    /**
     * Recursively walk a GeoJSON {@code coordinates} value and collect every (x, y) point. Works
     * for Point, LineString, Polygon, MultiPolygon, etc. — anything where the innermost list of
     * numbers is one position. The Z coordinate (if any) is ignored: the CRS bbox checks below
     * only care about the horizontal extent.
     */
    static List<double[]> flattenPoints(Object node) {
        List<double[]> out = new ArrayList<>()
        flattenPointsInto(node, out)
        return out
    }

    static void flattenPointsInto(Object node, List<double[]> out) {
        if (node instanceof List) {
            if (!node.isEmpty() && node[0] instanceof Number) {
                out.add([((Number) node[0]).doubleValue(),
                         ((Number) node[1]).doubleValue()] as double[])
            } else {
                node.each { flattenPointsInto(it, out) }
            }
        }
    }

    /** Horizontal bbox [minX, minY, maxX, maxY] of a GeoJSON geometry. */
    static double[] bbox(Object geometry) {
        def points = flattenPoints(geometry.coordinates)
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Geometry has no coordinates: ${geometry}")
        }
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY
        for (double[] p : points) {
            if (p[0] < minX) minX = p[0]
            if (p[0] > maxX) maxX = p[0]
            if (p[1] < minY) minY = p[1]
            if (p[1] > maxY) maxY = p[1]
        }
        return [minX, minY, maxX, maxY] as double[]
    }

    static boolean bboxApproxEqual(double[] a, double[] b, double tol) {
        return Math.abs(a[0] - b[0]) <= tol && Math.abs(a[1] - b[1]) <= tol &&
                Math.abs(a[2] - b[2]) <= tol && Math.abs(a[3] - b[3]) <= tol
    }

    /**
     * GET /collections/{cid}?f=json and return the parsed JSON document. Used by the metadata
     * round-trip phases ({@code A0}, {@code A1}, {@code A2}) to check that the
     * {@code featureProvider.changes().handle(...)} dispatch performed by the executor after each
     * transaction is keeping the collection's {@code itemCount} / {@code lastModified} fields
     * current. The receivable / CRS phases above intentionally do not exercise this path.
     */
    Map fetchCollectionMeta() {
        String url = "${sutUrl}${sutPath}/collections/${jsonCollectionId()}?f=json"
        def req = HttpRequest.newBuilder(URI.create(url))
                .header('Accept', 'application/json')
                .GET()
                .build()
        def resp = httpClient.send(req, BodyHandlers.ofString())
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                    "GET ${url} returned ${resp.statusCode()}: ${resp.body()}")
        }
        return (Map) new JsonSlurper().parseText(resp.body())
    }

    /**
     * Extract the (nullable) {@code itemCount} from the collection metadata. Returns {@code null}
     * if the field is absent — some collection configurations don't expose a count, and the spec
     * is tolerant of that.
     */
    static Long itemCount(Map collection) {
        Object raw = collection.get('itemCount')
        return raw instanceof Number ? ((Number) raw).longValue() : null
    }

    /**
     * Poll the collection metadata up to ~5s waiting for {@code itemCount} to reach
     * {@code expected}. Returns the final document. Change emission is async (single-threaded
     * executor inside {@code FeatureChangeHandlerImpl}); without a wait the metadata can still be
     * stale right after a successful POST returns.
     */
    Map waitForItemCount(Long expected) {
        Map last = null
        for (int i = 0; i < 50; i++) {
            last = fetchCollectionMeta()
            Long got = itemCount(last)
            if (Objects.equals(got, expected)) return last
            Thread.sleep(100)
        }
        return last
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

    def 'phase A0: capture baseline collection metadata before any test write'() {
        when:
        Map meta = fetchCollectionMeta()
        // Capture into @Shared state for the later A1 / A2 phases. itemCount and lastModified are
        // both optional in the OGC API Collections response, so either may be null — the later
        // phases skip the assertion when the baseline is null.
        baselineItemCount = itemCount(meta)
        baselineLastModified = (String) meta.get('lastModified')

        then: 'GET succeeds and exposes a collection id'
        meta != null
        meta.get('id') != null
    }

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

    // ---- handling=strict phases --------------------------------------------------------------
    //
    // The three phases below exercise the Prefer: handling=strict path end-to-end:
    //   * 3e: WFS payload + strict + valid feature — proves the strict wiring does not break the
    //     happy path. Per-feature GML validation runs via FeaturesFormatGml.validate; if the
    //     dataset has no GmlConfiguration.schemaLocations configured, that validator logs a WARN
    //     and silently skips, so this phase still passes.
    //   * 3f and 3g: switch the content type to application/ogc-tx+json and exercise the JSON
    //     envelope checks performed by the streaming parser. These don't require a GeoJSON-capable
    //     collection because malformed envelope structure is rejected before DB access.

    def 'phase 3e: Prefer: handling=strict + valid wfs:Replace passes per-feature validation'() {
        when:
        def r = postTransactionWithPrefer(buildReplace(0), ['handling=strict'])

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.summary.totalReplaced == 1
        doc.replaceResults instanceof List
        doc.replaceResults.size() == 1
        doc.replaceResults[0].toString().endsWith('/items/' + testIds[0])
    }

    def 'phase 3f: Prefer: handling=strict + valid empty ogc-tx+json envelope is accepted'() {
        when:
        def r = postTransactionWithPrefer(
                '{"semantic": "atomic", "transaction": []}',
                ['handling=strict'],
                JSON_TX_CONTENT_TYPE)

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.semantic == 'atomic'
        doc.summary.totalInserted == 0
        doc.summary.totalReplaced == 0
        doc.summary.totalUpdated == 0
        doc.summary.totalDeleted == 0
    }

    def 'phase 3g: Prefer: handling=strict rejects an ogc-tx+json envelope missing the transaction array'() {
        when:
        def r = postTransactionWithPrefer(
                '{"semantic": "atomic"}',
                ['handling=strict'],
                JSON_TX_CONTENT_TYPE)

        then:
        r.statusCode() == 400
        // The endpoint maps parser IllegalArgumentException to BadRequestException. Body shape is
        // the API's default 400 representation, so assert on the request-specific text.
        r.body().toLowerCase().contains('transaction')
    }

    def 'phase 3h: handling=strict + batch JSON insert where every item is invalid → FAILED action, one exception per item'() {
        given:
        // Two clearly-invalid GeoJSON items: one with a non-Feature type, one with no type at all.
        // Both should fail FeaturesFormatGeoJson.validate against the collection's receivables
        // schema, so the executor returns a FAILED insert action with per-item error messages
        // surfaced as separate entries in exceptions[] — one per rejected feature.
        String body = """
            {
              "semantic": "batch",
              "transaction": [{
                "action": "insert",
                "collection": "${jsonCollectionId()}",
                "items": [
                  {"type": "NotAFeature"},
                  {"foo": "bar"}
                ]
              }]
            }
        """

        when:
        def r = postTransactionWithPrefer(body, ['handling=strict'], JSON_TX_CONTENT_TYPE)

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.semantic == 'batch'
        doc.summary.totalInserted == 0
        doc.exceptions instanceof List
        // One exception entry per rejected feature, carrying that feature's own validation
        // message in detail. Items are reported in input order.
        doc.exceptions.size() == 2
        doc.exceptions.every { it.action == 'insert' && it.status == 422 }
        doc.exceptions[0].featureIndexes == [1]
        doc.exceptions[1].featureIndexes == [2]
        // Each entry carries its own non-empty detail message (the format validator's
        // per-feature error). We don't pin the exact text — just confirm it's populated and
        // non-trivial.
        doc.exceptions.every { it.detail instanceof String && !((String) it.detail).isEmpty() }
    }

    def 'phase 3i: handling=strict + batch JSON insert with one valid + two invalid items → SUCCESS plus per-item exceptions for the rejected ones'() {
        given:
        // Build a known-good GeoJSON insert payload by fetching one of the test features in
        // receivable-properties form and rewriting its id to a fresh value so the insert does not
        // collide with the existing testIds[*]. The fresh id is included in deleteAllQuietly().
        String validFeature = rewriteJsonId(fetchReceivableFeature(testIds[0]), partialOkId)
        String body = """
            {
              "semantic": "batch",
              "transaction": [{
                "action": "insert",
                "collection": "${jsonCollectionId()}",
                "items": [
                  ${validFeature},
                  {"type": "NotAFeature"},
                  {"foo": "bar"}
                ]
              }]
            }
        """

        when:
        def r = postTransactionWithPrefer(body, ['handling=strict'], JSON_TX_CONTENT_TYPE)

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.semantic == 'batch'
        doc.summary.totalInserted == 1
        // The action is SUCCESS — at least one item wrote — and the valid item appears in
        // insertResults.
        doc.insertResults instanceof List
        doc.insertResults.size() == 1
        doc.insertResults[0].toString().endsWith('/items/' + partialOkId)
        // The two rejected items appear in exceptions[], one entry per rejected feature with its
        // own detail message. The first input item (the valid one) is not in exceptions.
        doc.exceptions instanceof List
        doc.exceptions.size() == 2
        doc.exceptions.every { it.action == 'insert' && it.status == 422 }
        doc.exceptions[0].featureIndexes == [2]
        doc.exceptions[1].featureIndexes == [3]
        doc.exceptions.every { it.detail instanceof String && !((String) it.detail).isEmpty() }
    }

    // ---- Part-11 CRS / GeoJSON-geom phases ---------------------------------------------------
    //
    // Phases 5–9 exercise the four Part-11 requirements that sit at the request boundary:
    //   * 5 (Req 22) — Content-Crs honored when set: insert with the storage CRS, GET back in the
    //     same CRS, bbox is preserved (server did NOT silently reproject).
    //   * 6 (Req 22, contrast) — Content-Crs declares CRS84 with CRS84 coords; GET back in storage
    //     CRS yields a bbox that matches the same reference feature in storage CRS (server
    //     reprojected as declared).
    //   * 7 (Req 23) — No Content-Crs header, CRS84 coords; result matches phase 6 (default is
    //     CRS84, not the storage CRS or something else).
    //   * 8 (Req 24B) — Content-Crs declares a CRS the collection does not support → 4xx.
    //   * 9 (Req 27) — On GET, the primary-geometry property surfaces as the top-level "geometry"
    //     member, not as an entry in "properties".
    //
    // All five reuse one of the phase-1 fixtures as their source feature, fetched in the
    // appropriate CRS via the receivable-properties profile.

    def 'phase 5: JSON insert with Content-Crs = storage CRS round-trips coords unchanged [Req 22]'() {
        given:
        String fresh = crsTestIds[0]
        String featureInStorage =
                rewriteJsonId(fetchReceivableFeatureInCrs(testIds[0], CRS_STORAGE_URI), fresh)
        String envelope = jsonInsertEnvelope(featureInStorage)

        when: 'POST the feature back with its own storage-CRS coords and Content-Crs: <storage>'
        def r = postJson(envelope, '<' + CRS_STORAGE_URI + '>')

        then:
        r.statusCode() == 200
        def insertDoc = new JsonSlurper().parseText(r.body())
        insertDoc.summary.totalInserted == 1
        insertDoc.insertResults.size() == 1
        insertDoc.insertResults[0].toString().endsWith('/items/' + fresh)

        when: 'GET the inserted feature back in the same storage CRS'
        def stored = new JsonSlurper().parseText(
                fetchReceivableFeatureInCrs(fresh, CRS_STORAGE_URI))
        def posted = new JsonSlurper().parseText(featureInStorage)

        then: 'bbox is preserved to sub-cm — server did not silently reproject'
        stored.geometry != null
        stored.geometry.type == posted.geometry.type
        bboxApproxEqual(bbox(stored.geometry), bbox(posted.geometry), 0.01)
    }

    def 'phase 6: JSON insert with Content-Crs = CRS84 + CRS84 coords reprojects to storage CRS [Req 22 contrast]'() {
        given:
        String fresh = crsTestIds[1]
        // Same source feature as phase 5 but fetched in CRS84 — so the body genuinely carries
        // CRS84 coordinates (longitudes ~7, latitudes ~50 for the Bonn fixture), not storage-CRS
        // coordinates. Without the server honoring the Content-Crs header, these would be stored
        // as raw EPSG:25832 northings/eastings near (7, 50) — nonsense.
        String featureInCrs84 =
                rewriteJsonId(fetchReceivableFeatureInCrs(testIds[0], CRS_84_URI), fresh)
        String envelope = jsonInsertEnvelope(featureInCrs84)

        when:
        def r = postJson(envelope, '<' + CRS_84_URI + '>')

        then:
        r.statusCode() == 200
        new JsonSlurper().parseText(r.body()).summary.totalInserted == 1

        when: 'GET in storage CRS — coords should sit in the Bonn EPSG:25832 range'
        def stored = new JsonSlurper().parseText(
                fetchReceivableFeatureInCrs(fresh, CRS_STORAGE_URI))
        def reference = new JsonSlurper().parseText(
                fetchReceivableFeatureInCrs(testIds[0], CRS_STORAGE_URI))

        then: 'storage-CRS bbox matches the reference feature within sub-meter reprojection round-trip error'
        bboxApproxEqual(bbox(stored.geometry), bbox(reference.geometry), 1.0)

        and: 'and the storage-CRS coords are clearly EPSG:25832-shaped, not raw lon/lat'
        // EPSG:25832 eastings near Bonn are ~360 000 (huge); raw CRS84 longitudes are ~7 (tiny).
        // The threshold catches a regression where the server takes the CRS84 coords verbatim.
        bbox(stored.geometry)[0] > 100_000.0
        bbox(stored.geometry)[1] > 1_000_000.0
    }

    def 'phase 7: JSON insert with no Content-Crs header defaults to CRS84 [Req 23]'() {
        given:
        String fresh = crsTestIds[2]
        // Same CRS84 body as phase 6, but no Content-Crs header on the request.
        String featureInCrs84 =
                rewriteJsonId(fetchReceivableFeatureInCrs(testIds[0], CRS_84_URI), fresh)
        String envelope = jsonInsertEnvelope(featureInCrs84)

        when:
        def r = postJson(envelope, null)

        then:
        r.statusCode() == 200
        new JsonSlurper().parseText(r.body()).summary.totalInserted == 1

        when: 'GET in storage CRS — must match phase 6 (server defaulted to CRS84 and reprojected)'
        def stored = new JsonSlurper().parseText(
                fetchReceivableFeatureInCrs(fresh, CRS_STORAGE_URI))
        def storedFromPhase6 = new JsonSlurper().parseText(
                fetchReceivableFeatureInCrs(crsTestIds[1], CRS_STORAGE_URI))

        then: 'bbox matches phase 6 to sub-cm — same input, same interpretation'
        // If the default were storage CRS (or anything other than CRS84), the bbox here would
        // differ from phase 6 by tens of thousands of meters.
        bboxApproxEqual(bbox(stored.geometry), bbox(storedFromPhase6.geometry), 0.01)
    }

    def 'phase 8: JSON insert with Content-Crs declaring an unsupported CRS is rejected [Req 24B]'() {
        given:
        // Body shape is irrelevant — we only need a syntactically valid envelope so the request
        // reaches the CRS check. Reuse the phase-5 feature payload but with an id that is not
        // shared with any other phase so a regression that accidentally accepts the insert is
        // still caught by cleanupSpec.
        String feature =
                rewriteJsonId(fetchReceivableFeatureInCrs(testIds[0], CRS_STORAGE_URI),
                        phase8RejectedId)
        String envelope = jsonInsertEnvelope(feature)

        when:
        def r = postJson(envelope, '<' + CRS_BOGUS_URI + '>')

        then: '4xx — server rejected the CRS before writing anything'
        r.statusCode() >= 400 && r.statusCode() < 500
        // Be lenient on exact status (could be 400 Bad Request or 415 / 422) but the body should
        // mention the CRS in some form so the rejection is attributable to the CRS check, not to
        // an unrelated parse error.
        r.body().toLowerCase().contains('crs') || r.body().contains('99999')

        and: 'the feature was not inserted — GET 404'
        def url = sutUrl + sutPath + '/collections/' + jsonCollectionId() + '/items/' +
                phase8RejectedId
        def getReq = HttpRequest.newBuilder(URI.create(url))
                .header('Accept', 'application/geo+json')
                .GET()
                .build()
        def getResp = httpClient.send(getReq, BodyHandlers.ofString())
        getResp.statusCode() == 404
    }

    def 'phase 9: GET surfaces primary-geometry as the top-level geometry member, not as a property [Req 27]'() {
        // Uses the feature inserted by phase 5. Read the schema so the assertion survives renames
        // of the underlying primary-geometry property: whatever its name, it must NOT appear
        // alongside the GeoJSON members.
        given:
        String schemaUrl =
                sutUrl + sutPath + '/collections/' + jsonCollectionId() + '/schema?f=json'
        def schemaResp = httpClient.send(
                HttpRequest.newBuilder(URI.create(schemaUrl))
                        .header('Accept', 'application/schema+json, application/json')
                        .GET()
                        .build(),
                BodyHandlers.ofString())
        assert schemaResp.statusCode() == 200
        def schema = new JsonSlurper().parseText(schemaResp.body())
        // Both schema.properties and schema['properties'] resolve via Groovy meta-class to the
        // Map class's bean introspection (yielding keys like JDK_MAP_ALTHASHING_SYSPROP, size,
        // keys, values), NOT to the JSON value at key "properties". The fully-qualified
        // Map.get(key) call sidesteps the meta-class entirely.
        Map schemaMap = (Map) schema
        Map props = (Map) schemaMap.get('properties')
        String primaryGeometryProperty = null
        for (Map.Entry e in props.entrySet()) {
            Object v = e.value
            if (v instanceof Map && ((Map) v)['x-ogc-role'] == 'primary-geometry') {
                primaryGeometryProperty = e.key as String
                break
            }
        }
        assert primaryGeometryProperty != null :
                "no x-ogc-role: primary-geometry found in schema. Keys: ${props.keySet()}"

        when:
        def doc = new JsonSlurper().parseText(
                fetchReceivableFeatureInCrs(crsTestIds[0], CRS_STORAGE_URI))

        then: 'GeoJSON shape — top-level geometry populated, not duplicated under properties'
        doc.type == 'Feature'
        doc.geometry != null
        doc.geometry.type instanceof String
        doc.geometry.coordinates != null
        !((Map) doc.properties).containsKey(primaryGeometryProperty)
        !((Map) doc.properties).containsKey('geometry')
    }

    def 'phase A1: collection metadata reflects accumulated inserts before delete-all'() {
        // 9 features net inserted by earlier phases: 5 (phase 1) + 1 (phase 3i) + 3 (phases 5-7).
        // Replaces and updates do not change item count. Phase 8 was rejected.
        when:
        Map meta = baselineItemCount != null
                ? waitForItemCount(baselineItemCount + 9L)
                : fetchCollectionMeta()

        then: 'itemCount tracks the inserts when the collection exposes it'
        if (baselineItemCount != null) {
            assert itemCount(meta) == baselineItemCount + 9L :
                    "expected ${baselineItemCount + 9L} but got ${itemCount(meta)} — " +
                            "featureProvider.changes().handle(...) did not run or the listener " +
                            "didn't bump itemCount"
        }

        and: 'lastModified moved when the collection exposes it'
        if (baselineLastModified != null) {
            assert meta.get('lastModified') != null
            assert ((String) meta.get('lastModified')) != baselineLastModified :
                    "lastModified did not advance — change emission may be wired off"
        }
    }

    def 'phase 4: delete every inserted test feature to restore the prior state'() {
        when:
        // Filter covers testIds[0..4] (phase 1) + partialOkId (phase 3i) + crsTestIds[0..2]
        // (phases 5–7) + phase8RejectedId. Phase 8 expects the insert to be rejected, so
        // phase8RejectedId is not present in the dataset; ldproxy's delete-by-filter tolerates the
        // absence and contributes 0 to totalDeleted. A regression that let phase 8 insert anyway
        // would bump the count to 10 and fail this assertion loudly.
        def r = postTransaction(buildDeleteAll())

        then:
        r.statusCode() == 200
        def doc = new JsonSlurper().parseText(r.body())
        doc.summary.totalDeleted == 9
        doc.deleteResults instanceof List
        doc.deleteResults.size() == 9
    }

    def 'phase A2: collection metadata returns to baseline after delete-all'() {
        when:
        Map meta = baselineItemCount != null
                ? waitForItemCount(baselineItemCount)
                : fetchCollectionMeta()

        then: 'itemCount is back to baseline when the collection exposes it'
        if (baselineItemCount != null) {
            assert itemCount(meta) == baselineItemCount :
                    "expected itemCount to return to ${baselineItemCount} but got " +
                            "${itemCount(meta)} — the DELETE branch of " +
                            "featureProvider.changes().handle(...) did not run"
        }
    }
}
