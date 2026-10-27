/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app

import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.ogcapi.tiles.domain.TilesProviders
import spock.lang.Specification

class QueryParameterLimitTileSpec extends Specification {

    def 'a tile has at least one feature, like a page of features'() {
        given:
        // a stub returns no tile provider, so the schema has no default value
        def parameter = new QueryParameterLimitTile(Stub(SchemaValidator), Stub(TilesProviders))
        def apiData = new ImmutableOgcApiDataV2.Builder().id('buildings').build()

        expect:
        parameter.getSchema(apiData).getMinimum() == BigDecimal.ONE
        parameter.getSchema(apiData, 'buildings').getMinimum() == BigDecimal.ONE
    }
}
