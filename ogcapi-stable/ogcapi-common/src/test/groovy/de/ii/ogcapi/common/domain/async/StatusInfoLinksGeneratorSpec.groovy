/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async

import de.ii.ogcapi.foundation.domain.ApiMediaType
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.I18n
import de.ii.ogcapi.foundation.domain.Link
import de.ii.xtralink.jobs.Identifiers.Status
import de.ii.xtraplatform.web.domain.URICustomizer
import spock.lang.Specification

class StatusInfoLinksGeneratorSpec extends Specification {

    static final String RESULTS = "https://www.opengis.net/def/rel/ogc/1.0/results"

    I18n i18n = Stub() {
        get(_, _) >> "title"
    }

    def "the status info retrieved from the job links itself with f and its other formats as #mediaType.label()"() {
        given:
        ApiRequestContext requestContext = Stub() {
            getUriCustomizer() >> new URICustomizer("http://localhost/api/jobs/job-1?f=${mediaType.parameter()}")
            getMediaType() >> mediaType
            getAlternateMediaTypes() >> [alternate]
            getLanguage() >> Optional.empty()
        }

        when:
        List<Link> links = new StatusInfoLinksGenerator().generateLinks(requestContext, i18n, statusInfo(Status.RUNNING, null))

        then:
        links.collect { [it.rel, it.type, it.href] } == [
                ["self", mediaType.type().toString(), "http://localhost/api/jobs/job-1?f=${mediaType.parameter()}".toString()],
                ["alternate", alternate.type().toString(), "http://localhost/api/jobs/job-1?f=${alternate.parameter()}".toString()]
        ]

        where:
        mediaType                     | alternate
        ApiMediaType.JSON_MEDIA_TYPE  | ApiMediaType.HTML_MEDIA_TYPE
        ApiMediaType.HTML_MEDIA_TYPE  | ApiMediaType.JSON_MEDIA_TYPE
    }

    def "outside of a request the job is linked to monitor, once, without a type and a format"() {
        when:
        List<Link> links = monitorLinks(statusInfo(Status.RUNNING, null))

        then:
        links.collect { [it.rel, it.type, it.href] } == [
                ["monitor", null, "http://localhost/api/jobs/job-1"]
        ]
    }

    def "the results link of a successful job has the type #expectedType for result media type #resultMediaType"() {
        when:
        List<Link> links = monitorLinks(statusInfo(Status.SUCCESSFUL, resultMediaType))

        then:
        resultsLink(links).map(Link::getType) == Optional.of(expectedType)
        resultsLink(links).map(Link::getHref) == Optional.of("http://localhost/api/jobs/job-1/results")

        where:
        resultMediaType        || expectedType
        "application/geo+json" || "application/geo+json"
        "text/csv"             || "text/csv"
        null                   || "application/json"
    }

    def "a #status job has no results link and no other links than to monitor it"() {
        when:
        List<Link> links = monitorLinks(statusInfo(status, "application/geo+json"))

        then:
        resultsLink(links).isEmpty()
        links*.rel == ["monitor"]

        where:
        status << [Status.ACCEPTED, Status.RUNNING, Status.DISMISSED]
    }

    def "the results link of a failed job has the type #expectedType for result media type #resultMediaType"() {
        when:
        List<Link> links = monitorLinks(statusInfo(Status.FAILED, resultMediaType))

        then:
        resultsLink(links).map(Link::getType) == Optional.of(expectedType)

        where:
        resultMediaType    || expectedType
        "application/json" || "application/json"
        null               || "application/problem+json"
    }

    private List<Link> monitorLinks(OgcApiJobStatus statusInfo) {
        new StatusInfoLinksGenerator().generateMonitorLinks("http://localhost/api", i18n, statusInfo)
    }

    private static Optional<Link> resultsLink(List<Link> links) {
        Optional.ofNullable(links.find { it.rel == RESULTS })
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
