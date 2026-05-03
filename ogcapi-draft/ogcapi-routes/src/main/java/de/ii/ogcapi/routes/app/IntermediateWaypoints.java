/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.routes.domain.RoutingConfiguration;
import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class IntermediateWaypoints implements ConformanceClass {

  @Inject
  IntermediateWaypoints() {}

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(RoutingBuildingBlock.INTERMEDIATE_WAYPOINTS);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return ConformanceClass.super.isEnabledForApi(apiData)
        && apiData
            .getExtension(getBuildingBlockConfigurationType())
            .flatMap(
                config ->
                    Optional.ofNullable(((RoutingConfiguration) config).getIntermediateWaypoints()))
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }
}
