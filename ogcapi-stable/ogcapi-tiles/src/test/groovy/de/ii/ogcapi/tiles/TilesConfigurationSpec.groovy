/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import de.ii.ogcapi.foundation.domain.*
import de.ii.ogcapi.html.domain.MapClient
import de.ii.ogcapi.tiles.domain.ImmutableTileSetsLink
import de.ii.ogcapi.tiles.domain.ImmutableTilesConfiguration
import de.ii.ogcapi.tiles.domain.TilesConfiguration
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation

@SuppressWarnings('ClashingTraitMethods')
class TilesConfigurationSpec extends AbstractExtensionConfigurationSpec implements MergeBase<TilesConfiguration>, MergeMinimal<TilesConfiguration>, MergeSimple<TilesConfiguration>, MergeCollection<TilesConfiguration>, MergeMap<TilesConfiguration>, MergeNested<TilesConfiguration> {
    @Override
    TilesConfiguration getFull() {
        return new ImmutableTilesConfiguration.Builder()
                .enabled(true)
                .tileProvider("foo")
                .tileProviderTileset("bar")
                .addTileSetEncodings("foo")
                .mapClientType(MapClient.Type.MAP_LIBRE)
                .style("foo")
                .removeZoomLevelConstraints(true)
                .putTransformations("foo", [new ImmutablePropertyTransformation.Builder().rename("bar").build()])
                .addAdditionalLinks(link("https://example.com/foo"))
                .build()
    }

    static link(String href) {
        return new ImmutableTileSetsLink.Builder().rel("describedby").title("foo").href(href).build()
    }

    @Override
    TilesConfiguration getMinimal() {
        return new ImmutableTilesConfiguration.Builder()
                .build()
    }

    @Override
    TilesConfiguration getMinimalFullMerged() {
        return getFull()
    }

    @Override
    TilesConfiguration getSimple() {
        return new ImmutableTilesConfiguration.Builder()
                .enabled(false)
                .mapClientType(MapClient.Type.MAP_LIBRE)
                .style("foo")
                .build()
    }

    @Override
    TilesConfiguration getSimpleFullMerged() {
        return new ImmutableTilesConfiguration.Builder()
                .from(getFull())
                .from(getSimple())
                .build()
    }

    @Override
    TilesConfiguration getCollection() {
        return new ImmutableTilesConfiguration.Builder()
                .tileProviderTileset("foo")
                .addTileSetEncodings("bar")
                .addAdditionalLinks(link("https://example.com/foo"), link("https://example.com/bar"))
                .build()
    }

    @Override
    TilesConfiguration getCollectionFullMerged() {
        return new ImmutableTilesConfiguration.Builder()
                .from(getFull())
                .tileProviderTileset("foo")
                .tileSetEncodings(ImmutableList.of(
                        "foo",
                        "bar"
                ))
                .additionalLinks(ImmutableList.of(
                        link("https://example.com/foo"),
                        link("https://example.com/bar")
                ))
                .build()
    }

    @Override
    TilesConfiguration getMap() {
        return new ImmutableTilesConfiguration.Builder()
                .putTransformations("bar", [new ImmutablePropertyTransformation.Builder().rename("foo").build()])
                .build()
    }

    @Override
    TilesConfiguration getMapFullMerged() {
        return new ImmutableTilesConfiguration.Builder()
                .from(getFull())
                .transformations(ImmutableMap.of(
                        "foo", [new ImmutablePropertyTransformation.Builder().rename("bar").build()],
                        "bar", [new ImmutablePropertyTransformation.Builder().rename("foo").build()]
                ))
                .build()
    }

    @Override
    TilesConfiguration getNested() {
        return new ImmutableTilesConfiguration.Builder()
                .putTransformations("foo", [new ImmutablePropertyTransformation.Builder().codelist("cl").build()])
                .build()
    }

    @Override
    TilesConfiguration getNestedFullMerged() {
        return new ImmutableTilesConfiguration.Builder()
                .from(getFull())
                .transformations(ImmutableMap.of(
                        "foo", [
                        new ImmutablePropertyTransformation.Builder().rename("bar").build(),
                        new ImmutablePropertyTransformation.Builder().codelist("cl").build()
                ]
                ))
                .build()
    }
}
