/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.common.domain.CommonConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataHydratorExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.services.domain.ServicesContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.AbstractMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class CollectionsDataHydrator implements OgcApiDataHydratorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(CollectionsDataHydrator.class);

  private final URI servicesUri;

  @Inject
  public CollectionsDataHydrator(ServicesContext servicesContext) {
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

    OgcApiDataV2 data = apiData;

    // replace {serviceUrl} in collection descriptions
    data =
        new ImmutableOgcApiDataV2.Builder()
            .from(data)
            .collections(
                data.getCollections().entrySet().stream()
                    .map(
                        entry -> {
                          final String collectionId = entry.getKey();
                          if (entry.getValue().getDescription().isEmpty()
                              || !entry
                                  .getValue()
                                  .getDescription()
                                  .get()
                                  .contains("{serviceUrl}")) {
                            return entry;
                          }

                          return new AbstractMap.SimpleImmutableEntry<
                              String, FeatureTypeConfigurationOgcApi>(
                              collectionId,
                              new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                                  .from(entry.getValue())
                                  .description(
                                      entry
                                          .getValue()
                                          .getDescription()
                                          .map(
                                              desc ->
                                                  desc.replace(
                                                      "{serviceUrl}", servicesUri.toString())))
                                  .build());
                        })
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .build();

    return data;
  }
}
