/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.async.domain.ImmutableAsyncConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title Async
 * @langEn ...
 * @langDe ...
 */
@Singleton
@AutoBind
public class AsyncBuildingBlock implements ApiBuildingBlock {

  @Inject
  public AsyncBuildingBlock() {}

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .filter(ProcessesCoreConfiguration::isEnabled)
        .isPresent();
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableAsyncConfiguration.Builder().build();
  }
}
