/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import java.sql.BatchUpdateException
import java.sql.SQLException
import spock.lang.Specification

class ServerErrorSpec extends Specification {

    def "a failure with SQLSTATE #state is #kind"() {
        given: "the database error, wrapped as the SQL session wraps a failed statement"
        def error = new IllegalStateException("Mutation failed", new SQLException("message", (String) state))

        expect:
        TransactionExecutorImpl.isServerError(error) == serverError

        where:
        state   | serverError
        "08006" | true  // connection failure
        "53100" | true  // disk full
        "57P01" | true  // admin shutdown
        "58030" | true  // I/O error
        "XX000" | true  // internal error
        "40P01" | true  // deadlock
        null    | true  // no SQLSTATE, e.g. a timeout of the connection pool
        "23505" | false // unique violation
        "22023" | false // invalid parameter value, raised by a trigger
        "2F003" | false // prohibited statement, raised by a trigger
        "P0001" | false // raise exception in a trigger

        kind = serverError ? "a server error" : "caused by the request"
    }

    def "the next exception of a failed batch decides"() {
        given:
        def batch = new BatchUpdateException("batch failed", new int[0])
        batch.setNextException(new SQLException("disk full", "53100"))

        expect:
        TransactionExecutorImpl.isServerError(new IllegalStateException("Batched mutation failed", batch))
    }

    def "a failure without a database error is caused by the request"() {
        expect:
        !TransactionExecutorImpl.isServerError(new IllegalArgumentException("invalid value"))
    }
}
