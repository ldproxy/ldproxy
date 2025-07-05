/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.CollectionsExtension;
import de.ii.ogcapi.collections.domain.ImmutableCollections;
import de.ii.ogcapi.collections.domain.ImmutableCollections.Builder;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * add CRS information to the collection information (default list of coordinate reference systems)
 */
@Singleton
@AutoBind
public class CrsOnCollections implements CollectionsExtension {

  private final CrsSupport crsSupport;

  @Inject
  public CrsOnCollections(CrsSupport crsSupport) {
    this.crsSupport = crsSupport;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrsConfiguration.class;
  }

  @Override
  public ImmutableCollections.Builder process(
      Builder collectionsBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    if (isExtensionEnabled(api.getData(), CrsConfiguration.class)) {

      // list all CRSs as the list of default CRSs
      ImmutableList<String> crsList =
          api.getData()
                  .getExtension(CrsConfiguration.class)
                  .map(CrsConfiguration::shouldSuppressGlobalCrsList)
                  .orElse(false)
              ? ImmutableList.of()
              : crsSupport.getSupportedCrsList(api.getData()).stream()
                  .flatMap(
                      crs ->
                          Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
                  .filter(Objects::nonNull)
                  .collect(ImmutableList.toImmutableList());

      collectionsBuilder.crs(crsList);
    }

    return collectionsBuilder;
  }
}
