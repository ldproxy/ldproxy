/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class CrudOnCollection implements CollectionExtension {

  private final I18n i18n;
  private final CrsSupport crsSupport;

  @Inject
  public CrudOnCollection(I18n i18n, CrsSupport crsSupport) {
    this.i18n = i18n;
    this.crsSupport = crsSupport;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }

  @Override
  public Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {
    // skip link on /collections
    if (isNested) return collection;

    // nothing to add if disabled
    String collectionId = featureTypeConfiguration.getId();
    if (!isEnabledForApi(api.getData(), collectionId)) {
      return collection;
    }

    // TODO the editor client currently requires support for EPSG:25832
    if (!crsSupport.isSupported(api.getData(), featureTypeConfiguration, EpsgCrs.of(25832))) {
      return collection;
    }

    return collection.addAllLinks(
        new CrudLinkGenerator().generateCollectionLinks(uriCustomizer, i18n, language));
  }
}
