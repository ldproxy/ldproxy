/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.CommonConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataHydratorExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.services.domain.ServicesContext;
import java.net.URI;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class CommonDataHydrator implements OgcApiDataHydratorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(CommonDataHydrator.class);

  private final URI servicesUri;

  @Inject
  public CommonDataHydrator(ServicesContext servicesContext) {
    this.servicesUri = servicesContext.getUri();
  }

  @Override
  public int getSortPriority() {
    return 50;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CommonConfiguration.class;
  }

  @Override
  public OgcApiDataV2 getHydratedData(OgcApiDataV2 apiData) {

    if (apiData.getDescription().isEmpty()
        || !apiData.getDescription().get().contains("{serviceUrl}")) {
      return apiData;
    }

    OgcApiDataV2 data = apiData;

    // replace {serviceUrl} in API descriptions
    data =
        new ImmutableOgcApiDataV2.Builder()
            .from(data)
            .description(
                data.getDescription()
                    .map(desc -> desc.replace("{serviceUrl}", servicesUri.toString())))
            .build();

    return data;
  }
}
