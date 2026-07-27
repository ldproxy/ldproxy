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
import de.ii.ogcapi.features.gml.domain.XmlPathElement
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase.Type
import de.ii.xtraplatform.features.domain.SchemaConstraints
import spock.lang.Specification

import java.util.function.Consumer

class XmlPathsSpec extends Specification {

    static List<XmlPathElement> chain(String... segments) {
        segments.collect { XmlPathElement.parse(it) }
    }

    def encoding = Mock(FeatureTransformationContextGml)
    def state = ModifiableStateGml.create()

    def setup() {
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getNamespaces() >> [gmd: 'http://www.isotc211.org/2005/gmd', gco: 'http://www.isotc211.org/2005/gco']
    }

    private EncodingAwareContextGml contextFor(FeatureSchema schema, String value) {
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> value
        context.encoding() >> encoding
        return context
    }

    private FeatureSchema valueSchema(String name, String path, Type type = Type.STRING) {
        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> name
            getType() >> type
            getFullPathAsString() >> path
            getUnit() >> Optional.empty()
            getConstraints() >> Optional.empty()
        }
        return schema
    }

    def 'the uom of a chained property goes on the innermost element'() {
        given: 'a FLOAT property with a unit whose wire form is an element chain'
        encoding.getXmlPaths() >> ['pfh_abstand': chain('pfeilerhoehe', 'AX_Pfeilerhoehe_Lagefestpunkt', 'abstand')]
        encoding.mapUom(_) >> { String uom -> 'urn:adv:uom:' + uom }
        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'pfh_abstand'
            getType() >> Type.FLOAT
            getFullPathAsString() >> 'pfh_abstand'
            getUnit() >> Optional.of('mm')
            getConstraints() >> Optional.empty()
        }

        when:
        new GmlWriterProperties().onValue(contextFor(schema, '12'), {} as Consumer)

        then: 'the unit qualifies the value, not the wrapper — a gml:MeasureType on "abstand"'
        1 * encoding.writeStartElement('pfeilerhoehe')
        1 * encoding.writeStartElement('AX_Pfeilerhoehe_Lagefestpunkt')
        1 * encoding.writeStartElement('abstand')
        1 * encoding.writeAttribute('uom', 'urn:adv:uom:mm')
        1 * encoding.writeCharacters('12')
    }

    def 'chain is emitted outer to inner with the value in the innermost element'() {
        given:
        encoding.getXmlPaths() >> ['qualitaetsangaben.herkunft.processStep.dateTime': chain('gmd:dateTime', 'gco:DateTime')]

        def schema = valueSchema('dateTime', 'qualitaetsangaben.herkunft.processStep.dateTime', Type.DATETIME)
        def context = contextFor(schema, '2010-11-06T20:53:16Z')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:dateTime')
        1 * encoding.writeStartElement('gco:DateTime')
        1 * encoding.writeCharacters('2010-11-06T20:53:16Z')
        1 * encoding.writeEndElement()

        and:
        state.getXmlPathOpenPrefix().size() == 1
        state.getXmlPathOwner().get() == 'qualitaetsangaben.herkunft.processStep.dateTime'
    }

    def 'a flat property is encoded as a nested structure'() {
        given:
        encoding.getXmlPaths() >> ['lzi_beg': chain('lebenszeitintervall', 'AA_Lebenszeitintervall', 'beginnt')]

        def schema = valueSchema('lzi_beg', 'lzi_beg', Type.DATETIME)
        def context = contextFor(schema, '2010-11-11T02:43:17Z')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('lebenszeitintervall')
        1 * encoding.writeStartElement('AA_Lebenszeitintervall')
        1 * encoding.writeStartElement('beginnt')
        1 * encoding.writeCharacters('2010-11-11T02:43:17Z')
        1 * encoding.writeEndElement()
    }

    def 'consecutive properties sharing leading segments are encoded inside one wrapper instance'() {
        given:
        encoding.getXmlPaths() >> [
                'lzi_beg': chain('lebenszeitintervall', 'AA_Lebenszeitintervall', 'beginnt'),
                'lzi_end': chain('lebenszeitintervall', 'AA_Lebenszeitintervall', 'endet')]

        def beg = contextFor(valueSchema('lzi_beg', 'lzi_beg', Type.DATETIME), '2010-11-11T02:43:17Z')
        def end = contextFor(valueSchema('lzi_end', 'lzi_end', Type.DATETIME), '2020-01-01T00:00:00Z')
        def writer = new GmlWriterProperties()

        when:
        writer.onValue(beg, {} as Consumer)
        writer.onValue(end, {} as Consumer)

        then:
        1 * encoding.writeStartElement('lebenszeitintervall')
        1 * encoding.writeStartElement('AA_Lebenszeitintervall')
        1 * encoding.writeStartElement('beginnt')
        1 * encoding.writeStartElement('endet')
        1 * encoding.writeCharacters('2010-11-11T02:43:17Z')
        1 * encoding.writeCharacters('2020-01-01T00:00:00Z')
        2 * encoding.writeEndElement()

        and:
        state.getXmlPathOpenPrefix().collect { it.getName() } == ['lebenszeitintervall', 'AA_Lebenszeitintervall']
        state.getXmlPathOwner().get() == 'lzi_end'
    }

    def 'repeated values of the same property repeat the full chain'() {
        given:
        encoding.getXmlPaths() >> ['mat': chain('modellart', 'AA_Modellart', 'advStandardModell')]

        def writer = new GmlWriterProperties()

        when:
        writer.onValue(contextFor(valueSchema('mat', 'mat'), 'DLKM'), {} as Consumer)
        writer.onValue(contextFor(valueSchema('mat', 'mat'), 'Basis-DLM'), {} as Consumer)

        then:
        2 * encoding.writeStartElement('modellart')
        2 * encoding.writeStartElement('AA_Modellart')
        2 * encoding.writeStartElement('advStandardModell')
        1 * encoding.writeCharacters('DLKM')
        1 * encoding.writeCharacters('Basis-DLM')
        4 * encoding.writeEndElement()
    }

    def 'a property with a partially shared chain closes only the divergent wrappers'() {
        given:
        encoding.getXmlPaths() >> [
                'zpe': chain('qualitaetsangaben', 'AX_DQMitDatenerhebung', 'gmd:dateTime'),
                'src': chain('qualitaetsangaben', 'AX_DQMitDatenerhebung', 'gmd:source', 'AX_Datenerhebung'),
                'oth': chain('qualitaetsangaben', 'AX_Sonstige', 'wert')]

        def writer = new GmlWriterProperties()

        when:
        writer.onValue(contextFor(valueSchema('zpe', 'zpe', Type.DATETIME), '2021-01-01T00:00:00Z'), {} as Consumer)
        writer.onValue(contextFor(valueSchema('src', 'src'), '1000'), {} as Consumer)
        writer.onValue(contextFor(valueSchema('oth', 'oth'), 'x'), {} as Consumer)

        then:
        1 * encoding.writeStartElement('qualitaetsangaben')
        1 * encoding.writeStartElement('AX_DQMitDatenerhebung')
        1 * encoding.writeStartElement('gmd:dateTime')
        1 * encoding.writeStartElement('gmd:source')
        1 * encoding.writeStartElement('AX_Datenerhebung')
        1 * encoding.writeStartElement('AX_Sonstige')
        1 * encoding.writeStartElement('wert')
        3 * encoding.writeCharacters(_)
        // zpe value element + src value element + (src wrapper + AX_DQMitDatenerhebung closed on
        // divergence to oth) + oth value element
        5 * encoding.writeEndElement()

        and:
        state.getXmlPathOpenPrefix().collect { it.getName() } == ['qualitaetsangaben', 'AX_Sonstige']
    }

    def 'an unmapped property closes the open wrappers and is encoded plainly'() {
        given:
        encoding.getXmlPaths() >> ['lzi_beg': chain('lebenszeitintervall', 'AA_Lebenszeitintervall', 'beginnt')]
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> n }

        def writer = new GmlWriterProperties()

        when:
        writer.onValue(contextFor(valueSchema('lzi_beg', 'lzi_beg', Type.DATETIME), '2010-11-11T02:43:17Z'), {} as Consumer)
        writer.onValue(contextFor(valueSchema('gfk', 'gfk'), '1000'), {} as Consumer)

        then:
        1 * encoding.closeXmlPathWrappers()
        1 * encoding.writeStartElement('gfk')
        1 * encoding.writeCharacters('1000')
    }

    def 'the boundary writer closes open wrappers on structural events'() {
        given:
        def context = Stub(EncodingAwareContextGml)
        context.encoding() >> encoding
        def writer = new GmlWriterXmlPaths()

        when:
        writer.onObjectStart(context, {} as Consumer)
        writer.onObjectEnd(context, {} as Consumer)
        writer.onArrayStart(context, {} as Consumer)
        writer.onArrayEnd(context, {} as Consumer)
        writer.onGeometry(context, {} as Consumer)
        writer.onFeatureEnd(context, {} as Consumer)

        then:
        6 * encoding.closeXmlPathWrappers()
    }

    def 'iso 19139 codeList attributes are emitted on the element matching the codelist id'() {
        given:
        encoding.getXmlPaths() >> ['qualitaetsangaben.herkunft.processStep.responsibleParty.role': chain('gmd:role', 'gmd:CI_RoleCode')]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')

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
        def context = contextFor(schema, 'processor')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:role')
        1 * encoding.writeStartElement('gmd:CI_RoleCode')
        1 * encoding.writeAttribute('codeList', 'https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#CI_RoleCode')
        1 * encoding.writeAttribute('codeListValue', 'processor')
        1 * encoding.writeCharacters('processor')
        1 * encoding.writeEndElement()
    }

    def 'no iso 19139 codeList attributes when the property has no codelist constraint'() {
        given:
        encoding.getXmlPaths() >> ['organisationName': chain('gmd:organisationName', 'gco:CharacterString')]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')

        def context = contextFor(valueSchema('organisationName', 'organisationName'), '062550')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:organisationName')
        1 * encoding.writeStartElement('gco:CharacterString')
        1 * encoding.writeCharacters('062550')
        1 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }

    def 'no iso 19139 codeList attributes when no element local name matches the codelist id'() {
        given:
        encoding.getXmlPaths() >> ['role': chain('gmd:role', 'gco:CharacterString')]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')

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
        def context = contextFor(schema, 'processor')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:role')
        1 * encoding.writeStartElement('gco:CharacterString')
        1 * encoding.writeCharacters('processor')
        1 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }

    def 'no iso 19139 codeList attributes when codeListUriTemplateIso19139 is not configured'() {
        given:
        // Opt-in gate: a codelist-constrained property mapped to the matching element is encoded
        // plainly (no codeList/codeListValue) unless codeListUriTemplateIso19139 is set.
        encoding.getXmlPaths() >> ['role': chain('gmd:role', 'gmd:CI_RoleCode')]
        encoding.getCodeListUriTemplateIso19139() >> Optional.empty()

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
        def context = contextFor(schema, 'processor')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:role')
        1 * encoding.writeStartElement('gmd:CI_RoleCode')
        1 * encoding.writeCharacters('processor')
        1 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }


    def 'no iso 19139 codeList attributes on an application-namespace element matching the codelist id'() {
        given:
        // The NAS AX_Datenerhebung wrapper is an application-schema codelist element (adv
        // namespace); the ISO 19139 codeList/codeListValue attributes are not allowed there.
        encoding.getXmlPaths() >> ['src': chain('gmd:description', 'AX_Datenerhebung')]
        encoding.getCodeListUriTemplateIso19139() >> Optional.of('https://schemas.isotc211.org/19139/resources/codelists/gmxCodelists.xml/gmxCodelists.xml#{{codelistId}}')

        def constraints = Stub(SchemaConstraints) {
            getCodelist() >> Optional.of('AX_Datenerhebung')
        }
        def schema = Stub(FeatureSchema) {
            isValue() >> true
            isId() >> false
            getName() >> 'src'
            getType() >> Type.STRING
            getFullPathAsString() >> 'src'
            getUnit() >> Optional.empty()
            getConstraints() >> Optional.of(constraints)
        }
        def context = contextFor(schema, '4200')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('gmd:description')
        1 * encoding.writeStartElement('AX_Datenerhebung')
        1 * encoding.writeCharacters('4200')
        1 * encoding.writeEndElement()
        0 * encoding.writeAttribute('codeList', _)
        0 * encoding.writeAttribute('codeListValue', _)
    }

    def 'iso 19139 quantitative result: valueUnit element and xsi:type on the record are emitted'() {
        given:
        // The full ISO 19139 quantitative-result pattern (NAS genauigkeitswert): the trailing '/'
        // segment injects the empty gmd:valueUnit as first child of gmd:DQ_QuantitativeResult
        // (before gmd:value), and the innermost anyType gco:Record carries its declared xsi:type.
        encoding.getXmlPaths() >> ['q2d.gwt': chain(
                'genauigkeitswert',
                'gmd:DQ_RelativeInternalPositionalAccuracy',
                'gmd:result',
                'gmd:DQ_QuantitativeResult',
                'gmd:valueUnit[xlink:href=urn:adv:uom:m]/',
                'gmd:value',
                'gco:Record[xsi:type=gml:doubleList]')]

        def context = contextFor(valueSchema('gwt', 'q2d.gwt'), '0.0074721')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('genauigkeitswert')
        1 * encoding.writeStartElement('gmd:DQ_RelativeInternalPositionalAccuracy')
        1 * encoding.writeStartElement('gmd:result')
        1 * encoding.writeStartElement('gmd:DQ_QuantitativeResult')

        then:
        1 * encoding.writeStartElement('gmd:valueUnit')
        1 * encoding.writeAttribute('xlink:href', 'urn:adv:uom:m')

        then:
        1 * encoding.writeStartElement('gmd:value')
        1 * encoding.writeStartElement('gco:Record')
        1 * encoding.writeAttribute('xsi:type', 'gml:doubleList')
        1 * encoding.writeCharacters('0.0074721')
        // the empty valueUnit and the value element are closed during emission; the remaining
        // wrappers stay open for merging
        2 * encoding.writeEndElement()

        and:
        state.getXmlPathOpenPrefix().collect { it.getName() } ==
                ['genauigkeitswert', 'gmd:DQ_RelativeInternalPositionalAccuracy', 'gmd:result',
                 'gmd:DQ_QuantitativeResult', 'gmd:valueUnit', 'gmd:value']
    }

    def 'plain chain segments emit no attributes and no injected elements'() {
        given:
        encoding.getXmlPaths() >> ['sonstigewerte': chain(
                'sonstigewerte',
                'gmd:DQ_QuantitativeResult',
                'gco:Record')]

        def context = contextFor(valueSchema('sonstigewerte', 'sonstigewerte'), '0.5')

        when:
        new GmlWriterProperties().onValue(context, {} as Consumer)

        then:
        1 * encoding.writeStartElement('sonstigewerte')
        1 * encoding.writeStartElement('gmd:DQ_QuantitativeResult')
        1 * encoding.writeStartElement('gco:Record')
        1 * encoding.writeCharacters('0.5')
        1 * encoding.writeEndElement()
        0 * encoding.writeStartElement('gmd:valueUnit')
        0 * encoding.writeAttribute(_, _)
    }
}
