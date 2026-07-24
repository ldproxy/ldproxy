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
import spock.lang.Specification

import java.time.Instant

/**
 * Unit-level guard for the atomic execution loop: stop-at-first-error vs. collectErrors
 * semantics, the per-action savepoint lifecycle, the SUCCESS-to-ROLLED_BACK flip on failed
 * transactions, and the fatal-error path that skips the remaining actions.
 *
 * <p>Per-action outcomes are scripted through the package-private {@code runAction} seam and
 * sessions are recording fakes, following the pattern documented in
 * {@link TransactionExecutorChangesSpec} (the full OgcApi / FeatureProvider graph cannot be
 * stubbed on this test classpath).
 */
class TransactionExecutorAtomicSpec extends Specification {

    def 'stop-at-first-error: remaining actions are skipped, successes are rolled back, one error is reported'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(false), session, [
                a1: { success('a1', ['f1']) },
                a2: { failure('a2', 'boom') },
                a3: { success('a3', ['f3']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1', 'a2', 'a3'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        statuses(result) == [ActionStatus.ROLLED_BACK, ActionStatus.FAILED, ActionStatus.SKIPPED]
        !result.isSuccess()
        result.transactionError.isEmpty()

        and: 'no savepoints are used and the session is rolled back, not committed'
        session.calls.findAll { it.startsWith('savepoint') }.isEmpty()
        session.calls.contains('rollback')
        !session.calls.contains('commit')
    }

    def 'collectErrors: every action runs, all errors are reported, everything is still rolled back'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(true), session, [
                a1: { success('a1', ['f1']) },
                a2: { failure('a2', 'first error') },
                a3: { failure('a3', 'second error') },
                a4: { success('a4', ['f4']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1', 'a2', 'a3', 'a4'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then: 'both errors surface, the successes flip to ROLLED_BACK'
        statuses(result) == [ActionStatus.ROLLED_BACK, ActionStatus.FAILED, ActionStatus.FAILED, ActionStatus.ROLLED_BACK]
        result.actionResults[1].error.get() == 'first error'
        result.actionResults[2].error.get() == 'second error'
        !result.isSuccess()

        and: 'each action ran inside a savepoint: released on success, rolled back on failure'
        session.calls == ['execute',
                          'savepoint', 'release',
                          'savepoint', 'rollbackTo',
                          'savepoint', 'rollbackTo',
                          'savepoint', 'release',
                          'rollback', 'close']
    }

    def 'collectErrors with an all-success transaction commits and keeps SUCCESS statuses'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(true), session, [
                a1: { success('a1', ['f1']) },
                a2: { success('a2', ['f2']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1', 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        statuses(result) == [ActionStatus.SUCCESS, ActionStatus.SUCCESS]
        result.isSuccess()
        session.calls == ['execute',
                          'savepoint', 'release',
                          'savepoint', 'release',
                          'execute', 'commit', 'close']
    }

    def 'a failed rollback-to-savepoint is fatal: the remaining actions are skipped'() {
        given:
        RecordingSession session = new RecordingSession(
                throwOnRollbackToSavepoint: new IllegalStateException('connection lost'))
        TransactionExecutorImpl executor = newExecutor(config(true), session, [
                a1: { failure('a1', 'boom') },
                a2: { success('a2', ['f2']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1', 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then: 'the failing action keeps its own error, the rest is skipped'
        statuses(result) == [ActionStatus.FAILED, ActionStatus.SKIPPED]
        result.actionResults[0].error.get() == 'boom'
        !result.isSuccess()
        !session.calls.contains('commit')
    }

    def 'collectErrors falls back to stop-at-first-error when the session does not support savepoints'() {
        given:
        RecordingSession session = new RecordingSession(savepoints: false)
        TransactionExecutorImpl executor = newExecutor(config(true), session, [
                a1: { failure('a1', 'boom') },
                a2: { success('a2', ['f2']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1', 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        statuses(result) == [ActionStatus.FAILED, ActionStatus.SKIPPED]
        session.calls.findAll { it.startsWith('savepoint') }.isEmpty()
    }

    def 'per-action SQL warnings are drained from the session and attached to the action result'() {
        given:
        RecordingSession session = new RecordingSession()
        TransactionExecutorImpl executor = newExecutor(config(false), session, [
                a1: { session.nextWarnings = ['trigger touched a protected row']; success('a1', ['f1']) },
                a2: { success('a2', ['f2']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1', 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        result.actionResults[0].warnings == ['trigger touched a protected row']
        result.actionResults[1].warnings.isEmpty()
    }

    def 'a failed commit rolls back all successes and reports a transaction-level error'() {
        given:
        RecordingSession session = new RecordingSession(throwOnCommit: new IllegalStateException('deferred constraint'))
        TransactionExecutorImpl executor = newExecutor(config(false), session, [
                a1: { success('a1', ['f1']) },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction('a1'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        statuses(result) == [ActionStatus.ROLLED_BACK]
        !result.isSuccess()
        result.transactionError.get().contains('deferred constraint')
        session.calls.contains('rollback')
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private static List<ActionStatus> statuses(ExecutionResult result) {
        return result.actionResults.collect { it.status }
    }

    private static TransactionsConfiguration config(boolean collectErrors) {
        return new ImmutableTransactionsConfiguration.Builder()
                .atomic(true)
                .collectErrors(collectErrors)
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

    private static ActionResult failure(String actionId, String error) {
        return new ImmutableActionResult.Builder()
                .type(TxActionType.DELETE)
                .collectionId('c')
                .actionId(actionId)
                .status(ActionStatus.FAILED)
                .error(error)
                .build()
    }

    private static Transaction transaction(String... actionIds) {
        List<TxAction> actions = actionIds.collect {
            new ImmutableTxDelete.Builder().collectionId('c').actionId(it).addTargetIds('f').build() as TxAction
        }
        return new FixedTransaction(actions)
    }

    /**
     * Scripted executor: per-action outcomes come from {@code outcomes} (keyed by actionId), the
     * session and provider resolution are fixed. Follows the subclass-with-override pattern from
     * {@link TransactionExecutorChangesSpec}.
     */
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
                    boolean fromWfs) {
                return outcomes[action.actionId.get()].call()
            }
        }
    }

    private static class FixedTransaction implements Transaction {
        private final List<TxAction> actions

        FixedTransaction(List<TxAction> actions) {
            this.actions = actions
        }

        @Override
        TxSemantic getSemantic() {
            return TxSemantic.ATOMIC
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
        boolean savepoints = true
        RuntimeException throwOnRollbackToSavepoint
        RuntimeException throwOnCommit

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
            return savepoints
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
            if (throwOnRollbackToSavepoint != null) {
                throw throwOnRollbackToSavepoint
            }
        }

        @Override
        void commit() {
            calls << 'commit'
            if (throwOnCommit != null) {
                throw throwOnCommit
            }
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
