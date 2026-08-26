/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

/**
 * Locks the placement of the XML-attribute placeholder against the real output buffer of
 * {@code FeatureTransformationContextGml} — the interleaving of {@code XMLStreamWriter} calls with
 * the raw buffer appends the placeholder mechanism relies on.
 *
 * <p>The placeholder is appended to the buffer directly so that it lands in attribute position of a
 * start tag whose {@code >} is still pending. An {@code XMLStreamWriter} buffers internally and only
 * hands its output to the underlying writer on flush, so the append has to be ordered against that
 * buffer: without it the placeholder — and with it the resolved XML attribute — is emitted in front
 * of the element it belongs to, in a collection response even in front of the member wrapper.
 */
class XmlAttributePlaceholderSpec extends AbstractGmlOutputSpec {

    /** One feature with one property element, and the given XML attributes on the feature element. */
    private void writeFeature(
            FeatureTransformationContextGml context,
            boolean featureCollection,
            Map<String, String> xmlAttributes) {
        openFeature(context, featureCollection)
        xmlAttributes.each { name, value -> context.writeAsXmlAtt(name, value) }
        context.writeStartElement('ap:art')
        context.writeCharacters('ZAE')
        context.writeEndElement()
        closeFeature(context, featureCollection)
    }

    def 'a property mapped to an XML attribute is written into the feature elements start tag'() {
        given: 'a collection response with a property mapped to an XML attribute'
        def context = context(true, ['_updated'])
        writeCollectionStart(context)

        when: 'a feature carrying that attribute is encoded'
        writeFeature(context, true, ['_updated': '2026-08-07T10:00:00Z'])

        then: 'the attribute is on the feature element, after gml:id — not in front of the member wrapper'
        written().endsWith(
                '<wfs:member>' +
                        "<ap:AP_PTO gml:id=\"${FEATURE_ID}\" _updated=\"2026-08-07T10:00:00Z\">" +
                        '<ap:art>ZAE</ap:art>' +
                        '</ap:AP_PTO>' +
                        '</wfs:member>')
    }

    def 'the same holds for a single-feature response, where the feature element is the root'() {
        given: 'an item response with a property mapped to an XML attribute'
        def context = context(false, ['_updated'])
        context.writeProlog()

        when: 'the feature is encoded'
        writeFeature(context, false, ['_updated': '2026-08-07T10:00:00Z'])

        then: 'the attribute is inside the root element, not in front of it'
        written().endsWith(
                "<ap:AP_PTO gml:id=\"${FEATURE_ID}\" _updated=\"2026-08-07T10:00:00Z\">" +
                        '<ap:art>ZAE</ap:art>' +
                        '</ap:AP_PTO>')
    }

    def 'an unused placeholder leaves no trace in the start tag'() {
        given: 'a feature whose XML-attribute placeholder is never filled'
        def context = context(true, ['_updated'])
        writeCollectionStart(context)

        when:
        writeFeature(context, true, [:])

        then: 'the placeholder is removed in place and the start tag is unchanged'
        written().endsWith(
                '<wfs:member>' +
                        "<ap:AP_PTO gml:id=\"${FEATURE_ID}\">" +
                        '<ap:art>ZAE</ap:art>' +
                        '</ap:AP_PTO>' +
                        '</wfs:member>')
    }
}
