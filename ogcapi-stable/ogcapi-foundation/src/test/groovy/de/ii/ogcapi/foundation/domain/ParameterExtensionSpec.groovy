/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain

import io.swagger.v3.oas.models.media.Schema
import spock.lang.Specification

/**
 * Locks the null-schema guard in {@link ParameterExtension#validateSchema}: a parameter whose
 * schema is unavailable must skip schema validation instead of failing the request with a
 * NullPointerException.
 */
class ParameterExtensionSpec extends Specification {

    def 'a parameter without a schema skips schema validation instead of failing'() {
        given: 'a parameter whose schema is unavailable'
        def parameter = new ParameterExtension() {
            @Override
            String getName() { 'test' }

            @Override
            String getDescription() { 'test parameter' }

            @Override
            Schema<?> getSchema(OgcApiDataV2 apiData) { null }

            @Override
            SchemaValidator getSchemaValidator() { null }
        }

        when: 'a value is validated'
        def result = parameter.validate(null, Optional.empty(), ['foo'])

        then: 'validation reports no error and does not throw'
        result == Optional.empty()
    }
}
