/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app

import com.fasterxml.jackson.databind.ObjectMapper
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions
import spock.lang.Specification

class JobControlOptionsSpec extends Specification {

    ObjectMapper mapper = new ObjectMapper()

    def "#option is encoded as '#value'"() {
        expect:
        mapper.writeValueAsString(option) == "\"${value}\""

        where:
        option                          || value
        JobControlOptions.SYNC_EXECUTE  || "sync-execute"
        JobControlOptions.ASYNC_EXECUTE || "async-execute"
        JobControlOptions.DISMISS       || "dismiss"
    }

    def "'#value' is decoded as #option"() {
        expect:
        mapper.readValue("\"${value}\"", JobControlOptions) == option

        where:
        value           || option
        "sync-execute"  || JobControlOptions.SYNC_EXECUTE
        "SYNC_EXECUTE"  || JobControlOptions.SYNC_EXECUTE
        "async-execute" || JobControlOptions.ASYNC_EXECUTE
        "ASYNC_EXECUTE" || JobControlOptions.ASYNC_EXECUTE
    }
}
