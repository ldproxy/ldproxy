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

/**
 * The {@code uom} attribute (gml:MeasureType) is written for numeric properties with a
 * {@code unit} in the provider schema — both for single values and for the members of a
 * value array (e.g. {@code VALUE_ARRAY} of {@code FLOAT}), whose numeric type is the
 * {@code valueType}.
 */
class UomAttributeSpec extends Specification {

    def encoding = Mock(FeatureTransformationContextGml)

    def setup() {
        def state = ModifiableStateGml.create()
        encoding.getState() >> state
        encoding.getXmlAttributes() >> []
        encoding.getCodelistProperties() >> [:]
        encoding.getValueWrap() >> [:]
        encoding.qualifyPropertyElementName(_, _) >> { String n, String _o -> n }
        encoding.mapUom(_) >> { String uom -> 'urn:adv:uom:' + uom }
    }

    def context(FeatureSchema schema, String value) {
        def ctx = Stub(EncodingAwareContextGml)
        ctx.schema() >> Optional.of(schema)
        ctx.value() >> value
        ctx.encoding() >> encoding
        return ctx
    }

    def 'a FLOAT property with a unit gets the uom attribute'() {
        given:
        def schema = Stub(FeatureSchema)
        schema.isValue() >> true
        schema.getName() >> 'wert'
        schema.getType() >> Type.FLOAT
        schema.getValueType() >> Optional.empty()
        schema.getFullPathAsString() >> 'wert'
        schema.getUnit() >> Optional.of('s-2')

        when:
        new GmlWriterProperties().onValue(context(schema, '2.0'), {} as Consumer)

        then:
        1 * encoding.writeStartElement('wert')
        1 * encoding.writeAttribute('uom', 'urn:adv:uom:s-2')
        1 * encoding.writeCharacters('2.0')
        1 * encoding.writeEndElement()
    }

    def 'a member of a FLOAT value array with a unit gets the uom attribute'() {
        given:
        def schema = Stub(FeatureSchema)
        schema.isValue() >> true
        schema.getName() >> 'messhoehe'
        schema.getType() >> Type.VALUE_ARRAY
        schema.getValueType() >> Optional.of(Type.FLOAT)
        schema.getFullPathAsString() >> 'messhoehe'
        schema.getUnit() >> Optional.of('mm')

        when:
        new GmlWriterProperties().onValue(context(schema, '10'), {} as Consumer)

        then:
        1 * encoding.writeStartElement('messhoehe')
        1 * encoding.writeAttribute('uom', 'urn:adv:uom:mm')
        1 * encoding.writeCharacters('10')
        1 * encoding.writeEndElement()
    }
}
