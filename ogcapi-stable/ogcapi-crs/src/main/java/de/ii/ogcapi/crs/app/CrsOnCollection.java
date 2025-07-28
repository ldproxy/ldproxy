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
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add CRS information to the collection information */
@Singleton
@AutoBind
public class CrsOnCollection implements CollectionExtension {

  private final FeaturesCoreProviders providers;
  private final CrsSupport crsSupport;

  @Inject
  public CrsOnCollection(FeaturesCoreProviders providers, CrsSupport crsSupport) {
    this.providers = providers;
    this.crsSupport = crsSupport;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrsConfiguration.class;
  }

  @Override
  public ImmutableOgcApiCollection.Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    boolean hasGeometry =
        providers
            .getFeatureSchema(api.getData(), featureTypeConfiguration)
            .flatMap(SchemaBase::getPrimaryGeometry)
            .isPresent();
    if (isExtensionEnabled(featureTypeConfiguration, CrsConfiguration.class) && hasGeometry) {
      List<String> crsList;
      if (isNested
          && !api.getData()
              .getExtension(CrsConfiguration.class)
              .map(CrsConfiguration::shouldSuppressGlobalCrsList)
              .orElse(false)) {
        // just reference the default list of coordinate reference systems
        crsList = ImmutableList.of("#/crs");
      } else {
        // this is just the collection resource, so no default to reference; include all CRSs
        crsList =
            crsSupport.getSupportedCrsList(api.getData(), featureTypeConfiguration).stream()
                .flatMap(
                    crs -> Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
                .filter(Objects::nonNull)
                .collect(ImmutableList.toImmutableList());
      }
      collection.crs(crsList);

      String storageCrsUri =
          crsSupport
              .getStorageCrs(api.getData(), Optional.of(featureTypeConfiguration))
              .toUriString();

      // add native CRS as storageCRS
      collection.storageCrs(storageCrsUri);
    }

    return collection;
  }
}
