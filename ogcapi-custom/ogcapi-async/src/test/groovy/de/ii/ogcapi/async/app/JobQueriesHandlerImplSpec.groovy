/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app

import de.ii.ogcapi.async.app.format.JobResultsFormatAny
import de.ii.ogcapi.async.domain.ImmutableQueryInputResults
import de.ii.ogcapi.async.domain.ImmutableQueryInputResultsSpecific
import de.ii.ogcapi.async.domain.ImmutableQueryInputResultsSpecificN
import de.ii.ogcapi.async.domain.JobQueriesHandler.Query
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.I18n
import de.ii.ogcapi.foundation.domain.QueryParameterSet
import de.ii.ogcapi.foundation.domain.TypedClientErrorException
import de.ii.xtralink.jobs.Identifiers.Status
import jakarta.ws.rs.NotAcceptableException
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.StreamingOutput
import org.glassfish.jersey.internal.MapPropertiesDelegate
import org.glassfish.jersey.server.ContainerRequest
import spock.lang.Specification

class JobQueriesHandlerImplSpec extends Specification {

    static final MediaType GEO_JSON = new MediaType("application", "geo+json")
    static final MediaType PROBLEM_JSON = new MediaType("application", "problem+json")

    AsyncJobs jobs = Stub()
    JobQueriesHandlerImpl handler = new JobQueriesHandlerImpl(Stub(I18n) { getLanguages() >> ([] as Set) }, jobs)

    def setup() {
        jobs.getStatusInfo("job-1") >> Optional.of(Stub(OgcApiJobStatus))
    }

    def "a result document of type application/geo+json is returned for Accept '#accept'"() {
        given:
        jobs.getResultDocument("job-1") >> Optional.of(ImmutableResultDocument.of(GEO_JSON, Stub(StreamingOutput)))

        when:
        Response response = results(accept)

        then:
        response.status == 200
        response.mediaType == GEO_JSON

        where:
        accept << [
                "application/geo+json",
                "application/json",
                "*/*",
                "text/csv, application/geo+json;q=0.9",
        ]
    }

    def "a result document, the only representation, is returned also for Accept '#accept'"() {
        given:
        jobs.getResultDocument("job-1") >> Optional.of(ImmutableResultDocument.of(GEO_JSON, Stub(StreamingOutput)))

        when:
        Response response = results(accept)

        then:
        response.status == 200
        response.mediaType == GEO_JSON

        where:
        accept << ["image/png", "text/html"]
    }

    def "the reason of a failure is returned with its status for Accept '#accept'"() {
        given:
        jobs.getResultDocument("job-1") >> Optional.of(ImmutableResultDocument.of(MediaType.APPLICATION_JSON_TYPE, Stub(StreamingOutput)).withStatus(422))

        when:
        Response response = results(accept)

        then:
        response.status == 422
        response.mediaType == MediaType.APPLICATION_JSON_TYPE

        where:
        accept << ["application/json", "image/png"]
    }

    def "the file name of a result document has its extension: #extension"() {
        given:
        jobs.getResultDocument("job-1") >> Optional.of(ImmutableResultDocument.of(GEO_JSON, Stub(StreamingOutput)).withFileExtension(extension))

        when:
        Response response = results("application/geo+json")

        then:
        response.getHeaderString("Content-Disposition") == disposition

        where:
        extension          || disposition
        Optional.of("json") || 'inline; filename="job-1.json"'
        Optional.empty()    || 'inline; filename="job-1"'
    }

    def "named outputs are not acceptable when only the catch-all format matched"() {
        given:
        jobs.getResultDocument("job-1") >> Optional.empty()
        jobs.getResults("job-1") >> [value: 1]

        when:
        results("image/png")

        then:
        thrown(NotAcceptableException)
    }

    def "the result of each output of a failed job is the reason of the failure, value #index"() {
        given:
        jobs.getStatusInfo("job-2") >> Optional.of(Stub(OgcApiJobStatus) { getStatus() >> Status.FAILED })
        jobs.getResultDocument("job-2") >> Optional.of(ImmutableResultDocument.of(PROBLEM_JSON, Stub(StreamingOutput)).withStatus(422))

        when:
        Response response = handler.handle(query, input, requestContext("application/json", [:]))

        then:
        response.status == 422
        response.mediaType == PROBLEM_JSON

        where:
        query                   | input                                                                                  | index
        Query.RESULTS_SPECIFIC   | new ImmutableQueryInputResultsSpecific.Builder().jobId("job-2").outputId("value").build()            | "-"
        Query.RESULTS_SPECIFIC_N | new ImmutableQueryInputResultsSpecificN.Builder().jobId("job-2").outputId("value").indexN(0).build() | "0"
    }

    def "an output that is not among the results is an invalid value of outputs"() {
        given:
        jobs.getResultDocument("job-1") >> Optional.empty()
        jobs.getResults("job-1") >> [value: 1]

        when:
        handler.handle(Query.RESULTS, new ImmutableQueryInputResults.Builder().jobId("job-1").build(),
                requestContext("application/json", [outputs: ["other"]]))

        then:
        TypedClientErrorException e = thrown()
        e.response.status == 400
        e.type == "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/invalid-query-parameter-value"
    }

    private ApiRequestContext requestContext(String accept, Map<String, Object> typedValues) {
        QueryParameterSet parameters = Stub() {
            getTypedValues() >> typedValues
        }
        Stub(ApiRequestContext) {
            getRequest() >> Optional.of(request(accept))
            getMediaType() >> JobResultsFormatAny.MEDIA_TYPE
            getAlternateMediaTypes() >> []
            getLanguage() >> Optional.empty()
            getQueryParameterSet() >> parameters
        }
    }

    private Response results(String accept) {
        ApiRequestContext requestContext = Stub() {
            getRequest() >> Optional.of(request(accept))
            getMediaType() >> JobResultsFormatAny.MEDIA_TYPE
            getAlternateMediaTypes() >> []
            getLanguage() >> Optional.empty()
        }
        handler.handle(Query.RESULTS, new ImmutableQueryInputResults.Builder().jobId("job-1").build(), requestContext)
    }

    private static ContainerRequest request(String accept) {
        def request = new ContainerRequest(
                URI.create("http://localhost/"),
                URI.create("http://localhost/api/jobs/job-1/results"),
                "GET",
                null,
                new MapPropertiesDelegate(),
                null)
        request.header(HttpHeaders.ACCEPT, accept)
        request
    }
}
