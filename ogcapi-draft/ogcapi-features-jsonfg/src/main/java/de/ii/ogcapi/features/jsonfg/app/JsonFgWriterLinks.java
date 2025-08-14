/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.strings.domain.StringTemplateFilters;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterLinks implements GeoJsonWriter {

  static final String OPEN_TEMPLATE = "{{";
  static final String CLOSE_TEMPLATE = "}}";

  Map<String, List<Link>> collectionMap;
  boolean isEnabled;
  List<Link> currentLinks;
  Map<String, String> currentMap = new HashMap<>();

  @Inject
  JsonFgWriterLinks() {}

  @Override
  public JsonFgWriterLinks create() {
    return new JsonFgWriterLinks();
  }

  @Override
  public int getSortPriority() {
    return 60;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    collectionMap = getCollectionMap(context.encoding());

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next) {
    List<Link> links =
        Objects.requireNonNullElse(collectionMap.get(context.type()), ImmutableList.of());
    isEnabled = !links.isEmpty();
    if (isEnabled) {
      currentLinks =
          links.stream()
              .map(
                  link -> {
                    if (hasTemplate(link)) return link;
                    context.encoding().getState().addCurrentFeatureLinks(link);
                    return null;
                  })
              .filter(Objects::nonNull)
              .collect(ImmutableList.toImmutableList());
      currentMap.clear();
      currentMap.putAll(
          FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS.apply(context.encoding().getServiceUrl()));
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (isEnabled
        && !currentLinks.isEmpty()
        && context.schema().filter(FeatureSchema::isValue).isPresent()
        && Objects.nonNull(context.value())) {

      final FeatureSchema schema = context.schema().get();
      currentMap.put(schema.getFullPathAsString(), context.value());
    }

    next.accept(context);
  }

  @Override
  public void onPropertiesEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next) {
    if (isEnabled) {
      currentLinks.forEach(
          link -> {
            link = replace(link);
            if (!hasTemplate(link) && !link.getHref().isEmpty() && !link.getRel().isEmpty())
              context.encoding().getState().addCurrentFeatureLinks(link);
          });
    }

    next.accept(context);
  }

  private Link replace(Link link) {
    if (hasTemplate(link)) {
      final String href = StringTemplateFilters.applyTemplate(link.getHref(), currentMap::get);
      final String rel = StringTemplateFilters.applyTemplate(link.getRel(), currentMap::get);
      final String title =
          Objects.nonNull(link.getTitle())
              ? StringTemplateFilters.applyTemplate(link.getTitle(), currentMap::get)
              : null;
      link = new ImmutableLink.Builder().from(link).href(href).rel(rel).title(title).build();
    }
    return link;
  }

  private boolean hasTemplate(Link link) {
    return link.getHref().contains(OPEN_TEMPLATE)
        || Objects.requireNonNullElse(link.getTitle(), "").contains(OPEN_TEMPLATE)
        || Objects.requireNonNullElse(link.getRel(), "").contains(OPEN_TEMPLATE);
  }

  private Map<String, List<Link>> getCollectionMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, List<Link>> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .keySet()
        .forEach(
            collectionId ->
                builder.put(
                    collectionId,
                    writeJsonFgExtensions(transformationContext)
                        ? transformationContext
                            .getApiData()
                            .getExtension(JsonFgConfiguration.class, collectionId)
                            .map(JsonFgConfiguration::getLinks)
                            .orElse(List.of())
                        : List.of()));

    return builder.build();
  }
}
