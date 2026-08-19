/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.HeaderPrefer
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.transactions.domain.ActionResult
import de.ii.ogcapi.transactions.domain.ActionStatus
import de.ii.ogcapi.transactions.domain.ExecutionResult
import de.ii.ogcapi.transactions.domain.ImmutableActionResult
import de.ii.ogcapi.transactions.domain.ImmutableTransactionsConfiguration
import de.ii.ogcapi.transactions.domain.ImmutableTxDelete
import de.ii.ogcapi.transactions.domain.MutationStrategy
import de.ii.ogcapi.transactions.domain.Transaction
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration
import de.ii.ogcapi.transactions.domain.TxAction
import de.ii.ogcapi.transactions.domain.TxActionType
import de.ii.ogcapi.transactions.domain.TxSemantic
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureTokenSource
import de.ii.xtraplatform.features.domain.FeatureTransactions
import de.ii.xtraplatform.features.domain.JobHook
import spock.lang.Specification

import java.time.Instant

/**
 * Unit-level guard for cooperative cancellation in the execution loops: atomic transactions are
 * rolled back as a whole, batch transactions keep committed actions and stop, and a CANCELLED
 * action result (a checkpoint that fired mid-action) stops execution the same way.
 *
 * <p>Per-action outcomes are scripted through the package-private {@code runAction} seam and
 * sessions are recording fakes, following the pattern documented in
 * {@link TransactionExecutorAtomicSpec}.
 */
class TransactionExecutorCancellationSpec extends Specification {

