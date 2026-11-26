/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.projections.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.core.domain.SchemaInfo
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.QueryParameterSet
import de.ii.ogcapi.foundation.domain.SchemaValidator
import jakarta.ws.rs.core.MultivaluedHashMap
import spock.lang.Specification

/**
 * OGC API Features Part 6, /req/properties/exclusive-parameters A: a request with both query
 * parameters "properties" and "exclude-properties" is a bad request. /req/properties/exclude-parameter
 * B: an empty value of "exclude-properties" is an empty list, no property is excluded.
 */
class QueryParameterPropertiesExclusiveSpec extends Specification {

    QueryParameterProperties properties = new QueryParameterProperties(Stub(SchemaInfo), Stub(SchemaValidator))
    QueryParameterExcludeProperties excludeProperties = new QueryParameterExcludeProperties(Stub(SchemaInfo), Stub(SchemaValidator), Stub(FeaturesCoreProviders))

    QueryParameterSet evaluate(Map<String, String> query) {
        def values = new MultivaluedHashMap<String, String>()
        query.each { k, v -> values.add(k, v) }
        // the definitions are a set, the parsing order must not depend on their order
        // the API is not used to parse the two parameters
        return QueryParameterSet.of([excludeProperties, properties], values).evaluate(null, Optional.empty())
    }

    def 'a request with both parameters is rejected'() {
        when:
        evaluate(['properties': 'name', 'exclude-properties': 'height'])

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The query parameters 'properties' and 'exclude-properties' cannot be combined."
    }

    def 'each parameter on its own is accepted'() {
        when:
        def withProperties = evaluate(['properties': 'name,height'])
        def withExclude = evaluate(['exclude-properties': 'height'])

        then:
        withProperties.getValue(properties) == Optional.of(['name', 'height'])
        withProperties.getValue(excludeProperties).isEmpty()
        withExclude.getValue(excludeProperties) == Optional.of(['height'])
        withExclude.getValue(properties).isEmpty()
    }

    def 'exclude-properties is parsed after properties'() {
        expect:
        excludeProperties.getPriority() > properties.getPriority()
    }

    def 'an empty exclude-properties is accepted as an empty list'() {
        when:
        def withEmpty = evaluate(['exclude-properties': ''])

        then:
        withEmpty.getValue(excludeProperties) == Optional.of([])
    }

    def 'an empty exclude-properties together with properties is rejected'() {
        when:
        evaluate(['properties': 'name', 'exclude-properties': ''])

        then:
        thrown(IllegalArgumentException)
    }

    def 'the schema of exclude-properties allows an empty list, the schema of properties does not'() {
        given:
        def apiData = Stub(OgcApiDataV2)

        expect:
        excludeProperties.getSchema(apiData).getMinItems() == null
        properties.getSchema(apiData).getMinItems() == 1
    }
}
