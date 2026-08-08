/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.gml.domain.GmlVersion
import de.ii.xtraplatform.web.domain.URICustomizer
import spock.lang.Specification

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
 *
 * <p>The specs therefore drive the real context (not a mock) and assert on the bytes it writes.
 */
class XmlAttributePlaceholderSpec extends Specification {

    static final String GML_NS = 'http://www.opengis.net/gml/3.2'
    static final String WFS_NS = 'http://www.opengis.net/wfs/2.0'
    static final String AP_NS = 'http://example.org/ap'

    def out = new ByteArrayOutputStream()

    private FeatureTransformationContextGml context(boolean featureCollection, List<String> xmlAttributes) {
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putObjectTypeNamespaces('AP_PTO', 'ap')
                .build()
        def encoding = new ImmutableCollectionEncodingGml.Builder()
                .config(config)
                .xmlAttributes(xmlAttributes)
                .build()
        // the derived serviceUrl is computed while the context is built, so the request has to
        // supply a real URICustomizer
        def request = Stub(ApiRequestContext)
        request.getUriCustomizer() >> new URICustomizer('http://localhost:7080/alkis')
        return ImmutableFeatureTransformationContextGml.builder()
                .api(Stub(OgcApi))
                .apiData(Stub(OgcApiDataV2))
                .outputStream(out)
                .defaultCrs(OgcCrs.CRS84)
                .isFeatureCollection(featureCollection)
                .ogcApiRequest(request)
                .limit(10)
                .offset(0)
                .gmlVersion(GmlVersion.GML32)
                .supportsStandardResponseParameters(false)
                .featureSchemas(['ap_pto': Optional.of(featureSchema())])
                .namespaces(['gml': GML_NS, 'wfs': WFS_NS, 'ap': AP_NS])
                .collectionEncodings(['ap_pto': encoding])
                .build()
    }

    private FeatureSchema featureSchema() {
        def schema = Stub(FeatureSchema)
        schema.getObjectType() >> Optional.of('AP_PTO')
        return schema
    }

    /** The document opening GmlWriterSkeleton.onStart writes for a collection response. */
    private void writeCollectionStart(FeatureTransformationContextGml context) {
        context.writeProlog()
        context.writeStartElement('wfs:FeatureCollection')
        context.writeNamespace('wfs', WFS_NS)
        context.writeNamespace('gml', GML_NS)
        context.writeNamespace('ap', AP_NS)
        context.closeStartElement()
        context.flush()
    }

    /**
     * One feature, written in the order GmlWriterSkeleton.onFeatureStart and GmlWriterProperties
     * use: member wrapper, feature element with its gml:id and XML-attribute placeholder, then the
     * properties — the XML attribute among them.
     */
    private void writeFeature(
            FeatureTransformationContextGml context, boolean featureCollection, Map<String, String> xmlAttributes) {
        if (featureCollection) {
            context.writeStartElement('wfs:member')
        }
        context.writeStartElement(context.startGmlObject(featureSchema()))
        context.writeGmlIdAttribute()
        context.writeXmlAttPlaceholder()
        xmlAttributes.each { name, value -> context.writeAsXmlAtt(name, value) }
        context.setCurrentGmlId('DEHE061200001YFA')
        context.writeStartElement('ap:art')
        context.writeCharacters('ZAE')
        context.writeEndElement()
        context.writeEndElement()
        if (featureCollection) {
            context.writeEndElement()
        }
        context.closeGmlObject()
        context.flush()
    }

    def 'a property mapped to an XML attribute is written into the feature elements start tag'() {
        given: 'a collection response with a property mapped to an XML attribute'
        def context = context(true, ['_updated'])
        writeCollectionStart(context)

        when: 'a feature carrying that attribute is encoded'
        writeFeature(context, true, ['_updated': '2026-08-07T10:00:00Z'])

        then: 'the attribute is on the feature element, after gml:id — not in front of the member wrapper'
        out.toString('UTF-8').endsWith(
                '<wfs:member>' +
                        '<ap:AP_PTO gml:id="DEHE061200001YFA" _updated="2026-08-07T10:00:00Z">' +
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
        out.toString('UTF-8').endsWith(
                '<ap:AP_PTO gml:id="DEHE061200001YFA" _updated="2026-08-07T10:00:00Z">' +
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
        out.toString('UTF-8').endsWith(
                '<wfs:member>' +
                        '<ap:AP_PTO gml:id="DEHE061200001YFA">' +
                        '<ap:art>ZAE</ap:art>' +
                        '</ap:AP_PTO>' +
                        '</wfs:member>')
    }
}
