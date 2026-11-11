/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression
import de.ii.ogcapi.features.search.domain.ImmutableStringOrParameter
import de.ii.ogcapi.features.search.domain.SearchConfiguration
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.Query
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler.QueryInputQuery
import de.ii.ogcapi.features.search.domain.StoredQueryRepository
import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.QueryParameterSet
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.cql.app.CqlImpl
import de.ii.xtraplatform.crs.domain.OgcCrs
import de.ii.xtraplatform.features.domain.FeatureProvider
import de.ii.xtraplatform.web.domain.URICustomizer
import de.ii.xtraplatform.xtralink.domain.Jobs
import jakarta.ws.rs.NotAcceptableException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import spock.lang.Specification

// A stored query executed as a job is validated before the job is created, as a synchronous request
// and an ad-hoc query, so an invalid request is rejected with its client error and not accepted as
// a job that fails.
class StoredQueryAsyncSpec extends Specification {

    SearchQueriesHandler queryHandler = Mock()
    Jobs jobs = Mock()
    OgcApi api = Stub()
    ApiRequestContext requestContext = Stub()
    EndpointStoredQuery endpoint

    def setup() {
        FeaturesCoreConfiguration coreConfiguration = Stub()
        coreConfiguration.isEnabled() >> true
        coreConfiguration.getItemType() >> Optional.empty()
        coreConfiguration.getDefaultEpsgCrs() >> OgcCrs.CRS84
        SearchConfiguration searchConfiguration = Stub()
        searchConfiguration.isAsync() >> true

        OgcApiDataV2 apiData = Stub()
        apiData.getId() >> 'demo'
        apiData.getExtension(FeaturesCoreConfiguration) >> Optional.of(coreConfiguration)
        apiData.getExtension(SearchConfiguration) >> Optional.of(searchConfiguration)
        apiData.getExtension(_) >> Optional.empty()
        api.getData() >> apiData

        requestContext.getApi() >> api
        requestContext.getQueryParameterSet() >> QueryParameterSet.of()
        requestContext.getUriCustomizer() >> new URICustomizer('http://localhost/demo/search/changes')
        requestContext.getMediaType() >> new ImmutableApiMediaType.Builder()
                .type(new MediaType('application', 'geo+json'))
                .label('GeoJSON')
                .parameter('json')
                .build()
        requestContext.getLanguage() >> Optional.empty()

        StoredQueryRepository repository = Stub()
        repository.get(apiData, 'changes') >> new ImmutableStoredQueryExpression.Builder()
                .id('changes')
                .addCollections(new ImmutableStringOrParameter.Builder().value('buildings').build())
                .build()
        FeaturesCoreProviders providers = Stub()
        providers.getFeatureProviderOrThrow(apiData) >> Stub(FeatureProvider)
        ExtensionRegistry registry = Stub()
        registry.getExtensionsForType(_) >> []

        endpoint = new EndpointStoredQuery(registry, providers, repository, queryHandler,
                Stub(SchemaValidator), new CqlImpl(), jobs, Stub(ResourceStore))
    }

    def 'an invalid stored query is rejected before a job is created'() {
        when:
        endpoint.getStoredQuery('changes', ['respond-async'], api, requestContext)

        then:
        1 * queryHandler.validate(_, requestContext) >> {
            throw new NotAcceptableException('The requested media type is not supported.')
        }
        0 * jobs._
        thrown(NotAcceptableException)
    }

    def 'a valid stored query is validated as a stored query before the job is created'() {
        when:
        endpoint.getStoredQuery('changes', ['respond-async'], api, requestContext)

        then:
        1 * queryHandler.validate({ QueryInputQuery input -> input.isStoredQuery() }, requestContext)

        then: 'the job is created; the response is not of interest here'
        1 * jobs.push(_) >> { throw new IllegalStateException('job created') }
        IllegalStateException e = thrown()
        e.message == 'job created'
    }

    def 'a synchronous request is not validated separately, the query is executed'() {
        given:
        Response response = Response.ok().build()

        when:
        Response result = endpoint.getStoredQuery('changes', [], api, requestContext)

        then:
        0 * queryHandler.validate(*_)
        1 * queryHandler.handle(Query.QUERY, _, requestContext) >> response
        0 * jobs._
        result == response
    }
}
