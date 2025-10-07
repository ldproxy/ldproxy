/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.sorting.domain.SortingConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/** add a link to the Sortables to the collection */
@Singleton
@AutoBind
public class SortablesOnCollection implements CollectionExtension {

  private final I18n i18n;

  @Inject
  public SortablesOnCollection(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SortingConfiguration.class;
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
    if (isExtensionEnabled(featureTypeConfiguration, SortingConfiguration.class) && !isNested) {
      collection.addAllLinks(
          ImmutableList.<Link>builder()
              .add(
                  new ImmutableLink.Builder()
                      .href(
                          uriCustomizer
                              .copy()
                              .ensureNoTrailingSlash()
                              .ensureLastPathSegment("sortables")
                              .removeParameters("f")
                              .toString())
                      .rel("http://www.opengis.net/def/rel/ogc/1.0/sortables")
                      .title(i18n.get("sortablesLink", language))
                      .build())
              .build());
    }

    return collection;
  }
}
