/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.transactions.domain.ActionResult
import de.ii.ogcapi.transactions.domain.ActionStatus
import de.ii.ogcapi.transactions.domain.ImmutableActionResult
import de.ii.ogcapi.transactions.domain.TxActionType
import de.ii.xtraplatform.crs.domain.BoundingBox
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureChange
import de.ii.xtraplatform.features.domain.FeatureChanges
import org.threeten.extra.Interval
import spock.lang.Specification

import java.time.Instant

/**
 * Unit-level guard for the executor's post-commit {@code featureProvider.changes().handle(...)}
 * dispatch. The integration round-trip in {@link de.ii.ogcapi.transactions.TransactionalRESTApiSpec}
 * covers the wire path; this spec covers the aggregation logic in isolation so we get fast feedback
 * if the per-(collection, action) grouping or the failure / commit-fail gating regresses.
 */
class TransactionExecutorChangesSpec extends Specification {

    def 'aggregates successful actions per (collection, action) into one FeatureChange each'() {
        given:
        FeatureChanges changes = Mock()
        TransactionExecutorImpl executor = newExecutor('cA': changes)
        def api = newApi()

        BoundingBox bbox1 = BoundingBox.of(0d, 0d, 1d, 1d, OgcCrs.CRS84)
        BoundingBox bbox2 = BoundingBox.of(2d, 2d, 3d, 3d, OgcCrs.CRS84)
        BoundingBox bboxUpd = BoundingBox.of(0.5d, 0.5d, 1.5d, 1.5d, OgcCrs.CRS84)

        List<ActionResult> results = [
                successInsert('cA', ['f1'], bbox1, null),
                successInsert('cA', ['f2', 'f3'], bbox2, null),
                successUpdate('cA', ['f4'], bboxUpd, null),
                successDelete('cA', ['f5']),
        ]

        when:
        executor.emitChanges(api, results)

        then: 'one CREATE event combines both inserts, ids preserve order, bbox is the union'
        1 * changes.handle({ FeatureChange ch ->
            ch.action == FeatureChange.Action.CREATE &&
                    ch.featureType == 'cA' &&
                    ch.featureIds == ['f1', 'f2', 'f3'] &&
                    ch.newBoundingBox.isPresent() &&
                    ch.newBoundingBox.get().xmin == 0d &&
                    ch.newBoundingBox.get().xmax == 3d
        })

        then: 'one UPDATE event for the update'
        1 * changes.handle({ FeatureChange ch ->
            ch.action == FeatureChange.Action.UPDATE &&
                    ch.featureType == 'cA' &&
                    ch.featureIds == ['f4']
        })

        then: 'one DELETE event for the delete'
        1 * changes.handle({ FeatureChange ch ->
            ch.action == FeatureChange.Action.DELETE &&
                    ch.featureType == 'cA' &&
                    ch.featureIds == ['f5']
        })

        then: 'no other events'
        0 * changes.handle(_)
    }

    def 'aggregates per collection — multi-collection inserts produce one CREATE each'() {
        given:
        FeatureChanges changesA = Mock()
        FeatureChanges changesB = Mock()
        TransactionExecutorImpl executor = newExecutor('cA': changesA, 'cB': changesB)
        def api = newApi()

        BoundingBox bboxA = BoundingBox.of(0d, 0d, 1d, 1d, OgcCrs.CRS84)
        BoundingBox bboxB = BoundingBox.of(10d, 10d, 11d, 11d, OgcCrs.CRS84)

        List<ActionResult> results = [
                successInsert('cA', ['a1'], bboxA, null),
                successInsert('cB', ['b1'], bboxB, null),
        ]

        when:
        executor.emitChanges(api, results)

        then:
        1 * changesA.handle({ FeatureChange ch ->
            ch.action == FeatureChange.Action.CREATE && ch.featureType == 'cA' && ch.featureIds == ['a1']
        })
        1 * changesB.handle({ FeatureChange ch ->
            ch.action == FeatureChange.Action.CREATE && ch.featureType == 'cB' && ch.featureIds == ['b1']
        })
        0 * changesA.handle(_)
        0 * changesB.handle(_)
    }

    def 'skips FAILED and SKIPPED results, and skips SUCCESS actions with no feature ids'() {
        given:
        FeatureChanges changes = Mock()
        TransactionExecutorImpl executor = newExecutor('cA': changes)
        def api = newApi()

        BoundingBox bbox = BoundingBox.of(0d, 0d, 1d, 1d, OgcCrs.CRS84)

        List<ActionResult> results = [
                successInsert('cA', ['f1'], bbox, null),
                new ImmutableActionResult.Builder()
                        .type(TxActionType.INSERT)
                        .collectionId('cA')
                        .status(ActionStatus.FAILED)
                        .error('boom')
                        .build(),
                new ImmutableActionResult.Builder()
                        .type(TxActionType.DELETE)
                        .collectionId('cA')
                        .status(ActionStatus.SKIPPED)
                        .build(),
                successDelete('cA', []),  // zero rids matched the filter — no event
        ]

        when:
        executor.emitChanges(api, results)

        then:
        1 * changes.handle({ FeatureChange ch -> ch.action == FeatureChange.Action.CREATE })
        0 * changes.handle(_)
    }

