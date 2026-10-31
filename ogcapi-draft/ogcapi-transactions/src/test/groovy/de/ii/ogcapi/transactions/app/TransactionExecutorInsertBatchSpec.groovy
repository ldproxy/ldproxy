/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.transactions.domain.ActionResult
import de.ii.ogcapi.transactions.domain.ActionStatus
import de.ii.ogcapi.transactions.domain.CompositeId
import de.ii.ogcapi.transactions.domain.InsertItem
import de.ii.ogcapi.transactions.domain.MutationStrategy
import de.ii.ogcapi.transactions.domain.TxInsert
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureTokenSource
import de.ii.xtraplatform.features.domain.FeatureTransactions
import de.ii.xtraplatform.features.domain.ImmutableMutationResult
import de.ii.xtraplatform.geometries.domain.Axes
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * The versioned-insert pre-flight checks every feature id against the database. Inserts are still
 * written in batches: the batch is only written before the check when the id repeats an id of the
 * batch, since the check cannot see features that are not written yet.
 *
 * <p>Decoding is replaced through the package-private {@code axes} and {@code decodeFeature} seams, the session is a
 * recording fake that knows which ids are "in the database".
 */
class TransactionExecutorInsertBatchSpec extends Specification {

    def 'without repeated ids the features are written in batches of 100'() {
        given:
        RecordingSession session = new RecordingSession()
        List<String> ids = (1..250).collect { "f$it".toString() }

        when:
        ActionResult result = run(session, preflight(false), ids)

        then:
        result.status == ActionStatus.SUCCESS
        session.batches*.size() == [100, 100, 50]
        session.checked == ids
        result.featureIds == ids
    }

    def 'a repeated id writes the batch first, so the check finds the conflict'() {
        given:
        RecordingSession session = new RecordingSession()

        when:
        ActionResult result = run(session, preflight(false), ['a', 'b', 'a', 'c'])

        then:
        result.status == ActionStatus.FAILED
        result.error.get().contains("'a'")
        session.batches == [['a', 'b']]
        session.checked == ['a', 'b', 'a']
    }

    def 'with ignored existing features a repeated id is skipped and the batch continues'() {
        given:
        RecordingSession session = new RecordingSession()

        when:
        ActionResult result = run(session, preflight(true), ['a', 'b', 'a', 'c'])

        then:
        result.status == ActionStatus.SUCCESS
        session.batches == [['a', 'b'], ['c']]
        result.featureIds == ['a', 'b', 'c']
    }

