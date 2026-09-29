/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import de.ii.ogcapi.features.core.domain.FeatureFormatExtension
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression
import de.ii.ogcapi.features.search.domain.ImmutableStringOrParameter
import de.ii.ogcapi.features.search.domain.StoredQueryExpression
import de.ii.ogcapi.features.search.domain.StoredQueryRepository
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

// The operations of a stored query depend on paging: without paging, the parameters may also be
// URL-encoded in a POST body (the next link of a paged response would require a URI that includes
// the parameters), and there is no offset (all features are returned, an offset would be ignored).
class StoredQueryOperationsSpec extends Specification {

    static final MediaType GEO_JSON = new MediaType('application', 'geo+json')

    def 'POST is offered for stored queries without paging only'() {
        given: 'a stored query with paging and one without'
        StoredQueryRepository repository = Stub()
        repository.getAll(_) >> [query('paged', true), query('single-shot', false), query('default', null)]
        EndpointStoredQuery endpoint = new EndpointStoredQuery(
                registry(), null, repository, null, null, null)

        when:
        ApiEndpointDefinition definition = endpoint.computeDefinition(Stub(OgcApiDataV2))

        then: 'all can be executed with GET'
        ['paged', 'single-shot', 'default'].every { operations(definition, it).containsKey('GET') }

        and: 'only those without paging with POST'
        !operations(definition, 'paged').containsKey('POST')
        operations(definition, 'single-shot').containsKey('POST')
        operations(definition, 'default').containsKey('POST')
    }

    def 'the POST operation accepts a form and does not offer HTML'() {
        given:
        StoredQueryRepository repository = Stub()
        repository.getAll(_) >> [query('single-shot', false)]
        EndpointStoredQuery endpoint = new EndpointStoredQuery(
                registry(), null, repository, null, null, null)

        when:
        ApiEndpointDefinition definition = endpoint.computeDefinition(Stub(OgcApiDataV2))
        ApiOperation get = operations(definition, 'single-shot').get('GET')
        ApiOperation post = operations(definition, 'single-shot').get('POST')

        then:
        post.hasFormRequestBody()
        post.getSuccess().orElseThrow().getContent().keySet() == [GEO_JSON] as Set
        get.getSuccess().orElseThrow().getContent().keySet() == [GEO_JSON, MediaType.TEXT_HTML_TYPE] as Set
        post.getOperationId() != get.getOperationId()
    }

    def 'offset is a parameter of stored queries with paging only'() {
        given: 'a stored query with paging and one without'
        StoredQueryRepository repository = Stub()
        repository.getAll(_) >> [query('paged', true), query('single-shot', false), query('default', null)]
        EndpointStoredQuery endpoint = new EndpointStoredQuery(
                registry([parameter(QueryParameterOffsetStoredQuery, 'offset'),
                          parameter(OgcApiQueryParameter, 'pretty')]),
                null, repository, null, null, null)

        when:
        ApiEndpointDefinition definition = endpoint.computeDefinition(Stub(OgcApiDataV2))

        then:
        parameterNames(definition, 'paged', 'GET') == ['offset', 'pretty']
        parameterNames(definition, 'single-shot', 'GET') == ['pretty']
        parameterNames(definition, 'default', 'GET') == ['pretty']

        and: 'the form of the POST request has the same parameters as the GET request'
        formParameterNames(definition, 'single-shot') == ['pretty'] as Set
    }

    private static List<String> parameterNames(ApiEndpointDefinition definition, String queryId, String method) {
        return operations(definition, queryId).get(method).getQueryParameters().collect { it.getName() }
    }

    private static Set<String> formParameterNames(ApiEndpointDefinition definition, String queryId) {
        return operations(definition, queryId).get('POST').getRequestBody().orElseThrow()
                .getContent().get(MediaType.APPLICATION_FORM_URLENCODED_TYPE).getSchema()
                .getProperties().keySet()
    }

    private <T extends OgcApiQueryParameter> T parameter(Class<T> type, String name) {
        T parameter = Stub(type)
        parameter.getName() >> name
        parameter.getDescription() >> name
        parameter.isApplicable(_, _, _) >> true
        parameter.getSchema(_, _) >> new IntegerSchema()
        parameter.getRequired(_, _) >> false
        return parameter
    }

    private static Map<String, ApiOperation> operations(ApiEndpointDefinition definition, String queryId) {
        return definition.getResources().get('/search/' + queryId).getOperations()
    }

    private static StoredQueryExpression query(String id, Boolean supportPaging) {
        def builder = new ImmutableStoredQueryExpression.Builder()
                .id(id)
                .addCollections(new ImmutableStringOrParameter.Builder().value('ax_test').build())
        if (supportPaging != null) {
            builder.supportPaging(supportPaging)
        }
        return builder.build()
    }

    private ExtensionRegistry registry(List<OgcApiQueryParameter> parameters = []) {
        FeatureFormatExtension geoJson = format(GEO_JSON, 'json')
        FeatureFormatExtension html = format(MediaType.TEXT_HTML_TYPE, 'html')
        ExtensionRegistry registry = Stub()
        registry.getExtensionsForType(FeatureFormatExtension) >> [geoJson, html]
        registry.getExtensionsForType(OgcApiQueryParameter) >> parameters
        registry.getExtensionsForType(_) >> []
        return registry
    }

    private FeatureFormatExtension format(MediaType mediaType, String parameter) {
        ApiMediaTypeContent content = new ImmutableApiMediaTypeContent.Builder()
                .ogcApiMediaType(new ImmutableApiMediaType.Builder()
                        .type(mediaType)
                        .label(parameter)
                        .parameter(parameter)
                        .build())
                .schema(new ObjectSchema())
                .schemaRef('#/components/schemas/' + parameter)
                .build()
        FeatureFormatExtension format = Stub()
        format.supportsHeterogeneousFeatureCollections() >> true
        format.isEnabledForApi(_) >> true
        format.getContent() >> content
        return format
    }
}
