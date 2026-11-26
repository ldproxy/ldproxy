/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import java.time.Instant
import spock.lang.Specification

class TimestampParsingSpec extends Specification {

    def 'reads the begin of the open version as the database returns it: #value'() {
        expect:
        TransactionExecutorImpl.parseTimestamp(value) == Optional.ofNullable(expected == null ? null : Instant.parse(expected))

        where:
        value                           || expected
        '2007-08-13 00:00:00+00'        || '2007-08-13T00:00:00Z'
        '2007-08-13 02:00:00+02'        || '2007-08-13T00:00:00Z'
        '2007-08-13 00:00:00.5+00'      || '2007-08-13T00:00:00.5Z'
        '2007-08-13 00:00:00+05:30'     || '2007-08-12T18:30:00Z'
        '2007-08-13T00:00:00Z'          || '2007-08-13T00:00:00Z'
        'not a timestamp'               || null
    }
}
