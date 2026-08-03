/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml
import de.ii.ogcapi.features.gml.domain.FeatureTransformationContextGml
import de.ii.xtraplatform.features.domain.FeatureSchema
import spock.lang.Specification

import java.util.function.Consumer

class GmlWriterIdSpec extends Specification {

    def encoding = Mock(FeatureTransformationContextGml)
    def writer = new GmlWriterId()

    def idContext(String value, String canonical) {
        def schema = Stub(FeatureSchema)
        schema.isId() >> true
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> value
        context.canonicalFeatureId() >> canonical
        context.encoding() >> encoding
        return context
    }

    def 'a composite id keeps the canonical id on gml:identifier'() {
        given:
        encoding.getGmlIdPrefix() >> Optional.empty()

        when:
        writer.onValue(idContext('DEHE862010016eK320170613T080316Z', 'DEHE862010016eK3'), {} as Consumer)

        then:
        1 * encoding.setCurrentGmlId('DEHE862010016eK320170613T080316Z')
        1 * encoding.setCurrentGmlIdentifierValue('DEHE862010016eK3')
    }

    def 'a canonical id does not override gml:identifier'() {
        given:
        encoding.getGmlIdPrefix() >> Optional.empty()

        when:
        writer.onValue(idContext('DEHE862010016eK3', null), {} as Consumer)

        then:
        1 * encoding.setCurrentGmlId('DEHE862010016eK3')
        0 * encoding.setCurrentGmlIdentifierValue(_)
    }
}
