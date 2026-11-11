/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app

import com.google.common.collect.ImmutableSet
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension
import de.ii.ogcapi.features.search.domain.ImmutableSearchJob
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression
import de.ii.ogcapi.features.search.domain.ImmutableStringOrParameter
import de.ii.ogcapi.features.search.domain.StoredQueryExpression
import de.ii.ogcapi.features.search.domain.StoredQueryRepository
import de.ii.ogcapi.foundation.domain.ApiMediaType
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.xtraplatform.blobs.domain.ResourceStore
import de.ii.xtraplatform.cql.app.CqlImpl
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.StringSchema
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

/**
 * An API {@code demo} with the stored query {@code changes} and the parameters {@code f}, {@code gebiet}
 * and {@code beginn}, as the endpoint of the stored queries defines them.
 */
abstract class StoredQueryFixture extends Specification {

    static final MediaType GML = new MediaType('application', 'gml+xml')

    StoredQueryRepository repository = Stub()
    OgcApiDataV2 apiData = Stub()
    OgcApi api = Stub()
    ExtensionRegistry extensionRegistry

    def setup() {
        apiData.getId() >> 'demo'
        api.getData() >> apiData
        api.getId() >> 'demo'
        api.getUri() >> URI.create('http://localhost:7080/demo')

        repository.getAll(_) >> [query('changes')]
        repository.exists(_, 'changes') >> true
        repository.get(_, 'changes') >> query('changes')

        List<OgcApiQueryParameter> parameters = [parameter('f'), parameter('gebiet'), parameter('beginn')]
        ExtensionRegistry definitionRegistry = registry(parameters)
        EndpointStoredQuery real = new EndpointStoredQuery(
                definitionRegistry, null, repository, null, null, null, null, Stub(ResourceStore))
        def definition = real.computeDefinition(api.getData())

        EndpointStoredQuery endpoint = Stub()
        endpoint.getDefinition(_) >> definition
        endpoint.getMediaTypes(_, '/changes', 'GET') >> ImmutableSet.of(apiMediaType(GML, "xml"), apiMediaType(MediaType.APPLICATION_JSON_TYPE, "json"))

        extensionRegistry = Stub()
        extensionRegistry.getExtensionsForType(EndpointStoredQuery) >> [endpoint]
    }

    protected OgcApiQueryParameter parameter(String name) {
        OgcApiQueryParameter parameter = Stub()
        parameter.getName() >> name
        parameter.getId() >> name
        parameter.getDescription() >> name
        parameter.isApplicable(_, _, _) >> true
        parameter.getSchema(_, _) >> new StringSchema()
        parameter.getRequired(_, _) >> false
        parameter.validate(_, _, _) >> { apiData, collectionId, List<String> values ->
            values == ['yesterday'] ? Optional.of("Invalid value 'yesterday' for '${name}'.".toString()) : Optional.empty()
        }
        return parameter
    }

    protected static StoredQueryExpression query(String id) {
        return new ImmutableStoredQueryExpression.Builder()
                .id(id)
                .addCollections(new ImmutableStringOrParameter.Builder().value('ax_flurstueck').build())
                .supportPaging(false)
                .build()
    }

    protected static ApiMediaType apiMediaType(MediaType type, String parameter) {
        return new ImmutableApiMediaType.Builder().type(type).label(parameter).parameter(parameter).build()
    }

    protected ExtensionRegistry registry(List<OgcApiQueryParameter> parameters) {
        FeatureFormatExtension gml = format(GML, 'xml')
        ExtensionRegistry registry = Stub()
        registry.getExtensionsForType(FeatureFormatExtension) >> [gml]
        registry.getExtensionsForType(OgcApiQueryParameter) >> parameters
        registry.getExtensionsForType(_) >> []
        return registry
    }

    protected FeatureFormatExtension format(MediaType mediaType, String parameter) {
        ApiMediaTypeContent content = new ImmutableApiMediaTypeContent.Builder()
                .ogcApiMediaType(apiMediaType(mediaType, parameter))
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