    def 'merges temporal intervals across actions within a (collection, action) group'() {
        given:
        FeatureChanges changes = Mock()
        TransactionExecutorImpl executor = newExecutor('cA': changes)
        def api = newApi()

        BoundingBox bbox = BoundingBox.of(0d, 0d, 1d, 1d, OgcCrs.CRS84)
        Interval iv1 = Interval.of(Instant.parse('2024-01-01T00:00:00Z'), Instant.parse('2024-02-01T00:00:00Z'))
        Interval iv2 = Interval.of(Instant.parse('2024-03-01T00:00:00Z'), Instant.parse('2024-04-01T00:00:00Z'))

        when:
        executor.emitChanges(api, [
                successInsert('cA', ['f1'], bbox, iv1),
                successInsert('cA', ['f2'], bbox, iv2),
        ])

        then:
        1 * changes.handle({ FeatureChange ch ->
            ch.newInterval.isPresent() &&
                    ch.newInterval.get().start == Instant.parse('2024-01-01T00:00:00Z') &&
                    ch.newInterval.get().end == Instant.parse('2024-04-01T00:00:00Z')
        })
    }

    def 'a failure in one providers change handler does not abort emission for other groups'() {
        given:
        FeatureChanges changesA = Mock()
        FeatureChanges changesB = Mock()
        TransactionExecutorImpl executor = newExecutor('cA': changesA, 'cB': changesB)
        def api = newApi()

        BoundingBox bbox = BoundingBox.of(0d, 0d, 1d, 1d, OgcCrs.CRS84)

        when:
        executor.emitChanges(api, [
                successInsert('cA', ['a1'], bbox, null),
                successInsert('cB', ['b1'], bbox, null),
        ])

        then: "the runtime exception from cA is swallowed and cB is still notified"
        1 * changesA.handle(_) >> { throw new RuntimeException('listener boom') }
        1 * changesB.handle({ FeatureChange ch -> ch.featureType == 'cB' })
        notThrown(RuntimeException)
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private static ActionResult successInsert(
            String collection, List<String> ids, BoundingBox bbox, Interval interval) {
        new ImmutableActionResult.Builder()
                .type(TxActionType.INSERT)
                .collectionId(collection)
                .status(ActionStatus.SUCCESS)
                .featureIds(ids)
                .newBoundingBox(Optional.ofNullable(bbox))
                .newInterval(Optional.ofNullable(interval))
                .build()
    }

    private static ActionResult successUpdate(
            String collection, List<String> ids, BoundingBox bbox, Interval interval) {
        new ImmutableActionResult.Builder()
                .type(TxActionType.UPDATE)
                .collectionId(collection)
                .status(ActionStatus.SUCCESS)
                .featureIds(ids)
                .newBoundingBox(Optional.ofNullable(bbox))
                .newInterval(Optional.ofNullable(interval))
                .build()
    }

    private static ActionResult successDelete(String collection, List<String> ids) {
        new ImmutableActionResult.Builder()
                .type(TxActionType.DELETE)
                .collectionId(collection)
                .status(ActionStatus.SUCCESS)
                .featureIds(ids)
                .build()
    }

    /**
     * Subclass-with-override avoids mocking {@code FeaturesCoreProviders} / {@code FeatureProvider}
     * — their capability- and cache-typed methods drag classes that aren't on the test classpath
     * onto the proxy-generation path. We only need a way to route a collection id to a
     * {@link FeatureChanges} sink.
     */
    private TransactionExecutorImpl newExecutor(Map<String, FeatureChanges> changesByCollection) {
        return new TransactionExecutorImpl(null, null, null, null) {
            @Override
            FeatureChanges resolveChanges(de.ii.ogcapi.foundation.domain.OgcApi api, String collectionId) {
                FeatureChanges c = changesByCollection.get(collectionId)
                if (c == null) {
                    throw new IllegalArgumentException(
                            "No feature provider available for collection '" + collectionId + "'")
                }
                return c
            }
        }
    }

    private Object newApi() {
        // resolveChanges is overridden so the api arg is never touched; returning null avoids
        // loading OgcApi at runtime (its bytecode references xtraplatform-cache, which the
        // transactions test classpath doesn't carry).
        return null
    }
}
