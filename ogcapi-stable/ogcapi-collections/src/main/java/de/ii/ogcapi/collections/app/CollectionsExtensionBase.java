/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.CollectionsConfiguration;
import de.ii.ogcapi.collections.domain.CollectionsExtension;
import de.ii.ogcapi.collections.domain.ImmutableCollections;
import de.ii.ogcapi.collections.domain.ImmutableCollections.Builder;
import de.ii.ogcapi.collections.domain.OgcApiCollection;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class CollectionsExtensionBase implements CollectionsExtension {

  private final ExtensionRegistry extensionRegistry;

  @Inject
  public CollectionsExtensionBase(ExtensionRegistry extensionRegistry) {
    this.extensionRegistry = extensionRegistry;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CollectionsConfiguration.class;
  }

  @Override
  public ImmutableCollections.Builder process(
      Builder collectionsBuilder,
      OgcApi api,
      URICustomizer uriCustomizer,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {

    if (!isEnabledForApi(api.getData())) {
      return collectionsBuilder;
    }

    List<CollectionExtension> collectionExtenders =
        extensionRegistry.getExtensionsForType(CollectionExtension.class);

    List<OgcApiCollection> collections =
        api.getData().getCollections().values().stream()
            .filter(featureType -> api.getData().isCollectionEnabled(featureType.getId()))
            .sorted(Comparator.comparing(FeatureTypeConfigurationOgcApi::getId))
            .map(
                featureType ->
                    CollectionExtension.createNestedCollection(
                        featureType,
                        api,
                        mediaType,
                        alternateMediaTypes,
                        language,
                        uriCustomizer,
                        collectionExtenders))
            .collect(Collectors.toList());

    collectionsBuilder.addAllCollections(collections);

    return collectionsBuilder; // TODO .addSections(ImmutableMap.of("collections", collections));
  }
}
