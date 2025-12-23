/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSql;
import de.ii.xtraplatform.features.sql.domain.ImmutableFeatureProviderSqlData;
import de.ii.xtraplatform.tiles.domain.ImmutableTileProviderFeaturesData;
import de.ii.xtraplatform.tiles.domain.TileProviderData;
import de.ii.xtraplatform.tiles.domain.TileProviderFeaturesData;
import de.ii.xtraplatform.tiles3d.domain.ImmutableTile3dProviderFeaturesData;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProviderData;
import de.ii.xtraplatform.tiles3d.domain.Tile3dProviderFeaturesData;

public interface EntityDataBuilders {

  default ImmutableOgcApiDataV2.Builder api() {
    return new ImmutableOgcApiDataV2.Builder()
        .entityStorageVersion(2)
        .serviceType(OgcApiDataV2.SERVICE_TYPE);
  }

  default ImmutableFeatureProviderSqlData.Builder provider() {
    return new ImmutableFeatureProviderSqlData.Builder()
        .entityStorageVersion(2)
        .providerType(FeatureProvider.PROVIDER_TYPE)
        .providerSubType(FeatureProviderSql.PROVIDER_SUB_TYPE);
  }

  default ImmutableTileProviderFeaturesData.Builder tiles() {
    return new ImmutableTileProviderFeaturesData.Builder()
        .providerType(TileProviderData.PROVIDER_TYPE)
        .providerSubType(TileProviderFeaturesData.PROVIDER_SUBTYPE);
  }

  default ImmutableTile3dProviderFeaturesData.Builder tiles3d() {
    return new ImmutableTile3dProviderFeaturesData.Builder()
        .providerType(Tile3dProviderData.PROVIDER_TYPE)
        .providerSubType(Tile3dProviderFeaturesData.PROVIDER_SUBTYPE);
  }
}
