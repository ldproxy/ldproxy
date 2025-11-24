/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.flatgeobuf.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.flatgeobuf.domain.FlatgeobufConfiguration;
import de.ii.ogcapi.features.flatgeobuf.domain.ImmutableFlatgeobufConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - FlatGeobuf
 * @langEn Encode features as [FlatGeobuf](https://flatgeobuf.org).
 * @langDe Kodierung von Features als [FlatGeobuf](https://flatgeobuf.org).
 * @scopeEn Features are encoded as FlatGeobuf file. The feature properties are always flattened.
 *     Properties with multiple values are limited to `maxMultiplicity` values.
 * @scopeDe Kodierung von Features als FlatGeobuf-Datei. Die Objekteigenschaften werden stets
 *     abgeflacht. Eigenschaften mit mehreren Werten werden auf `maxMultiplicity` Werte begrenzt.
 * @ref:cfg {@link de.ii.ogcapi.features.flatgeobuf.domain.FlatgeobufConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.features.flatgeobuf.domain.ImmutableFlatgeobufConfiguration}
 */
@Singleton
@AutoBind
public class FeaturesFlatgeobufBuildingBlock implements ApiBuildingBlock {

  public static int DEFAULT_MULTIPLICITY = 3;

  @Inject
  public FeaturesFlatgeobufBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableFlatgeobufConfiguration.Builder()
        .enabled(false)
        .maxMultiplicity(DEFAULT_MULTIPLICITY)
        .build();
  }

  @Override
  public <T extends ExtensionConfiguration> T hydrateConfiguration(T cfg) {
    if (cfg instanceof FlatgeobufConfiguration) {
      FlatgeobufConfiguration config = (FlatgeobufConfiguration) cfg;
      Map<String, List<PropertyTransformation>> transformations =
          config.extendWithFlattenIfMissing();

      if (Objects.equals(transformations, config.getTransformations())) {
        return (T) config;
      }

      return (T)
          new ImmutableFlatgeobufConfiguration.Builder()
              .from(config)
              .transformations(transformations)
              .build();
    }

    return cfg;
  }
}
