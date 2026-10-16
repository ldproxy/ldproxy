/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app

import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.ogcapi.tiles.domain.TilesProviders
import de.ii.xtraplatform.cql.domain.TemporalLiteral
import spock.lang.Specification

class QueryParameterDatetimeTileSpec extends Specification {

    def parameter = new QueryParameterDatetimeTile(Stub(SchemaValidator), Stub(TilesProviders))

    def 'a timestamp with an offset is converted to UTC'() {
        expect:
        parameter.parse(value, [:], null, Optional.empty()) == TemporalLiteral.of(expected)

        where:
        value                        || expected
        '2020-01-01T10:00:00+01:00'  || '2020-01-01T09:00:00Z'
        '1095-07-31T05:25:41-18:00'  || '1095-07-31T23:25:41Z'
        '2020-01-01T10:00:00Z'       || '2020-01-01T10:00:00Z'
        '2020-01-01'                 || '2020-01-01'
    }

    def 'the timestamps of an interval are converted to UTC'() {
        expect:
        parameter.parse('2020-01-01T10:00:00+01:00/..', [:], null, Optional.empty()) ==
                TemporalLiteral.of(['2020-01-01T09:00:00Z', '..'])
    }
}
