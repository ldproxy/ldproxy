/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Foundation
 * @langEn Essentials, API catalog with all published APIs.
 * @langDe Essenzielle Funktionalität, API-Katalog mit allen veröffentlichten APIs.
 * @ref:cfg {@link de.ii.ogcapi.foundation.domain.FoundationConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.foundation.domain.ImmutableFoundationConfiguration}
 */
@Singleton
@AutoBind
public class FoundationBuildingBlock implements ApiBuildingBlock {

  @Inject
  public FoundationBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableFoundationConfiguration.Builder()
        .enabled(true)
        .includeLinkHeader(true)
        .useLangParameter(false)
        .includeSpecificationInformation(false)
        .build();
  }
}
