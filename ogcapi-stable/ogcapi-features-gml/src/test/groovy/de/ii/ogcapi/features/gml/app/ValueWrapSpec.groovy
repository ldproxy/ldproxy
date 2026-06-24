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
import de.ii.xtraplatform.features.domain.SchemaConstraints
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
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> 'gmd:' + n }

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
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> n }

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
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> n }

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

    def 'iso 19139 codeList attributes are emitted on the wrapper matching the codelist id'() {
        given:
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> ['qualitaetsangaben.herkunft.processStep.responsibleParty.role': ['gmd:CI_RoleCode']]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> 'gmd:' + n }

        def constraints = Stub(SchemaConstraints) {
            getCodelist() >> Optional.of('CI_RoleCode')
        }
        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'role'
            getType() >> Type.STRING
            getFullPathAsString() >> 'qualitaetsangaben.herkunft.processStep.responsibleParty.role'
            getUnit() >> Optional.empty()
            getConstraints() >> Optional.of(constraints)
        }
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> 'processor'
        context.encoding() >> encoding

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:role')
        1 * encoding.writeStartElement('gmd:CI_RoleCode')
        1 * encoding.writeAttribute('codeList', 'https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#CI_RoleCode')
        1 * encoding.writeAttribute('codeListValue', 'processor')
        1 * encoding.writeCharacters('processor')
        2 * encoding.writeEndElement()
    }

    def 'no iso 19139 codeList attributes when the wrapped property has no codelist constraint'() {
        given:
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> ['organisationName': ['gco:CharacterString']]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> 'gmd:' + n }

        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'organisationName'
            getType() >> Type.STRING
            getFullPathAsString() >> 'organisationName'
            getUnit() >> Optional.empty()
            getConstraints() >> Optional.empty()
        }
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> '062550'
        context.encoding() >> encoding

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:organisationName')
        1 * encoding.writeStartElement('gco:CharacterString')
        1 * encoding.writeCharacters('062550')
        2 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }

    def 'no iso 19139 codeList attributes when the wrapper local name differs from the codelist id'() {
        given:
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> ['role': ['gco:CharacterString']]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> 'gmd:' + n }

        def constraints = Stub(SchemaConstraints) {
            getCodelist() >> Optional.of('CI_RoleCode')
        }
        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'role'
            getType() >> Type.STRING
            getFullPathAsString() >> 'role'
            getUnit() >> Optional.empty()
            getConstraints() >> Optional.of(constraints)
        }
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> 'processor'
        context.encoding() >> encoding

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:role')
        1 * encoding.writeStartElement('gco:CharacterString')
        1 * encoding.writeCharacters('processor')
        2 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }

    def 'no iso 19139 codeList attributes when codeListUriTemplateIso19139 is not configured'() {
        given:
        // Opt-in gate: a codelist-constrained property wrapped in the matching element is encoded
        // plainly (no codeList/codeListValue) unless codeListUriTemplateIso19139 is set.
        def encoding = Mock(FeatureTransformationContextGml)
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> ['role': ['gmd:CI_RoleCode']]
        encoding.getCodeListUriTemplateIso19139() >> Optional.empty()
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> 'gmd:' + n }

        def constraints = Stub(SchemaConstraints) {
            getCodelist() >> Optional.of('CI_RoleCode')
        }
        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'role'
            getType() >> Type.STRING
            getFullPathAsString() >> 'role'
            getUnit() >> Optional.empty()
            getConstraints() >> Optional.of(constraints)
        }
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> 'processor'
        context.encoding() >> encoding

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:role')
        1 * encoding.writeStartElement('gmd:CI_RoleCode')
        1 * encoding.writeCharacters('processor')
        2 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }
}
