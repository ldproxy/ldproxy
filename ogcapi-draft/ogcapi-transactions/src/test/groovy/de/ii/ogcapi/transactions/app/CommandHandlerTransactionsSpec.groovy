/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.transactions.domain.ExecutionResult
import de.ii.ogcapi.transactions.domain.ImmutableExecutionResult
import de.ii.ogcapi.transactions.domain.ImmutableTransactionsConfiguration
import de.ii.ogcapi.transactions.domain.ImmutableTxDelete
import de.ii.ogcapi.transactions.domain.Transaction
import de.ii.ogcapi.transactions.domain.TransactionExecutor
import de.ii.ogcapi.transactions.domain.TransactionParser
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration
import de.ii.ogcapi.transactions.domain.TxAction
import de.ii.ogcapi.transactions.domain.TxSemantic
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.crs.domain.OgcCrs
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class CommandHandlerTransactionsSpec extends Specification {

    def 'strict handling parses once and enables executor validation'() {
        given:
        String body = '{"transaction":[]}'
        RecordingParser parser = new RecordingParser(actions: [])
        CountingExecutor executor = new CountingExecutor()
        CommandHandlerTransactionsImpl handler = new CommandHandlerTransactionsImpl(executor)

        when:
        def response = handler.processTransaction(
                queryInput(parser, body, config(0), HeaderPreferTransaction.PreferHandling.STRICT),
                requestContext())

        then:
        response.status == 200
        parser.parseCount == 1
        parser.parsedBody == body
        executor.validate
    }

    def 'maxActionsPerRequest aborts lazily when the request has too many actions'() {
        given:
        RecordingParser parser = new RecordingParser(actions: [action(), action()])
        CountingExecutor executor = new CountingExecutor()
        CommandHandlerTransactionsImpl handler = new CommandHandlerTransactionsImpl(executor)

        when:
        handler.processTransaction(
                queryInput(parser, '{"transaction":[]}', config(1), HeaderPreferTransaction.PreferHandling.LENIENT),
                requestContext())

        then:
        BadRequestException e = thrown()
        e.message.contains('more than 1 action')
        executor.seen == 1
    }

    def 'maxActionsPerRequest allows requests at the configured limit'() {
        given:
        RecordingParser parser = new RecordingParser(actions: [action()])
        CountingExecutor executor = new CountingExecutor()
        CommandHandlerTransactionsImpl handler = new CommandHandlerTransactionsImpl(executor)

        when:
        def response = handler.processTransaction(
                queryInput(parser, '{"transaction":[]}', config(1), HeaderPreferTransaction.PreferHandling.LENIENT),
                requestContext())

        then:
        response.status == 200
        executor.seen == 1
    }

    private static CommandHandlerTransactions.QueryInputTransaction queryInput(
            RecordingParser parser,
            String body,
            TransactionsConfiguration config,
            HeaderPreferTransaction.PreferHandling handling) {
        return ImmutableQueryInputTransaction.builder()
                .parser(parser)
                .requestBody(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
                .contentType(MediaType.APPLICATION_JSON_TYPE)
                .config(config)
                .requestCrs(OgcCrs.CRS84)
                .handling(handling)
                .returnPreference(HeaderPreferTransaction.PreferReturn.REPRESENTATION)
                .build()
    }

    private static TransactionsConfiguration config(Integer maxActions) {
        return new ImmutableTransactionsConfiguration.Builder()
                .atomic(true)
                .batch(true)
                .maxActionsPerRequest(maxActions)
                .build()
    }

    private ApiRequestContext requestContext() {
        return Stub(ApiRequestContext) {
            getApi() >> null
        }
    }

    private static TxAction action() {
        return new ImmutableTxDelete.Builder().collectionId('c').build()
    }

    private static class RecordingParser implements TransactionParser {
        List<TxAction> actions
        int parseCount
        String parsedBody

        @Override
        boolean canParse(MediaType mediaType) {
            return true
        }

        @Override
        Transaction parse(InputStream body, MediaType mediaType) {
            parseCount++
            parsedBody = new String(body.readAllBytes(), StandardCharsets.UTF_8)
            return new FixedTransaction(TxSemantic.ATOMIC, actions)
        }
    }

    private static class FixedTransaction implements Transaction {
        private final TxSemantic semantic
        private final List<TxAction> actions
        boolean closed

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
            closed = true
        }
    }

    private static class CountingExecutor implements TransactionExecutor {
        int seen
        boolean validate

        @Override
        ExecutionResult execute(
                Transaction transaction,
                OgcApi api,
                ApiRequestContext requestContext,
                EpsgCrs requestCrs,
                boolean validate) {
            this.validate = validate
            Iterator<TxAction> it = transaction.actions()
            while (it.hasNext()) {
                it.next()
                seen++
            }
            return new ImmutableExecutionResult.Builder()
                    .semantic(transaction.semantic)
                    .actionResults([])
                    .build()
        }
    }
}
