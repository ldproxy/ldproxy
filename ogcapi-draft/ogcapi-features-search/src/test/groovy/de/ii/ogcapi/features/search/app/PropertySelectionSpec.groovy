/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.search.domain.QueryExpression
import spock.lang.Specification

// The effective field list of a sub-query, resolved from the "properties" and "excludeProperties"
// members of a query expression, each of which can be set globally (on the query expression) and
// locally (on a single query).
class PropertySelectionSpec extends Specification {

    static final List<String> ALL_PROPERTIES = ['id', 'name', 'geometry', 'lzi_beg', '_updated']

    def 'all properties are selected when nothing is selected and nothing excluded'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, [], [], [])

        then:
        fields == ['*']
    }

    def 'a selection without exclusions is passed through, global before local'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, ['id'], ['name'], [])

        then:
        fields == ['id', 'name']
    }

    def 'a duplicate between the global and local selection is dropped'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, ['id', 'name'], ['name'], [])

        then:
        fields == ['id', 'name']
    }

    def 'an exclusion without a selection subtracts from all properties'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, [], [], ['_updated'])

        then:
        fields == ['id', 'name', 'geometry', 'lzi_beg']
    }

    def 'an exclusion is subtracted from an explicit selection'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, ['id', '_updated'], ['name'], ['_updated'])

        then:
        fields == ['id', 'name']
    }

    def 'excluding a property the collection does not have is a no-op'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, [], [], ['nicht_vorhanden'])

        then:
        fields == ALL_PROPERTIES
    }

    def 'excluding every property yields an empty selection for the caller to reject'() {
        when:
        def fields = PropertySelection.fields(ALL_PROPERTIES, [], [], ALL_PROPERTIES)

        then:
        fields.isEmpty()
    }

    def 'the global and local exclusion lists are unioned'() {
        when:
        def exclusions = PropertySelection.exclusions(['_updated'], ['lzi_beg'])

        then:
        exclusions == ['_updated', 'lzi_beg']
    }

    def 'a name excluded both globally and locally appears once'() {
        when:
        def exclusions = PropertySelection.exclusions(['_updated'], ['_updated'])

        then:
        exclusions == ['_updated']
    }

    def 'unknown reports the names that are not known, once each'() {
        when:
        def unknown = PropertySelection.unknown(['_updated', 'tippfehler', 'tippfehler'], ALL_PROPERTIES)

        then:
        unknown == ['tippfehler']
    }

    def 'unknown reports nothing when every name is known'() {
        when:
        def unknown = PropertySelection.unknown(['_updated', 'name'], ALL_PROPERTIES)

        then:
        unknown.isEmpty()
    }

    def 'excludeProperties is deserialized at both the global and the local level'() {
        given:
        def json = '''{
          "excludeProperties": [ "_updated" ],
          "queries": [
            { "collections": [ "ax_flurstueck" ], "excludeProperties": [ "lzi_beg" ] },
            { "collections": [ "ax_gebaeude" ] }
          ]
        }'''

        when:
        QueryExpression queryExpression = QueryExpression.MAPPER.readValue(json, QueryExpression.class)

        then:
        queryExpression.getExcludeProperties() == ['_updated']
        queryExpression.getQueries().get(0).getExcludeProperties() == ['lzi_beg']
        queryExpression.getQueries().get(1).getExcludeProperties().isEmpty()
    }
}
