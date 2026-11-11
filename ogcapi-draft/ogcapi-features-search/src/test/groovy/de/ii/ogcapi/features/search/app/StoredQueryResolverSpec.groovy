/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.search.domain.ImmutableSearchJob
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.cql.app.CqlImpl

// A search job pushed without a request names a stored query and its parameter values. They are
// checked and resolved as for a GET request of /search/{queryId}.
class StoredQueryResolverSpec extends StoredQueryFixture {

    StoredQueryResolver resolver

    def setup() {
        resolver = new StoredQueryResolver(extensionRegistry, repository, Stub(SchemaValidator), new CqlImpl())
    }

    def 'a stored query with valid parameters is resolved, f selects the format'() {
        when:
        def resolved = resolver.resolveForJob(api, 'changes',
                ['f': 'xml', 'gebiet': 'POLYGON((0 0,1 0,1 1,0 0))', 'beginn': '2026-10-01T00:00:00Z'])

        then:
        resolved.getMediaType().parameter() == 'xml'
        resolved.getMediaType().type() == GML
        resolved.getQuery().getCollections() == ['ax_flurstueck']

        and: 'the request URI is that of the equivalent GET request'
        def uri = resolved.getRequestUri()
        uri.getPath() == '/demo/search/changes'
        uri.getQuery().split('&') as Set == ['f=xml', 'gebiet=POLYGON((0 0,1 0,1 1,0 0))', 'beginn=2026-10-01T00:00:00Z'] as Set
    }

    def 'an unknown parameter is rejected'() {
        when:
        resolver.resolveForJob(api, 'changes', ['f': 'xml', 'ende': '2026-10-25T00:00:00Z'])

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage().contains('ende')
        e.getMessage().contains('Valid parameters are: beginn, f, gebiet')
    }

    def 'an invalid value is rejected with the message of the parameter'() {
        when:
        resolver.resolveForJob(api, 'changes', ['f': 'xml', 'beginn': 'yesterday'])

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage().contains("Invalid value 'yesterday' for 'beginn'.")
    }

    def 'f is required'() {
        when:
        resolver.resolveForJob(api, 'changes', ['beginn': '2026-10-01T00:00:00Z'])

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage().contains("The parameter 'f' is required.")
    }

    def 'an unknown stored query is rejected'() {
        when:
        resolver.resolveForJob(api, 'unknown', ['f': 'xml'])

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage() == "The stored query 'unknown' does not exist in API 'demo'."
    }

    def 'without a format, the parameters are checked and resolved in the same way'() {
        when:
        def resolved = resolver.resolveParameters(api, 'changes', ['beginn': '2026-10-01T00:00:00Z'])

        then:
        resolved.getQuery().getCollections() == ['ax_flurstueck']
        resolved.getQueryParameterSet().getValues().getFirst('beginn') == '2026-10-01T00:00:00Z'

        when:
        resolver.resolveParameters(api, 'changes', ['ende': '2026-10-25T00:00:00Z'])

        then:
        def e = thrown(IllegalArgumentException)
        e.getMessage().contains('ende')

        when:
        resolver.resolveParameters(api, 'unknown', [:])

        then:
        def e2 = thrown(IllegalArgumentException)
        e2.getMessage() == "The stored query 'unknown' does not exist in API 'demo'."
    }

    def 'a job with a stored query id is a stored query job'() {
        when:
        def job = new ImmutableSearchJob.Builder()
                .apiId('demo')
                .queryId('changes')
                .putParameters('f', 'xml')
                .build()

        then:
        job.getIsStoredQuery()
        job.getQueryExpression() == null
        job.getMediaType() == null
    }
}
