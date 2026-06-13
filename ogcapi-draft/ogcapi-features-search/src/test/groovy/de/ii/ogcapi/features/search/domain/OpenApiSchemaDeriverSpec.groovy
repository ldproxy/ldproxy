/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain

import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaGeometry
import io.swagger.v3.oas.models.media.StringSchema
import spock.lang.Specification

class OpenApiSchemaDeriverSpec extends Specification {

    def 'a geometry parameter schema becomes a string schema with the geometry format'() {
        given:
        def deriver = new OpenApiSchemaDeriver()
        def schema = new ImmutableJsonSchemaGeometry.Builder().format("geometry-polygon").build()

        when:
        def openApi = schema.accept(deriver)

        then:
        openApi instanceof StringSchema
        openApi.getFormat() == "geometry-polygon"
    }
}
