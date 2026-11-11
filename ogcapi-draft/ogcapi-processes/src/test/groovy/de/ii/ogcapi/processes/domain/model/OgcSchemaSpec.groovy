/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model

import de.ii.xtraplatform.xtralink.domain.Jobs
import spock.lang.Specification

class OgcSchemaSpec extends Specification {

    def "a schema is written as it was configured: #json"() {
        when:
        OgcSchema schema = Jobs.DEFAULT_MAPPER.readValue(json, OgcSchema)

        then:
        Jobs.DEFAULT_MAPPER.readValue(Jobs.DEFAULT_MAPPER.writeValueAsString(schema), Map) == Jobs.DEFAULT_MAPPER.readValue(json, Map)

        where:
        json << [
                '{"type":"integer","maximum":1000}',
                '{"type":"number","minimum":0.5,"exclusiveMaximum":10}',
                '{"type":"string"}',
                '{"type":"array","minItems":1,"maxItems":3}'
        ]
    }
}
