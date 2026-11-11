/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain

import de.ii.xtraplatform.xtralink.domain.Jobs
import spock.lang.Specification

class OapJobSpec extends Specification {

    def "the inputs of a job are written and read as the object of the inputs, any name included"() {
        given:
        Map<String, Object> inputs = [document: [arazzo: "1.1.0"], inputs: [postcode: "3811LM"], count: 2]

        when:
        String json = Jobs.DEFAULT_MAPPER.writeValueAsString(OapJob.fromInputs(inputs))
        OapJob job = Jobs.DEFAULT_MAPPER.readValue(json, OapJob)

        then:
        Jobs.DEFAULT_MAPPER.readValue(json, Map) == inputs
        job.inputs == inputs
    }
}
