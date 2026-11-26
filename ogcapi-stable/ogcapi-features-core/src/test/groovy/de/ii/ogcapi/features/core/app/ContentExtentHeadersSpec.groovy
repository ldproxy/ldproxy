/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app

import de.ii.ogcapi.foundation.domain.ApiHeader
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.responses.ApiResponse
import spock.lang.Specification

class ContentExtentHeadersSpec extends Specification {

    io.swagger.v3.oas.models.headers.Header declaration(ApiHeader header) {
        def openApi = new OpenAPI().components(new Components())
        header.updateOpenApiDefinition(Stub(OgcApiDataV2), openApi, new ApiResponse())
        return openApi.getComponents().getHeaders().get(header.getId())
    }

    def 'the temporal extent is declared as an exploded object, as it is written'() {
        when:
        def declared = declaration(new HeaderContentTemporalExtent(Stub(SchemaValidator)))

        then:
        declared.getExplode()
        declared.getSchema().getType() == 'object'
    }

    def 'the bounding box is declared as a plain array'() {
        when:
        def declared = declaration(new HeaderContentBoundingBox(Stub(SchemaValidator)))

        then:
        declared.getExplode() == null
        declared.getSchema().getType() == 'array'
    }
}
