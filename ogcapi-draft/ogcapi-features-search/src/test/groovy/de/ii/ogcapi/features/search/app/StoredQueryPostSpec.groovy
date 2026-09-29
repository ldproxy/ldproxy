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
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

// A stored query can be executed with the parameters URL-encoded in a POST body, but only without
// paging: the next link of a paged response would require a URI that includes the parameters.
class StoredQueryPostSpec extends Specification {

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

    private ExtensionRegistry registry() {
        FeatureFormatExtension geoJson = format(GEO_JSON, 'json')
        FeatureFormatExtension html = format(MediaType.TEXT_HTML_TYPE, 'html')
        ExtensionRegistry registry = Stub()
        registry.getExtensionsForType(FeatureFormatExtension) >> [geoJson, html]
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
