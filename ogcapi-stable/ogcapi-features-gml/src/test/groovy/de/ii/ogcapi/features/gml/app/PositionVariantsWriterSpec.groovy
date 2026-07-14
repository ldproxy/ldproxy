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
import de.ii.xtraplatform.features.gml.domain.GmlVersion
import de.ii.ogcapi.features.gml.domain.ImmutablePositionVariants
import de.ii.ogcapi.features.gml.domain.ImmutableSrsNameMapping
import de.ii.ogcapi.features.gml.domain.PositionVariants
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory
import de.ii.xtraplatform.crs.domain.EpsgCrs
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.geometries.domain.Point
import spock.lang.Specification

import java.util.function.Consumer

/**
 * {@link GmlWriterPositionVariants}: a foreign-CRS position is written once, from the variant
 * property, with the stored verbatim srsName and the false-easting difference subtracted; a 1D
 * position is reconstructed as a gml:Point with srsDimension="1"; the helper properties and the
 * derived native copy are suppressed; native rows pass through to the normal geometry writer.
 */
class PositionVariantsWriterSpec extends Specification {

    static final String GK3_HE100 = 'urn:adv:crs:DE_DHDN_3GK3_HE100'
    static final String DHHN92 = 'urn:adv:crs:DE_DHHN92_NH'

    static PositionVariants variants() {
        new ImmutablePositionVariants.Builder()
                .putVariantProperties(GK3_HE100, 'pos_gk3')
                .verticalProperty('pos_h')
                .addVerticalSrsNames(DHHN92)
                .srsNameProperty('pos_srs')
                .build()
    }

    def encoding = Mock(FeatureTransformationContextGml)
    def writer = new StringWriter()
    def xmlWriter = javax.xml.stream.XMLOutputFactory.newInstance().createXMLStreamWriter(writer)
    def crsTransformerFactory = Stub(CrsTransformerFactory) {
        getTransformer(_, _) >> Optional.empty()
    }
    def gmlWriter = new GmlWriterPositionVariants(crsTransformerFactory)

    def setup() {
        encoding.getPositionVariants() >> ['position': variants()]
        encoding.getGmlPrefix() >> 'gml'
        encoding.getWriter() >> xmlWriter
        encoding.getGmlVersion() >> GmlVersion.GML32
        encoding.getGmlIdOnGeometries() >> false
        encoding.getUseSurfaceAndCurve() >> false
        encoding.getGmlIdPrefix() >> Optional.empty()
        encoding.getSrsNameMappings() >> [new ImmutableSrsNameMapping.Builder()
                                                  .crs(EpsgCrs.of(5677))
                                                  .value(GK3_HE100)
                                                  .falseEastingDifference(3000000d)
                                                  .build()]
    }

    def valueContext(String path, String value) {
        def schema = Stub(FeatureSchema) {
            getFullPathAsString() >> path
        }
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.value() >> value
        context.encoding() >> encoding
        return context
    }

    def geometryContext(String path, Point point) {
        def schema = Stub(FeatureSchema) {
            getFullPathAsString() >> path
        }
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.of(schema)
        context.geometry() >> point
        context.encoding() >> encoding
        return context
    }

    def 'a 1D position is reconstructed at the vertical property token'() {
        given:
        def next = Mock(Consumer)

        when:
        gmlWriter.onValue(valueContext('pos_srs', DHHN92), next)
        gmlWriter.onValue(valueContext('pos_h', '229.94'), next)

        then:
        1 * encoding.writeStartElement('position')
        1 * encoding.writeStartElement('gml:Point')
        1 * encoding.writeAttribute('srsName', DHHN92)
        1 * encoding.writeAttribute('srsDimension', '1')
        1 * encoding.writeStartElement('gml:pos')
        1 * encoding.writeCharacters('229.94')
        3 * encoding.writeEndElement()
        0 * next.accept(_)
    }

    def 'a foreign-CRS position is written from the variant with verbatim srsName and inverse shift'() {
        given:
        def next = Mock(Consumer)
        def variant = Point.of(3446104.62d, 5551059.77d, EpsgCrs.of(5677))
        def derivedNative = Point.of(446051.2d, 5549279.4d, EpsgCrs.of(25832))

        when:
        gmlWriter.onValue(valueContext('pos_srs', GK3_HE100), next)
        gmlWriter.onGeometry(geometryContext('pos_gk3', variant), next)
        gmlWriter.onGeometry(geometryContext('position', derivedNative), next)

        then:
        1 * encoding.writeStartElement('position')
        1 * encoding.writeEndElement()
        0 * next.accept(_)
        xmlWriter.flush()
        writer.toString().contains('srsName="' + GK3_HE100 + '"')
        writer.toString().contains('446104.62 5551059.77')
        xmlWriter.flush()
        !writer.toString().contains('3446104.62')
        !writer.toString().contains('446051.2')
    }

    def 'a native position passes through to the normal geometry writer'() {
        given:
        def next = Mock(Consumer)
        def nativePoint = Point.of(448733.315d, 5539621.758d, EpsgCrs.of(25832))
        def context = geometryContext('position', nativePoint)

        when:
        gmlWriter.onGeometry(context, next)

        then:
        1 * next.accept(context)
        0 * encoding.writeStartElement(_)
    }
}
