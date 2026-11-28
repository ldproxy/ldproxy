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
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    if (isExtensionEnabled(featureTypeConfiguration, CrsConfiguration.class)
        && hasGeometry(api.getData(), featureTypeConfiguration)) {
      List<String> crsList;
      if (isNested && !suppressGlobalCrsList(api.getData())) {
        // just reference the default list of coordinate reference systems
        crsList = ImmutableList.of("#/crs");
      } else {
        // this is just the collection resource, so no default to reference; include all CRSs
        crsList =
            crsSupport.getSupportedCrsList(api.getData(), featureTypeConfiguration).stream()
                .flatMap(crs -> crs.allUris().stream())
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

  /**
   * Unless the global list is suppressed, a collection in the Collections resource references the
   * global list of CRSs (`"#/crs"`), while the Collection resource lists the CRSs.
   */
  @Override
  public boolean isNestedCollectionIdentical(OgcApiDataV2 apiData) {
    return suppressGlobalCrsList(apiData)
        || apiData.getCollections().values().stream()
            .noneMatch(
                collection ->
                    isExtensionEnabled(collection, CrsConfiguration.class)
                        && hasGeometry(apiData, collection));
  }

  private static boolean suppressGlobalCrsList(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(CrsConfiguration.class)
        .map(CrsConfiguration::shouldSuppressGlobalCrsList)
        .orElse(false);
  }

  private boolean hasGeometry(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi featureTypeConfiguration) {
    return providers
        .getFeatureSchema(apiData, featureTypeConfiguration)
        .flatMap(SchemaBase::getPrimaryGeometry)
        .isPresent();
  }
}
