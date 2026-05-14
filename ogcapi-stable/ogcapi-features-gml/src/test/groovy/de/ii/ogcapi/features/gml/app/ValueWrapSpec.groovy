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
import de.ii.ogcapi.features.gml.domain.ModifiableStateGml
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase.Type
import spock.lang.Specification

import java.util.function.Consumer

class ValueWrapSpec extends Specification {

    def 'single wrapper element is emitted around value'() {
        // given
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> ['qualitaetsangaben.herkunft.processStep.dateTime': ['gco:DateTime']]
        encoding.qualifyPropertyElementName(_) >> { String n -> 'gmd:' + n }

        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'dateTime'
            getType() >> Type.DATETIME
            getFullPathAsString() >> 'qualitaetsangaben.herkunft.processStep.dateTime'
            getUnit() >> Optional.empty()
        }
        def context = Stub(EncodingAwareContextGml) {
            schema() >> Optional.of(schema)
            value() >> '2010-11-06T20:53:16Z'
            encoding() >> encoding
        }

        // when
        new GmlWriterProperties().onValue(context, {} as Consumer)

        // then
        1 * encoding.writeStartElement('gmd:dateTime')
        1 * encoding.writeStartElement('gco:DateTime')
        1 * encoding.writeCharacters('2010-11-06T20:53:16Z')
        2 * encoding.writeEndElement()
    }

    def 'multiple wrapper elements are emitted outer to inner'() {
        // given
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> ['lebenszeitintervall': ['aaa:AA_Lebenszeitintervall', 'aaa:beginnt']]
        encoding.qualifyPropertyElementName(_) >> { String n -> n }

        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'lebenszeitintervall'
            getType() >> Type.DATETIME
            getFullPathAsString() >> 'lebenszeitintervall'
            getUnit() >> Optional.empty()
        }
        def context = Stub(EncodingAwareContextGml) {
            schema() >> Optional.of(schema)
            value() >> '2010-11-11T02:43:17Z'
            encoding() >> encoding
        }

        // when
        new GmlWriterProperties().onValue(context, {} as Consumer)

        // then
        1 * encoding.writeStartElement('lebenszeitintervall')
        1 * encoding.writeStartElement('aaa:AA_Lebenszeitintervall')
        1 * encoding.writeStartElement('aaa:beginnt')
        1 * encoding.writeCharacters('2010-11-11T02:43:17Z')
        3 * encoding.writeEndElement()
    }

    def 'no wrapping when path is not in valueWrap'() {
        // given
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> [:]
        encoding.qualifyPropertyElementName(_) >> { String n -> n }

        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'gebaeudefunktion'
            getType() >> Type.STRING
            getFullPathAsString() >> 'gebaeudefunktion'
            getUnit() >> Optional.empty()
        }
        def context = Stub(EncodingAwareContextGml) {
            schema() >> Optional.of(schema)
            value() >> '2000'
            encoding() >> encoding
        }

        // when
        new GmlWriterProperties().onValue(context, {} as Consumer)

        // then
        1 * encoding.writeStartElement('gebaeudefunktion')
        1 * encoding.writeCharacters('2000')
        1 * encoding.writeEndElement()
    }
}
