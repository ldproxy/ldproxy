/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain

import de.ii.ogcapi.foundation.domain.QueryParameterSet
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaInteger
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaNumber
import de.ii.xtraplatform.jsonschema.domain.JsonSchema
import spock.lang.Specification

class ParameterResolverSpec extends Specification {

    static QueryExpression resolve(Map<String, Object> values, StoredQueryExpression query) {
        def queryParameterSet = [getTypedValues: { values }] as QueryParameterSet
        def schemaValidator = [validate: { s, v -> Optional.empty() }] as SchemaValidator
        return new ParameterResolver(queryParameterSet, schemaValidator, null).visit(query)
    }

    static ParameterValue parameter(String name, JsonSchema schema) {
        return new ImmutableParameterValue.Builder().name(name).schema(schema).build()
    }

    static ImmutableStoredQueryExpression.Builder query(Map<String, JsonSchema> parameters) {
        return new ImmutableStoredQueryExpression.Builder()
                .id('q')
                .addCollections(new ImmutableStringOrParameter.Builder().value('c').build())
                .allParameters(parameters)
    }

    static StoredQueryExpression limitQuery() {
        def schema = new ImmutableJsonSchemaInteger.Builder().build()
        return query([max: schema])
                .limit(new ImmutableIntegerOrParameter.Builder().parameter(parameter('max', schema)).build())
                .build()
    }

    def 'an integer parameter in the 32-bit range sets the limit'() {
        expect:
        resolve([max: 10], limitQuery()).getLimit() == Optional.of(10)
    }

    def 'an integer parameter outside the 32-bit range is rejected for the limit'() {
        when:
        resolve([max: value], limitQuery())

        then:
        def e = thrown IllegalArgumentException
        e.message.contains("parameter 'max'")

        where:
        value << [2147483648L, new BigInteger("9223372036854775808")]
    }

    def 'a number parameter without a fraction sets the maximum allowable offset'() {
        given:
        def schema = new ImmutableJsonSchemaNumber.Builder().build()
        def query = query([offset: schema])
                .maxAllowableOffset(new ImmutableDoubleOrParameter.Builder().parameter(parameter('offset', schema)).build())
                .build()

        expect:
        resolve([offset: 2], query).getMaxAllowableOffset() == Optional.of(2.0d)
    }
}
