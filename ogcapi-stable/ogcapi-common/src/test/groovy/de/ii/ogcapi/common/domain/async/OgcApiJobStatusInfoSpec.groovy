/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async

import de.ii.ogcapi.common.domain.async.OgcApiJobStatus.EntityType
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtralink.jobs.Job
import de.ii.xtralink.jobs.JobProgress
import de.ii.xtraplatform.xtralink.domain.Jobs
import spock.lang.Specification

class OgcApiJobStatusInfoSpec extends Specification {

    def "the errors of a job are its message: #errors"() {
        when:
        OgcApiJobStatus statusInfo = OgcApiJobStatusInfo.of(job(Status.FAILED, errors))

        then:
        statusInfo.message == message

        where:
        errors                                   || message
        []                                       || Optional.empty()
        ["The transaction was rejected."]        || Optional.of("The transaction was rejected.")
        ["First error.", "Second error."]        || Optional.of("First error. Second error.")
    }

    private static Job job(Status status, List<String> errors) {
        Map<String, Object> context = Jobs.DEFAULT_MAPPER.convertValue(
                new JobContextAsync("api", EntityType.OGC_API_FEATURES, Optional.empty(), Optional.empty()), Jobs.MAP_TYPE)
        new Job("job-1", "feature-transaction", 0L, -1L, -1L, -1L, 1000, new JobProgress(0, 1, 0, null), status, errors,
                context, null, null, [:], [:], Optional.empty(), Optional.empty(), [], Optional.empty(),
                Optional.empty())
    }
}
