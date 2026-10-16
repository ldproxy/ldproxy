/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app

import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson
import spock.lang.Specification

import java.util.function.Consumer

class JsonFgWriterTimeSpec extends Specification {

    def 'a value without a schema is passed on'() {
        given:
        JsonFgWriterTime writer = new JsonFgWriterTime()
        writer.isEnabled = enabled
        EncodingAwareContextGeoJson context = Stub(EncodingAwareContextGeoJson)
        context.schema() >> Optional.empty()
        context.value() >> '2022-07-04'
        Consumer<EncodingAwareContextGeoJson> next = Mock(Consumer)

        when:
        writer.onValue(context, next)

        then:
        noExceptionThrown()
        1 * next.accept(context)

        where:
        enabled << [true, false]
    }
}
