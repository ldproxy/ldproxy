/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.api

import com.google.common.collect.ImmutableList
import de.ii.ogcapi.foundation.domain.ApiOperation
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.PermissionGroup
import jakarta.ws.rs.core.MediaType
import spock.lang.Specification

class TileSetLinkSpec extends Specification {

    def 'a tile of the dataset is linked from the first tile in the limits of the tile set'() {
        when:
        def operation = EndpointTileMixin.withLinkFromTileSet(
                tileOperation('dataset.vector.getTile'), '/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}')

        then:
        operation.getLinkedFrom().size() == 1
        def link = operation.getLinkedFrom().get(0)
        link.getOperationId() == 'dataset.vector.getTileSet'
        link.getParameters() == [
                'tileMatrixSetId': '$request.path.tileMatrixSetId',
                'tileMatrix'     : '$response.body#/tileMatrixSetLimits/0/tileMatrix',
                'tileRow'        : '$response.body#/tileMatrixSetLimits/0/minTileRow',
                'tileCol'        : '$response.body#/tileMatrixSetLimits/0/minTileCol']
    }

    def 'the collection and the style of a tile are taken from the request of the tile set'() {
        when:
        def operation = EndpointTileMixin.withLinkFromTileSet(
                tileOperation('collection.style.map.getTile'),
                '/collections/{collectionId}/styles/{styleId}/map/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}')

        then:
        def link = operation.getLinkedFrom().get(0)
        link.getOperationId() == 'collection.style.map.getTileSet'
        link.getParameters()['collectionId'] == '$request.path.collectionId'
        link.getParameters()['styleId'] == '$request.path.styleId'
    }

    def 'a listed collection is not taken from the request'() {
        when:
        def operation = EndpointTileMixin.withLinkFromTileSet(
                tileOperation('buildings.collection.vector.getTile'),
                '/collections/buildings/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}')

        then:
        def link = operation.getLinkedFrom().get(0)
        link.getOperationId() == 'buildings.collection.vector.getTileSet'
        !link.getParameters().containsKey('collectionId')
    }

    def 'an operation that does not fetch a tile is not changed'() {
        given:
        def operation = tileOperation('dataset.vector.getTileSet')

        expect:
        EndpointTileMixin.withLinkFromTileSet(operation, '/tiles/{tileMatrixSetId}').is(operation)
    }

    private static ApiOperation tileOperation(String operationId) {
        ApiOperation.getResource(
                new ImmutableOgcApiDataV2.Builder().id('buildings').build(),
                '/tiles',
                false,
                ImmutableList.of(),
                ImmutableList.of(),
                Map.of(MediaType.APPLICATION_JSON_TYPE, new ImmutableApiMediaTypeContent.Builder()
                        .schema(new io.swagger.v3.oas.models.media.ObjectSchema())
                        .schemaRef('#/components/schemas/Tile')
                        .ogcApiMediaType(new ImmutableApiMediaType.Builder()
                                .type(MediaType.APPLICATION_JSON_TYPE)
                                .build())
                        .build()),
                'fetch a tile',
                Optional.empty(),
                Optional.empty(),
                operationId,
                PermissionGroup.of(PermissionGroup.Base.READ, 'tiles', 'read tiles'),
                ImmutableList.of('Access tiles'),
                Optional.empty(),
                Optional.empty()).get()
    }
}
