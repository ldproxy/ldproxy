/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import de.ii.ogcapi.features.gml.app.GmlWriterProperties
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase.Type

import javax.xml.parsers.DocumentBuilderFactory
import java.util.function.Consumer

/**
 * The {@code xmlComments} option encodes a property as a {@code <!-- name: value -->} annotation in
 * the position of its property element, for values that have no place in the GML application schema.
 * A comment is allowed anywhere in element content, so the response stays valid against the
 * application schema — which is the whole point of the option, and is why these specs assert the
 * emitted bytes and parse them back.
 *
 * <p>The properties of a feature are unqualified here: the feature root's object type pins the
 * namespace of the feature element only, it does not propagate to its property children.
 */
class XmlCommentsSpec extends AbstractGmlOutputSpec {

    private FeatureSchema valueSchema(String name, String path = name) {
        def schema = Stub(FeatureSchema)
        schema.isValue() >> true
        schema.getName() >> name
        schema.getType() >> Type.STRING
        schema.getValueType() >> Optional.empty()
        schema.getFullPathAsString() >> path
        schema.getUnit() >> Optional.empty()
        schema.getConstraints() >> Optional.empty()
        schema.getOriginObjectType() >> Optional.empty()
        return schema
    }

    private void writeValue(FeatureTransformationContextGml context, FeatureSchema schema, String value) {
        def ctx = Stub(EncodingAwareContextGml)
        ctx.schema() >> Optional.of(schema)
        ctx.value() >> value
        ctx.encoding() >> context
        new GmlWriterProperties().onValue(ctx, {} as Consumer)
    }

    /** One feature of a collection response, with the given values written as properties. */
    private void writeFeature(
            FeatureTransformationContextGml context, List<List<String>> nameAndValue) {
        openFeature(context, true)
        nameAndValue.each { writeValue(context, valueSchema(it[0]), it[1]) }
        closeFeature(context, true)
    }

    def 'a listed property is encoded as an annotation comment instead of an element'() {
        given: 'a feature whose aktualisiert property is listed in xmlComments'
        def context = context(true, [], ['aktualisiert'])
        writeCollectionStart(context)

        when:
        writeFeature(context, [['aktualisiert', '2026-07-27T08:49:58Z']])
        writeCollectionEnd(context)

        then: 'the value is a comment inside the feature element, and no element carries it'
        written().contains(
                "<ap:AP_PTO gml:id=\"${FEATURE_ID}\">" +
                        '<!-- aktualisiert: 2026-07-27T08:49:58Z -->' +
                        '</ap:AP_PTO>')

        and: 'the response parses — a comment is allowed in element content'
        parse(written())
    }

    def 'an unlisted property is still encoded as an element'() {
        given: 'a feature with nothing listed in xmlComments'
        def context = context(true)
        writeCollectionStart(context)

        when:
        writeFeature(context, [['art', 'ZAE']])
        writeCollectionEnd(context)

        then:
        written().contains("<ap:AP_PTO gml:id=\"${FEATURE_ID}\"><art>ZAE</art></ap:AP_PTO>")
    }

    def 'a comment keeps the position of the property element among its siblings'() {
        given: 'the middle one of three properties is listed'
        def context = context(true, [], ['aktualisiert'])
        writeCollectionStart(context)

        when:
        writeFeature(context, [['art', 'ZAE'], ['aktualisiert', '2026-07-27T08:49:58Z'], ['sit', '1000']])
        writeCollectionEnd(context)

        then: 'the comment sits between the two elements, not collected elsewhere'
        written().contains(
                '<art>ZAE</art>' +
                        '<!-- aktualisiert: 2026-07-27T08:49:58Z -->' +
                        '<sit>1000</sit>')
    }

    def 'a value that would terminate the comment is escaped'() {
        given: 'a listed property whose value contains a double hyphen'
        def context = context(true, [], ['hinweis'])
        writeCollectionStart(context)

        when:
        writeFeature(context, [['hinweis', 'a--b']])
        writeCollectionEnd(context)

        then: 'the double hyphen is replaced, so the comment is not cut short'
        written().contains('<!-- hinweis: a-‐b -->')
        !written().contains('a--b')

        and: 'the response is still parseable'
        parse(written())
    }

    def 'a property listed as both an XML attribute and a comment is encoded as an attribute'() {
        given: 'the same property in xmlAttributes and in xmlComments'
        def context = context(true, ['aktualisiert'], ['aktualisiert'])
        writeCollectionStart(context)

        when:
        writeFeature(context, [['aktualisiert', '2026-07-27T08:49:58Z']])
        writeCollectionEnd(context)

        then: 'the attribute wins and no comment is written'
        written().contains(
                "<ap:AP_PTO gml:id=\"${FEATURE_ID}\" aktualisiert=\"2026-07-27T08:49:58Z\"/>")
        !written().contains('<!--')
    }

    def 'a nested property is commented inside its parent object'() {
        given: 'a listed property below an object property'
        def context = context(true, [], ['daq.aktualisiert'])
        writeCollectionStart(context)

        when:
        openFeature(context, true)
        context.writeStartElement('daq')
        writeValue(context, valueSchema('aktualisiert', 'daq.aktualisiert'), '2026-07-27T08:49:58Z')
        context.writeEndElement()
        closeFeature(context, true)
        writeCollectionEnd(context)

        then:
        written().contains('<daq><!-- aktualisiert: 2026-07-27T08:49:58Z --></daq>')

        and:
        parse(written())
    }

    def 'escapeCommentText only rewrites the comment terminator'() {
        expect:
        FeatureTransformationContextGml.escapeCommentText(value) == expected

        where:
        value    || expected
        'plain'  || 'plain'
        'a-b'    || 'a-b'
        'a--b'   || 'a-‐b'
        'a---b'  || 'a-‐-b'
        'a----b' || 'a-‐-‐b'
    }

    private static parse(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes('UTF-8')))
    }
}
