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
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
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

  /**
   * OGC API - Common - Part 2 requires that every property of a collection in the Collections
   * resource has the same value in the Collection resource (requirement
   * `/req/collections/description-success`, part C; `links` excepted).
   *
   * @param apiData the API
   * @return {@code false}, if the extension adds a property to a collection in the Collections
   *     resource with a value that differs from the value in the Collection resource
   */
  default boolean isNestedCollectionIdentical(OgcApiDataV2 apiData) {
    return true;
  }

  /**
   * @param extensionRegistry the extension registry
   * @param apiData the API
   * @return {@code true}, if the collections in the Collections resource have the same property
   *     values as in the Collection resource, i.e., the conformance classes of OGC API - Common -
   *     Part 2 may be declared
   */
  static boolean areNestedCollectionsIdentical(
      ExtensionRegistry extensionRegistry, OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(CollectionExtension.class).stream()
        .allMatch(extension -> extension.isNestedCollectionIdentical(apiData));
  }
}
