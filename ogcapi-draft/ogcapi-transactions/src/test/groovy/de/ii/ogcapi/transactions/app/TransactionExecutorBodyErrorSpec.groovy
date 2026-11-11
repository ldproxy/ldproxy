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
 * The transaction is released when the body cannot be read to the end, e.g. a truncated or
 * malformed request, and as soon as the execution of an atomic transaction stops, not only after
 * the rest of the body has been read.
 *
 * <p>The body is a transaction whose actions are read one by one and that fails at a given action
 * as the parsers do for a malformed body. The reads are logged together with the calls of the
 * session, so their order can be checked.
 */
class TransactionExecutorBodyErrorSpec extends Specification {

    List<String> log = []

    def 'atomic: a body that cannot be read to the end rolls back and closes the transaction'() {
        given:
        TransactionExecutorImpl executor = newExecutor(config(false), [
                a1: { success('a1', ['f1']) },
        ])

        when:
        executor.execute(
                transaction(TxSemantic.ATOMIC, ['a1', 'a2'], 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then: 'the error of the body is passed on'
        IllegalArgumentException e = thrown()
        e.message == 'Malformed wfs:Transaction body: unexpected end of input'

        and: 'the writes of the first action are rolled back and the session is closed'
        log == ['read a1', 'execute', 'rollback', 'close']
    }

    def 'atomic: a failed action rolls back the transaction before the rest of the body is read'() {
        given:
        TransactionExecutorImpl executor = newExecutor(config(false), [
                a1: { success('a1', ['f1']) },
                a2: { failure('a2', 'boom') },
        ])

        when:
        ExecutionResult result = executor.execute(
                transaction(TxSemantic.ATOMIC, ['a1', 'a2', 'a3', 'a4']), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then: 'the remaining actions are reported as skipped'
        result.actionResults.collect { it.status } ==
                [ActionStatus.ROLLED_BACK, ActionStatus.FAILED, ActionStatus.SKIPPED, ActionStatus.SKIPPED]

        and: 'they are read only after the transaction is rolled back and closed'
        log == ['read a1', 'execute', 'read a2', 'rollback', 'close', 'read a3', 'read a4']
    }

    def 'atomic: a body that cannot be read after a failed action does not roll back twice'() {
        given:
        TransactionExecutorImpl executor = newExecutor(config(false), [
                a1: { failure('a1', 'boom') },
        ])

        when:
        executor.execute(
                transaction(TxSemantic.ATOMIC, ['a1', 'a2'], 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        thrown(IllegalArgumentException)
        log == ['read a1', 'execute', 'rollback', 'close']
    }

    def 'batch: the committed actions are reported as changes also when the body cannot be read to the end'() {
        given:
        List<List<ActionStatus>> emitted = []
        TransactionExecutorImpl executor = newExecutor(config(false), [
                a1: { success('a1', ['f1']) },
        ], emitted)

        when:
        executor.execute(
                transaction(TxSemantic.BATCH, ['a1', 'a2'], 'a2'), null, null, OgcCrs.CRS84,
                HeaderPrefer.Handling.LENIENT, Optional.empty())

        then:
        thrown(IllegalArgumentException)
        log == ['read a1', 'execute', 'execute', 'commit', 'close']
        emitted == [[ActionStatus.SUCCESS]]
    }

    // ---------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------

    private static TransactionsConfiguration config(boolean collectErrors) {
        return new ImmutableTransactionsConfiguration.Builder()
                .atomic(true)
                .batch(true)
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

    /** The actions, read one by one; reading the action {@code malformed} fails. */
    private Transaction transaction(TxSemantic semantic, List<String> actionIds, String malformed = null) {
        List<TxAction> actions = actionIds.collect {
            new ImmutableTxDelete.Builder().collectionId('c').actionId(it).addTargetIds('f').build() as TxAction
        }
        return new ReadingTransaction(semantic, actions, malformed, log)
    }

    private TransactionExecutorImpl newExecutor(
            TransactionsConfiguration cfg,
            Map<String, Closure<ActionResult>> outcomes,
            List<List<ActionStatus>> emitted = []) {
        RecordingSession session = new RecordingSession(log)
        return new TransactionExecutorImpl(
                null, null, null, null, Stub(FeaturesCoreQueriesHandler), Stub(VolatileRegistry)) {
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
            void emitChanges(OgcApi api, List<ActionResult> results) {
                emitted << results.collect { it.status }
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

    private static class ReadingTransaction implements Transaction {
        private final TxSemantic semantic
        private final List<TxAction> actions
        private final String malformed
        private final List<String> log

        ReadingTransaction(TxSemantic semantic, List<TxAction> actions, String malformed, List<String> log) {
            this.semantic = semantic
            this.actions = actions
            this.malformed = malformed
            this.log = log
        }

        @Override
        TxSemantic getSemantic() {
            return semantic
        }

        @Override
        Iterator<TxAction> actions() {
            Iterator<TxAction> iterator = actions.iterator()
            String malformed = this.malformed
            List<String> log = this.log
            return new Iterator<TxAction>() {
                TxAction pending

                @Override
                boolean hasNext() {
                    if (pending == null && iterator.hasNext()) {
                        TxAction next = iterator.next()
                        if (next.actionId.get() == malformed) {
                            // as the parsers report a malformed body
                            throw new IllegalArgumentException(
                                    'Malformed wfs:Transaction body: unexpected end of input')
                        }
                        pending = next
                    }
                    return pending != null
                }

                @Override
                TxAction next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException()
                    }
                    TxAction action = pending
                    pending = null
                    log << "read ${action.actionId.get()}".toString()
                    return action
                }
            }
        }

        @Override
        void close() {
        }
    }

    private static class RecordingSession implements FeatureTransactions.Session {
        private final List<String> log

        RecordingSession(List<String> log) {
            this.log = log
        }

        @Override
        List<String> drainWarnings() {
            return []
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
            log << 'execute'
            return []
        }

        @Override
        boolean supportsSavepoints() {
            return true
        }

        @Override
        void savepoint() {
            log << 'savepoint'
        }

        @Override
        void releaseSavepoint() {
            log << 'release'
        }

        @Override
        void rollbackToSavepoint() {
            log << 'rollbackTo'
        }

        @Override
        void commit() {
            log << 'commit'
        }

        @Override
        void rollback() {
            log << 'rollback'
        }

        @Override
        void close() {
            log << 'close'
        }
    }
}
