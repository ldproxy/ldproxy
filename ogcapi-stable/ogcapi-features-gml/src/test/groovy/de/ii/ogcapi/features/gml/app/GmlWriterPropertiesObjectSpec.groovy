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
import spock.lang.Specification

import java.util.function.Consumer

class GmlWriterPropertiesObjectSpec extends Specification {

    def encoding = Mock(FeatureTransformationContextGml)
    def writer = new GmlWriterProperties()
    Consumer<EncodingAwareContextGml> next = {} as Consumer

    def setup() {
        encoding.getState() >> ModifiableStateGml.create()
        encoding.getXmlPaths() >> [:]
        encoding.getVariableObjectElementNames() >> [:]
        encoding.getObjectTypeSuffixedProperties() >> []
        encoding.qualifyPropertyElementName(_, _) >> 'ex:property'
        encoding.startGmlObject(_) >> 'ex:Object'
    }

    def 'the end of an object closes what its start wrote, also if the end has another schema'() {
        when:
        writer.onObjectStart(context(object('Object')), next)
        writer.onObjectEnd(context(null), next)

        then:
        2 * encoding.writeEndElement()
        1 * encoding.closeGmlObject()
    }

    def 'the end of an object that was not written writes nothing, also if the end has an object schema'() {
        when:
        writer.onObjectStart(context(null), next)
        writer.onObjectEnd(context(object('Object')), next)

        then:
        0 * encoding.writeEndElement()
        0 * encoding.closeGmlObject()
    }

    def 'an object in a link is closed as an object and the link as a link'() {
        when:
        writer.onObjectStart(context(object('Link')), next)
        writer.onObjectStart(context(object('Object')), next)
        writer.onObjectEnd(context(object('Object')), next)

        then:
        2 * encoding.writeEndElement()
        1 * encoding.closeGmlObject()

        when:
        writer.onObjectEnd(context(object('Link')), next)

        then:
        1 * encoding.writeEndElement()
        0 * encoding.closeGmlObject()
    }

    private FeatureSchema object(String objectType) {
        def schema = Stub(FeatureSchema)
        schema.isObject() >> true
        schema.isFeatureRef() >> false
        schema.getName() >> 'property'
        schema.getFullPathAsString() >> 'property'
        schema.getObjectType() >> Optional.of(objectType)
        schema.getOriginObjectType() >> Optional.empty()
        return schema
    }

    private EncodingAwareContextGml context(FeatureSchema featureSchema) {
        def context = Stub(EncodingAwareContextGml)
        context.schema() >> Optional.ofNullable(featureSchema)
        context.encoding() >> encoding
        return context
    }
}
