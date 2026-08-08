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
 * Harness for specs that assert the bytes a real {@code FeatureTransformationContextGml} writes,
 * rather than the calls a mocked one receives. Anything that depends on how {@code XMLStreamWriter}
 * output and the encoder's own output buffer interleave — placeholders, comments — is only visible
 * in the emitted bytes, and only against the StAX implementation the runtime resolves (declared as
 * a test dependency of this module).
 */
abstract class AbstractGmlOutputSpec extends Specification {

    static final String GML_NS = 'http://www.opengis.net/gml/3.2'
    static final String WFS_NS = 'http://www.opengis.net/wfs/2.0'
    static final String AP_NS = 'http://example.org/ap'

    static final String FEATURE_ID = 'DEHE86202002C3mG'

    def out = new ByteArrayOutputStream()

    /** The bytes written so far. */
    String written() {
        out.toString('UTF-8')
    }

    /**
     * A context for one collection whose feature type {@code AP_PTO} is bound to the {@code ap}
     * namespace, with the given properties mapped to XML attributes and to annotation comments.
     */
    FeatureTransformationContextGml context(
            boolean featureCollection,
            List<String> xmlAttributes = [],
            List<String> xmlComments = []) {
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .putObjectTypeNamespaces('AP_PTO', 'ap')
                .build()
        def encoding = new ImmutableCollectionEncodingGml.Builder()
                .config(config)
                .xmlAttributes(xmlAttributes)
                .xmlComments(xmlComments)
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
                .featureSchemas(['ap_pto': Optional.of(featureTypeSchema())])
                .namespaces(['gml': GML_NS, 'wfs': WFS_NS, 'ap': AP_NS])
                .collectionEncodings(['ap_pto': encoding])
                .build()
    }

    FeatureSchema featureTypeSchema() {
        def schema = Stub(FeatureSchema)
        schema.getObjectType() >> Optional.of('AP_PTO')
        return schema
    }

    /** The document opening GmlWriterSkeleton.onStart writes for a collection response. */
    void writeCollectionStart(FeatureTransformationContextGml context) {
        context.writeProlog()
        context.writeStartElement('wfs:FeatureCollection')
        context.writeNamespace('wfs', WFS_NS)
        context.writeNamespace('gml', GML_NS)
        context.writeNamespace('ap', AP_NS)
        context.closeStartElement()
        context.flush()
    }

    /** Closes the element {@link #writeCollectionStart} opened, completing the document. */
    void writeCollectionEnd(FeatureTransformationContextGml context) {
        context.writeEndElement()
        context.flush()
    }

    /**
     * Opens a feature the way GmlWriterSkeleton.onFeatureStart does: member wrapper (for a
     * collection response), feature element, its gml:id and the XML-attribute placeholder.
     */
    void openFeature(FeatureTransformationContextGml context, boolean featureCollection) {
        if (featureCollection) {
            context.writeStartElement('wfs:member')
        }
        context.writeStartElement(context.startGmlObject(featureTypeSchema()))
        context.writeGmlIdAttribute()
        context.writeXmlAttPlaceholder()
        context.setCurrentGmlId(FEATURE_ID)
    }

    /** Closes what {@link #openFeature} opened and flushes the feature, resolving placeholders. */
    void closeFeature(FeatureTransformationContextGml context, boolean featureCollection) {
        context.writeEndElement()
        if (featureCollection) {
            context.writeEndElement()
        }
        context.closeGmlObject()
        context.flush()
    }
}
