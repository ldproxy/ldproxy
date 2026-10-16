/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app

import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.domain.SchemaValidator
import de.ii.ogcapi.tiles.domain.TilesProviders
import de.ii.xtraplatform.tiles.domain.TilesetMetadata
import spock.lang.Specification

class PathParameterStyleIdSpec extends Specification {

    OgcApiDataV2 apiData = new ImmutableOgcApiDataV2.Builder()
            .id('roads')
            .putCollections('nodes', new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                    .id('nodes')
                    .label('nodes')
                    .build())
            .build()

    def 'only styles with map tiles of the dataset or of a collection are values'() {
        given: 'the styles asb and topo have map tiles for the dataset, night for a collection'
        TilesProviders tilesProviders = Stub()
        tilesProviders.getRasterTilesetMetadata(_ as OgcApiDataV2) >> ['topo': Stub(TilesetMetadata), 'asb': Stub(TilesetMetadata)]
        tilesProviders.getRasterTilesetMetadata(_ as OgcApiDataV2, _ as FeatureTypeConfigurationOgcApi) >> ['night': Stub(TilesetMetadata), 'asb': Stub(TilesetMetadata)]
        def parameter = new PathParameterStyleId(Stub(SchemaValidator), tilesProviders)

        expect:
        parameter.getValues(apiData) == ['asb', 'night', 'topo']
        parameter.getSchema(apiData).getEnum() == ['asb', 'night', 'topo']
    }

    def 'the values are determined again, as long as no style with map tiles is known'() {
        given: 'the tile provider is not available at first'
        TilesProviders tilesProviders = Stub()
        tilesProviders.getRasterTilesetMetadata(_ as OgcApiDataV2) >>> [[:], ['topo': Stub(TilesetMetadata)]]
        tilesProviders.getRasterTilesetMetadata(_ as OgcApiDataV2, _ as FeatureTypeConfigurationOgcApi) >> [:]
        def parameter = new PathParameterStyleId(Stub(SchemaValidator), tilesProviders)

        when:
        def first = parameter.getSchema(apiData).getEnum()
        def second = parameter.getSchema(apiData).getEnum()

        then:
        first == []
        second == ['topo']
    }

    def 'the parameter is not the parameter of the styles in the API definition'() {
        given:
        def parameter = new PathParameterStyleId(Stub(SchemaValidator), Stub(TilesProviders))

        expect: 'the name in the path is the same, the component of the API definition is a separate one'
        parameter.getName() == 'styleId'
        parameter.getId() != 'styleId'
    }
}
