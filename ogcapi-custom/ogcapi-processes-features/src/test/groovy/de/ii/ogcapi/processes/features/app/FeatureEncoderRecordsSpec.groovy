/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app

import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.PropertyBase
import de.ii.xtraplatform.features.domain.SchemaBase.Type
import de.ii.xtraplatform.features.domain.transform.PropertySfFlat
import spock.lang.Specification

class FeatureEncoderRecordsSpec extends Specification {

    static FeatureSchema schema(String name, Type type, List<String> parentPath = []) {
        new ImmutableFeatureSchema.Builder().name(name).type(type).path([name]).parentPath(parentPath).build()
    }

    PropertySfFlat value(FeatureSchema schema, String value, PropertySfFlat parent = null) {
        Stub(PropertySfFlat) {
            getType() >> PropertyBase.Type.VALUE
            getSchema() >> Optional.of(schema)
            getName() >> schema.getName()
            getValue() >> value
            getParent() >> Optional.ofNullable(parent)
        }
    }

    def "the id of a feature reference is a value of the reference, other values keep their path"() {
        given:
        PropertySfFlat reference = Stub(PropertySfFlat) {
            getType() >> PropertyBase.Type.OBJECT
            getSchema() >> Optional.of(schema("wijkcode", Type.FEATURE_REF))
            getName() >> "wijkcode"
            getParent() >> Optional.empty()
        }
        List<PropertySfFlat> nested = [
                value(schema("id", Type.STRING, ["wijkcode"]), "WK030701", reference),
                value(schema("title", Type.STRING, ["wijkcode"]), "Stadskern", reference)]
        reference.getNestedProperties() >> nested
        Map<String, List<Object>> values = [:]

        when:
        FeatureEncoderRecords.collect(reference, values)
        FeatureEncoderRecords.collect(value(schema("aantal_inwoners", Type.INTEGER), "70"), values)

        then:
        values == [wijkcode: ["WK030701"], "wijkcode.title": ["Stadskern"], aantal_inwoners: [70L]]
    }
}
