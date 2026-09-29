/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.HttpMethods
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ImmutableApiOperation
import de.ii.ogcapi.foundation.domain.PermissionGroup
import io.swagger.v3.oas.models.media.ObjectSchema
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class OptionsAcceptHeadersSpec extends Specification {

    static final MediaType GEO_JSON = new MediaType('application', 'geo+json')
    static final MediaType GML = new MediaType('application', 'gml+xml')
    static final MediaType MERGE_PATCH = new MediaType('application', 'merge-patch+json')

    def 'the media types of a request body become the header value'() {
        given: 'a POST operation that accepts two feature formats'
        Optional<ApiOperation> operation = operation(HttpMethods.POST, [GEO_JSON, GML])

        expect:
        OptionsEndpoint.acceptedMediaTypes(operation)
                == Optional.of('application/geo+json, application/gml+xml')
    }

    def 'the media types are sorted, so the value does not depend on the map order'() {
        given: 'the same two formats declared in either order'
        Optional<ApiOperation> mergePatchFirst = operation(HttpMethods.PATCH, [MERGE_PATCH, GEO_JSON])
        Optional<ApiOperation> geoJsonFirst = operation(HttpMethods.PATCH, [GEO_JSON, MERGE_PATCH])

        expect: 'the same, sorted value for both'
        OptionsEndpoint.acceptedMediaTypes(mergePatchFirst)
                == Optional.of('application/geo+json, application/merge-patch+json')
        OptionsEndpoint.acceptedMediaTypes(geoJsonFirst)
                == OptionsEndpoint.acceptedMediaTypes(mergePatchFirst)
    }

    def 'a method that the resource does not support has no header'() {
        expect:
        OptionsEndpoint.acceptedMediaTypes(Optional.empty()) == Optional.empty()
    }

    def 'an operation without a request body has no header'() {
        given: 'a DELETE operation, which has no request body'
        Optional<ApiOperation> operation = operation(HttpMethods.DELETE, [])

        expect:
        OptionsEndpoint.acceptedMediaTypes(operation) == Optional.empty()
    }

    def 'a single media type is not followed by a separator'() {
        given:
        Optional<ApiOperation> operation = operation(HttpMethods.POST, [GEO_JSON])

        expect:
        OptionsEndpoint.acceptedMediaTypes(operation) == Optional.of('application/geo+json')
    }

    private static Optional<ApiOperation> operation(HttpMethods method, List<MediaType> mediaTypes) {
        Map<MediaType, ApiMediaTypeContent> requestContent = new LinkedHashMap<>()
        mediaTypes.each { requestContent.put(it, content(it)) }

        // DELETE has no request body, so build it without one
        if (requestContent.isEmpty()) {
            return Optional.of(new ImmutableApiOperation.Builder()
                    .summary('delete a feature')
                    .operationId('deleteItem')
                    .permissionGroup(PermissionGroup.of(PermissionGroup.Base.WRITE, 'data', 'mutate data'))
                    .build())
        }

        ApiOperation.of(
                '/collections/buildings/items',
                method,
                requestContent,
                ImmutableList.of(),
                ImmutableList.of(),
                'mutate a feature',
                Optional.empty(),
                Optional.empty(),
                'mutateItem',
                PermissionGroup.of(PermissionGroup.Base.WRITE, 'data', 'mutate data'),
                ImmutableList.of('Mutate data'),
                Optional.empty(),
                Optional.empty(),
                false)
    }

    private static ApiMediaTypeContent content(MediaType mediaType) {
        new ImmutableApiMediaTypeContent.Builder()
                .schema(new ObjectSchema())
                .schemaRef('#/components/schemas/Feature')
                .ogcApiMediaType(new ImmutableApiMediaType.Builder().type(mediaType).build())
                .build()
    }
}