    def 'atomic: a cancellation between actions rolls back the whole transaction'() {
        given:
        RecordingSession session = new RecordingSession()
        FlagJobHook jobHook = new FlagJobHook()
        TransactionExecutorImpl executor = newExecutor(config(), session, [
                a1: { jobHook.cancelRequested = true; success('a1', ['f1']) },
                a2: { success('a2', ['f2']) },
                a3: { success('a3', ['f3']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction(TxSemantic.ATOMIC, 'a1', 'a2', 'a3'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty(), Optional.of(jobHook))

        then: 'the first action ran but is rolled back, the remaining actions are skipped'
        statuses(result) == [ActionStatus.ROLLED_BACK, ActionStatus.SKIPPED, ActionStatus.SKIPPED]
        result.isCancelled()
        !result.isSuccess()

        and: 'the session is rolled back, never committed'
        session.calls.contains('rollback')
        !session.calls.contains('commit')
    }

    def 'atomic: a CANCELLED action result rolls back the whole transaction'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(), session, [
                a1: { success('a1', ['f1']) },
                a2: { cancelledResult('a2') },
                a3: { success('a3', ['f3']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction(TxSemantic.ATOMIC, 'a1', 'a2', 'a3'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty(), Optional.empty())

        then:
        statuses(result) == [ActionStatus.ROLLED_BACK, ActionStatus.CANCELLED, ActionStatus.SKIPPED]
        result.isCancelled()
        !result.isSuccess()
        session.calls.contains('rollback')
        !session.calls.contains('commit')
    }

    def 'batch: a cancellation between actions keeps committed actions and stops'() {
        given:
        RecordingSession session = new RecordingSession()
        FlagJobHook jobHook = new FlagJobHook()
        TransactionExecutorImpl executor = newExecutor(config(), session, [
                a1: { jobHook.cancelRequested = true; success('a1', ['f1']) },
                a2: { success('a2', ['f2']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction(TxSemantic.BATCH, 'a1', 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty(), Optional.of(jobHook))

        then: 'the first action stays committed, the second was never executed'
        statuses(result) == [ActionStatus.SUCCESS]
        result.isCancelled()
        !result.isSuccess()
        session.calls.count { it == 'commit' } == 1
        !session.calls.contains('rollback')
    }

    def 'batch: a CANCELLED action result rolls back that action and stops'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(), session, [
                a1: { success('a1', ['f1']) },
                a2: { cancelledResult('a2') },
                a3: { success('a3', ['f3']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction(TxSemantic.BATCH, 'a1', 'a2', 'a3'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty(), Optional.empty())

        then: 'the first action stays committed, the cancelled action is rolled back, the third never runs'
        statuses(result) == [ActionStatus.SUCCESS, ActionStatus.CANCELLED]
        result.isCancelled()
        session.calls.count { it == 'commit' } == 1
        session.calls.count { it == 'rollback' } == 1
    }

    def 'an absent hook leaves an all-success transaction unchanged'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(), session, [
                a1: { success('a1', ['f1']) },
                a2: { success('a2', ['f2']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction(TxSemantic.ATOMIC, 'a1', 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        statuses(result) == [ActionStatus.SUCCESS, ActionStatus.SUCCESS]
        !result.isCancelled()
        result.isSuccess()
        session.calls.contains('commit')
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private static List<ActionStatus> statuses(ExecutionResult result) {
        return result.actionResults.collect { it.status }
    }

    private static TransactionsConfiguration config() {
        return new ImmutableTransactionsConfiguration.Builder()
                .atomic(true)
                .batch(true)
                .build()
    }

    private static ActionResult success(String actionId, List<String> ids) {
        return new ImmutableActionResult.Builder()
                .type(TxActionType.DELETE)
                .collectionId('c')
                .actionId(actionId)
                .status(ActionStatus.SUCCESS)
                .featureIds(ids)
                .build()
    }

    private static ActionResult cancelledResult(String actionId) {
        return new ImmutableActionResult.Builder()
                .type(TxActionType.DELETE)
                .collectionId('c')
                .actionId(actionId)
                .status(ActionStatus.CANCELLED)
                .build()
    }

    private static Transaction transaction(TxSemantic semantic, String... actionIds) {
        List<TxAction> actions = actionIds.collect {
            new ImmutableTxDelete.Builder().collectionId('c').actionId(it).addTargetIds('f').build() as TxAction
        }
        return new FixedTransaction(semantic, actions)
    }

    private TransactionExecutorImpl newExecutor(
            TransactionsConfiguration cfg,
            RecordingSession session,
            Map<String, Closure<ActionResult>> outcomes) {
        return new TransactionExecutorImpl(
                null, null, null, Stub(FeaturesCoreQueriesHandler), Stub(VolatileRegistry)) {
            @Override
            TransactionsConfiguration transactionsConfig(OgcApi api) {
                return cfg
            }

            @Override
            String resolveProviderId(OgcApi api, String collectionId) {
                return 'p1'
            }

            @Override
            String canonicalCollectionId(OgcApi api, String collectionId) {
                return collectionId
            }

            @Override
            FeatureTransactions.Session openSessionFor(OgcApi api, String collectionId) {
                return session
            }

            @Override
            ActionResult runAction(
                    TxAction action,
                    FeatureTransactions.Session s,
                    OgcApi api,
                    ApiRequestContext ctx,
                    EpsgCrs requestCrs,
                    Map<String, Set<String>> touchedIdsByCollection,
                    Map<String, MutationStrategy> strategyByCollection,
                    Instant scopeTimestamp,
                    Optional<Instant> ogcMutationDatetime,
                    boolean validate,
                    boolean skipInvalid,
                    boolean fromWfs,
                    Optional<JobHook> jobHook) {
                return outcomes[action.actionId.get()].call()
            }
        }
    }

    private static class FlagJobHook implements JobHook {
        boolean cancelRequested = false

        @Override
        boolean isCancelRequested() {
            return cancelRequested
        }
    }

    private static class FixedTransaction implements Transaction {
        private final TxSemantic semantic
        private final List<TxAction> actions

        FixedTransaction(TxSemantic semantic, List<TxAction> actions) {
            this.semantic = semantic
            this.actions = actions
        }

        @Override
        TxSemantic getSemantic() {
            return semantic
        }

        @Override
        Iterator<TxAction> actions() {
            return actions.iterator()
        }

        @Override
        void close() {
        }
    }

    private static class RecordingSession implements FeatureTransactions.Session {
        List<String> calls = []
        List<String> nextWarnings = []

        @Override
        List<String> drainWarnings() {
            List<String> drained = nextWarnings
            nextWarnings = []
            return drained
        }

        @Override
        FeatureTransactions.MutationResult createFeatures(
                String featureType, FeatureTokenSource source, EpsgCrs crs, Optional<String> featureId) {
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
        List<String> execute(List<String> statements) {
            calls << 'execute'
            return []
        }

        @Override
        boolean supportsSavepoints() {
            return true
        }

        @Override
        void savepoint() {
            calls << 'savepoint'
        }

        @Override
        void releaseSavepoint() {
            calls << 'release'
        }

        @Override
        void rollbackToSavepoint() {
            calls << 'rollbackTo'
        }

        @Override
        void commit() {
            calls << 'commit'
        }

        @Override
        void rollback() {
            calls << 'rollback'
        }

        @Override
        void close() {
            calls << 'close'
        }
    }
}
