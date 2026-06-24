/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.transactions.domain.TransactionsConfiguration
import spock.lang.Specification

class PlainMutationStrategySpec extends Specification {

    PlainMutationStrategy subject = new PlainMutationStrategy()

    def 'baseline priority is zero so specialised strategies override it'() {
        expect:
        subject.priority() == 0
    }

    def 'binds to TransactionsConfiguration so it is enabled only where the Transactions building block is enabled'() {
        expect:
        subject.getBuildingBlockConfigurationType() == TransactionsConfiguration
    }
}
