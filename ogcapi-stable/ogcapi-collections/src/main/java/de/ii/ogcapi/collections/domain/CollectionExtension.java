/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ContentExtension;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@AutoMultiBind
public interface CollectionExtension extends ContentExtension {

  static OgcApiCollection createNestedCollection(
      FeatureTypeConfigurationOgcApi featureType,
      OgcApi api,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language,
      URICustomizer uriCustomizer,
      List<CollectionExtension> collectionExtenders) {
    Builder ogcApiCollection = ImmutableOgcApiCollection.builder().id(featureType.getId());

    for (CollectionExtension ogcApiCollectionExtension : collectionExtenders) {
      ogcApiCollection =
          ogcApiCollectionExtension.process(
              ogcApiCollection,
              featureType,
              api,
              uriCustomizer.copy(),
              true,
              mediaType,
              alternateMediaTypes,
              language);
    }

    ImmutableOgcApiCollection result = null;
    try {
      result = ogcApiCollection.build();
    } catch (Throwable e) {
      result = null;
    }
    return result;
  }

  ImmutableOgcApiCollection.Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureTypeConfiguration,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language);

  default String getResourceName() {
    return "Collection";
  }
  ;
}
