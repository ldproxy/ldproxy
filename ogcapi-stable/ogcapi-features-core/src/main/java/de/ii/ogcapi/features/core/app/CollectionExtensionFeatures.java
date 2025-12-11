/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.CollectionExtension;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection;
import de.ii.ogcapi.collections.domain.ImmutableOgcApiCollection.Builder;
import de.ii.ogcapi.common.domain.OgcApiExtent;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.TemporalExtent;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class CollectionExtensionFeatures implements CollectionExtension {

  private final I18n i18n;
  private final ExtensionRegistry extensionRegistry;
  private final FeaturesCoreProviders providers;

  @Inject
  public CollectionExtensionFeatures(
      ExtensionRegistry extensionRegistry, I18n i18n, FeaturesCoreProviders providers) {
    this.extensionRegistry = extensionRegistry;
    this.i18n = i18n;
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public ImmutableOgcApiCollection.Builder process(
      Builder collection,
      FeatureTypeConfigurationOgcApi featureType,
      OgcApi api,
      URICustomizer uriCustomizer,
      boolean isNested,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      Optional<Locale> language) {

    if (!isEnabledForApi(api.getData(), featureType.getId())) {
      return collection;
    }

    collection
        .title(featureType.getLabel())
        .description(featureType.getDescription())
        .itemType(
            featureType
                .getExtension(FeaturesCoreConfiguration.class)
                .filter(ExtensionConfiguration::isEnabled)
                .flatMap(FeaturesCoreConfiguration::getItemType)
                .map(Enum::toString)
                .orElse(FeaturesCoreConfiguration.ItemType.unknown.toString()));

    api.getItemCount(featureType.getId())
        .filter(count -> count >= 0)
        .ifPresent(count -> collection.putExtensions("itemCount", count));

    URICustomizer uriBuilder = uriCustomizer.copy().ensureNoTrailingSlash().clearParameters();

    if (isNested) {
      // also add an untyped a self link in the Collections resource, otherwise the standard links
      // are already there
      collection.addLinks(
          new ImmutableLink.Builder()
              .href(
                  uriBuilder
                      .copy()
                      .ensureLastPathSegments("collections", featureType.getId())
                      .removeParameters("f")
                      .toString())
              .rel("self")
              .title(
                  i18n.get("selfLinkCollection", language)
                      .replace("{{collection}}", featureType.getLabel()))
              .build());
    }

    List<ApiMediaType> featureMediaTypes =
        extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
            .filter(outputFormatExtension -> outputFormatExtension.isEnabledForApi(api.getData()))
            .map(outputFormatExtension -> outputFormatExtension.getMediaType())
            .collect(Collectors.toList());

    featureMediaTypes.stream()
        .forEach(
            mtype ->
                collection.addLinks(
                    new ImmutableLink.Builder()
                        .href(
                            uriBuilder
                                .ensureLastPathSegments("collections", featureType.getId(), "items")
                                .setParameter("f", mtype.parameter())
                                .toString())
                        .rel("items")
                        .type(mtype.type().toString())
                        .title(
                            i18n.get("itemsLink", language)
                                .replace("{{collection}}", featureType.getLabel())
                                .replace("{{type}}", mtype.label()))
                        .build()));

    Optional<String> describeFeatureTypeUrl = Optional.empty();
    if (describeFeatureTypeUrl.isPresent()) {
      collection.addLinks(
          new ImmutableLink.Builder()
              .href(describeFeatureTypeUrl.get())
              .rel("describedby")
              .type("application/xml")
              .title(i18n.get("describedByXsdLink", language))
              .build());
    }

    // only add extents for cases where we can filter at least using 'bbox' / 'datetime'
    Optional<FeatureSchema> featureSchema = providers.getFeatureSchema(api.getData(), featureType);
    boolean isSpatial =
        featureSchema.map(schema -> schema.getPrimaryGeometry().isPresent()).orElse(false);
    boolean isTemporal =
        featureSchema
            .map(
                schema ->
                    schema.getPrimaryInstant().isPresent()
                        || schema.getPrimaryInterval().isPresent())
            .orElse(false);

    Optional<BoundingBox> spatial = api.getSpatialExtent(featureType.getId());
    Optional<TemporalExtent> temporal = api.getTemporalExtent(featureType.getId());
    if (isSpatial && isTemporal) {
      collection.extent(OgcApiExtent.of(spatial, temporal));
    } else if (isSpatial) {
      collection.extent(OgcApiExtent.of(spatial, Optional.empty()));
    } else if (isTemporal) {
      collection.extent(OgcApiExtent.of(Optional.empty(), temporal));
    }

    return collection;
  }
}
