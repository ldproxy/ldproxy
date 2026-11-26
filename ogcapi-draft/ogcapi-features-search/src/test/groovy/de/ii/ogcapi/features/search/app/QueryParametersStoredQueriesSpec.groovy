/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

/**
 * The description of a stored query parameter is "title: description" from the schema of the
 * parameter, without the name of the parameter in front.
 */
class QueryParametersStoredQueriesSpec extends Specification {

    def 'the description of a stored query parameter is built from title and description'() {
        given:
        def schema = new StringSchema().title(title).description(description)

        expect:
        QueryParametersStoredQueries.describe('statusPand', schema) == expected

        where:
        title         | description                 || expected
        'status pand' | 'Een of meer statussen.'    || 'status pand: Een of meer statussen.'
        'status pand' | null                        || 'status pand.'
        'status pand' | ''                          || 'status pand.'
        null          | 'Een of meer statussen.'    || 'Een of meer statussen.'
        ''            | 'Een of meer statussen.'    || 'Een of meer statussen.'
        null          | null                        || 'statusPand'
    }
}
