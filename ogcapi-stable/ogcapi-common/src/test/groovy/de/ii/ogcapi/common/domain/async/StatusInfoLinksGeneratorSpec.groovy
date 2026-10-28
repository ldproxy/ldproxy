/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async

import de.ii.ogcapi.foundation.domain.I18n
import de.ii.ogcapi.foundation.domain.Link
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtraplatform.web.domain.URICustomizer
import spock.lang.Specification

class StatusInfoLinksGeneratorSpec extends Specification {

    I18n i18n = Stub() {
        get(_, _) >> "title"
    }

    def "the results link of a successful job has the type #expectedType for result media type #resultMediaType"() {
        when:
        List<Link> links = links(statusInfo(Status.SUCCESSFUL, resultMediaType))

        then:
        resultsLink(links).map(Link::getType) == Optional.of(expectedType)
        resultsLink(links).map(Link::getHref) == Optional.of("http://localhost/api/jobs/job-1/results")

        where:
        resultMediaType        || expectedType
        "application/geo+json" || "application/geo+json"
        "text/csv"             || "text/csv"
        null                   || "application/json"
    }

    def "a #status job has no results link"() {
        when:
        List<Link> links = links(statusInfo(status, "application/geo+json"))

        then:
        resultsLink(links).isEmpty()
        links*.rel == ["self", "delete"]

        where:
        status << [Status.ACCEPTED, Status.RUNNING, Status.FAILED, Status.DISMISSED]
    }

    private List<Link> links(OgcApiJobStatus statusInfo) {
        new StatusInfoLinksGenerator().generateLinks(
                new URICustomizer("http://localhost/api/jobs/job-1"), i18n, Optional.empty(), statusInfo, 0)
    }

    private static Optional<Link> resultsLink(List<Link> links) {
        Optional.ofNullable(links.find { it.rel == "https://www.opengis.net/def/rel/ogc/1.0/results" })
    }

    private static OgcApiJobStatus statusInfo(Status status, String resultMediaType) {
        new ImmutableOgcApiJobStatusInfo.Builder()
                .id("job-1")
                .status(status)
                .processingEntityType(OgcApiJobStatus.EntityType.OGC_API_FEATURES)
                .resultMediaType(Optional.ofNullable(resultMediaType))
                .build()
    }
}
