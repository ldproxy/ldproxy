/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.cql.domain.BooleanValue2
import de.ii.xtraplatform.cql.domain.In
import de.ii.xtraplatform.cql.domain.ScalarLiteral
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import spock.lang.Specification

class QueryParameterIdsSpec extends Specification {

    OgcApi api = Stub()
    FeatureTypeConfigurationOgcApi collection = Stub()
    FeaturesCoreProviders providers = Stub()

    def setup() {
        api.getData() >> Stub(OgcApiDataV2)
    }

    def 'the values are the distinct items between the commas'() {
        given:
        providers.getFeatureSchema(_, _) >> Optional.of(schema(SchemaBase.Type.STRING))
        QueryParameterIds ids = new QueryParameterIds(Stub(SchemaValidator), providers)

        when:
        def filter = ids.parse('a, b c,ä,a', [:], api, Optional.of(collection))

        then:
        filter == In.of(ScalarLiteral.of('a'), ScalarLiteral.of('b c'), ScalarLiteral.of('ä'))
    }

    def 'values that are not integers are ignored for integer identifiers'() {
        given:
        providers.getFeatureSchema(_, _) >> Optional.of(schema(SchemaBase.Type.INTEGER))
        QueryParameterIds ids = new QueryParameterIds(Stub(SchemaValidator), providers)

        when:
        def filter = ids.parse(value, [:], api, Optional.of(collection))

        then:
        filter == expected

        where:
        value       || expected
        '1,x,2'     || In.of(ScalarLiteral.of('1'), ScalarLiteral.of('2'))
        'x,y'       || BooleanValue2.of(false)
    }

    def 'an empty list selects no feature and a missing parameter no filter'() {
        given:
        providers.getFeatureSchema(_, _) >> Optional.of(schema(SchemaBase.Type.STRING))
        QueryParameterIds ids = new QueryParameterIds(Stub(SchemaValidator), providers)

        expect:
        ids.parse('', [:], api, Optional.of(collection)) == BooleanValue2.of(false)
        ids.parse((String) null, [:], api, Optional.of(collection)) == null
    }

    private static FeatureSchema schema(SchemaBase.Type idType) {
        return new ImmutableFeatureSchema.Builder()
                .name('buildings')
                .type(SchemaBase.Type.OBJECT)
                .sourcePath('/buildings')
                .putPropertyMap('id', new ImmutableFeatureSchema.Builder()
                        .name('id')
                        .type(idType)
                        .sourcePath('id')
                        .role(SchemaBase.Role.ID))
                .build()
    }
}