    def 'an id that exists in the database is a conflict without writing the batch first'() {
        given:
        RecordingSession session = new RecordingSession(existing: ['x'] as Set)

        when:
        ActionResult result = run(session, preflight(false), ['a', 'b', 'x'])

        then:
        result.status == ActionStatus.FAILED
        result.error.get().contains("'x'")
        session.batches.isEmpty()
        session.checked == ['a', 'b', 'x']
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private MutationStrategy preflight(boolean ignoreExisting) {
        return Stub(MutationStrategy) {
            requiresInsertPreflight() >> true
            ignoresExistingFeatureOnInsert(_, _) >> ignoreExisting
            splitCompositeId(_, _, _) >> { args -> new CompositeId(args[2] as String, Optional.empty()) }
            insertRoleOverrides(_, _, _, _) >> [:]
            resolveMutationTimestamp(_, _, _, _) >> Instant.parse('2026-01-01T00:00:00Z')
            disallowsSameFeatureChain(_, _) >> false
        }
    }

    private ActionResult run(RecordingSession session, MutationStrategy strategy, List<String> ids) {
        Map<FeatureTokenSource, String> sources = [:]
        session.sources = sources
        TransactionExecutorImpl executor = new TransactionExecutorImpl(
                null, null, null, null, Stub(FeaturesCoreQueriesHandler), Stub(VolatileRegistry)) {
            @Override
            Axes axes(EpsgCrs crs) {
                return Axes.XY
            }

            @Override
            FeatureTokenSource decodeFeature(
                    MediaType contentType,
                    InputStream body,
                    OgcApiDataV2 apiData,
                    String collectionId,
                    EpsgCrs crs,
                    Axes axes,
                    boolean rejectEmptyValues) {
                // the session never reads the source, it only has to tell the features apart
                FeatureTokenSource source = new FeatureTokenSource(null)
                sources[source] = new String(body.readAllBytes(), StandardCharsets.UTF_8)
                return source
            }
        }
        // a real apiData without collections: getCollections() is a BuildableMap, which a stub
        // cannot produce; the collection id then stays the raw id of the action
        OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder()
                .id('api')
                .serviceType('OGC_API')
                .build()
        OgcApi api = Stub(OgcApi) {
            getData() >> apiData
        }
        return executor.runAction(
                insert(ids), session, api, null, OgcCrs.CRS84, [:], [c: strategy],
                Instant.parse('2026-01-01T00:00:00Z'), Optional.empty(), false, false, true,
                Optional.empty())
    }

    private static TxInsert insert(List<String> ids) {
        List<InsertItem> items = ids.collect { String id ->
            byte[] payload = id.getBytes(StandardCharsets.UTF_8)
            new InsertItem(Optional.of(id), payload, new ByteArrayInputStream(payload))
        }
        return new TxInsert() {
            @Override
            String getCollectionId() { 'c' }

            @Override
            Optional<String> getActionId() { Optional.empty() }

            @Override
            Optional<String> getTitle() { Optional.empty() }

            @Override
            Optional<String> getDescription() { Optional.empty() }

            @Override
            MediaType getMediaType() { MediaType.valueOf('application/gml+xml') }

            @Override
            Iterator<InsertItem> items() { items.iterator() }
        }
    }

    private static class RecordingSession implements FeatureTransactions.Session {
        Set<String> existing = [] as Set
        Map<FeatureTokenSource, String> sources = [:]
        List<List<String>> batches = []
        List<String> checked = []

        @Override
        FeatureTransactions.MutationResult assertNoConflictingVersion(String featureType, String featureId) {
            checked << featureId
            def builder = ImmutableMutationResult.builder()
                    .type(FeatureTransactions.MutationResult.Type.CREATE)
                    .hasFeatures(false)
            if (existing.contains(featureId)) {
                builder.error(new IllegalArgumentException("Cannot create feature id '" + featureId + "'"))
            }
            return builder.build()
        }

        @Override
        FeatureTransactions.MutationResult createFeatures(
                String featureType, Iterable<FeatureTokenSource> featureTokenSources, EpsgCrs crs) {
            List<String> ids = featureTokenSources.collect { sources[it] }
            batches << ids
            existing.addAll(ids)
            return ImmutableMutationResult.builder()
                    .type(FeatureTransactions.MutationResult.Type.CREATE)
                    .hasFeatures(false)
                    .ids(ids)
                    .build()
        }

        @Override
        FeatureTransactions.MutationResult createFeatures(
                String type, FeatureTokenSource source, EpsgCrs crs, Optional<String> featureId) {
            throw new UnsupportedOperationException()
        }

        @Override
        FeatureTransactions.MutationResult updateFeature(
                String type, String id, FeatureTokenSource source, EpsgCrs crs, boolean partial) {
            throw new UnsupportedOperationException()
        }

        @Override
        FeatureTransactions.MutationResult deleteFeature(String featureType, String id) {
            throw new UnsupportedOperationException()
        }

        @Override
        List<String> drainWarnings() { [] }

        @Override
        List<String> execute(List<String> statements) { [] }

        @Override
        boolean supportsSavepoints() { true }

        @Override
        void savepoint() {}

        @Override
        void releaseSavepoint() {}

        @Override
        void rollbackToSavepoint() {}

        @Override
        void commit() {}

        @Override
        void rollback() {}

        @Override
        void close() {}
    }
}
