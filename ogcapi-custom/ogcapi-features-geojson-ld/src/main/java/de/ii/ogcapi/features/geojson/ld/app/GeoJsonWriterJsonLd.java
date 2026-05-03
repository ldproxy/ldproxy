/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.ld.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.geojson.ld.domain.GeoJsonLdConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.strings.domain.StringTemplateFilters;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class GeoJsonWriterJsonLd implements GeoJsonWriter {

  List<String> currentTypes;

  @Inject
  GeoJsonWriterJsonLd() {}

  @Override
  public GeoJsonWriterJsonLd create() {
    return new GeoJsonWriterJsonLd();
  }

  @Override
  public int getSortPriority() {
    return 5;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.encoding().isFeatureCollection()) {
      Optional<GeoJsonLdConfiguration> jsonLdOptions =
          context
              .encoding()
              .getApiData()
              .getCollections()
              .get(context.encoding().getCollectionId())
              .getExtension(GeoJsonLdConfiguration.class);

      if (jsonLdOptions.isPresent() && jsonLdOptions.get().isEnabled()) {
        writeContext(context.encoding(), jsonLdOptions.get().getContext());
        writeJsonLdType(context.encoding(), ImmutableList.of("geojson:FeatureCollection"));
      }
    }

    currentTypes = ImmutableList.of();

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    Optional<GeoJsonLdConfiguration> jsonLdOptions =
        context
            .encoding()
            .getApiData()
            .getCollections()
            .get(context.encoding().getCollectionId())
            .getExtension(GeoJsonLdConfiguration.class);

    if (jsonLdOptions.isPresent() && jsonLdOptions.get().isEnabled()) {
      if (!context.encoding().isFeatureCollection()) {
        writeContext(context.encoding(), jsonLdOptions.get().getContext());
      }

      currentTypes =
          jsonLdOptions
              .map(GeoJsonLdConfiguration::getTypes)
              .orElse(ImmutableList.of("geojson:Feature"));

      if (currentTypes.stream().noneMatch(type -> type.contains("{{type}}"))) {
        writeJsonLdType(context.encoding(), currentTypes);
        currentTypes = ImmutableList.of();
      }
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {

    if (!currentTypes.isEmpty()) {
      writeJsonLdType(context.encoding(), currentTypes);
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.schema().isPresent() && Objects.nonNull(context.value())) {

      final FeatureSchema currentSchema = context.schema().get();
      String currentValue = context.value();

      if (currentSchema.isId()) {

        Map<String, String> substitutions =
            FeaturesCoreProviders.DEFAULT_SUBSTITUTIONS_PLUS.apply(
                context.encoding().getServiceUrl(),
                ImmutableMap.of(
                    "featureId",
                    currentValue,
                    "collectionId",
                    context.encoding().getCollectionId()));

        Optional<String> jsonLdId =
            context
                .encoding()
                .getApiData()
                .getCollections()
                .get(context.encoding().getCollectionId())
                .getExtension(GeoJsonLdConfiguration.class)
                .filter(GeoJsonLdConfiguration::isEnabled)
                .flatMap(GeoJsonLdConfiguration::getIdTemplate)
                .map(
                    idTemplate ->
                        StringTemplateFilters.applyTemplate(idTemplate, substitutions::get));

        if (jsonLdId.isPresent()) {
          context.encoding().getJson().writeStringField("@id", jsonLdId.get());
        }
      }

      if (currentSchema.isType() && !currentTypes.isEmpty()) {
        currentTypes =
            currentTypes.stream()
                .map(type -> type.replace("{{type}}", currentValue))
                .collect(Collectors.toUnmodifiableList());
      }
    }

    next.accept(context);
  }

  private void writeContext(
      FeatureTransformationContextGeoJson transformationContext, String ldContext)
      throws IOException {
    if (Objects.nonNull(ldContext))
      transformationContext
          .getJson()
          .writeStringField(
              "@context",
              ldContext
                  .replace("{{serviceUrl}}", transformationContext.getServiceUrl())
                  .replace("{{collectionId}}", transformationContext.getCollectionId()));
  }

  private void writeJsonLdType(
      FeatureTransformationContextGeoJson transformationContext, List<String> types)
      throws IOException {

    if (types.size() == 1) {
      // write @type
      transformationContext.getJson().writeStringField("@type", types.get(0));
    } else if (types.size() > 1) {
      // write @type as array
      transformationContext.getJson().writeArrayFieldStart("@type");

      for (String type : types) {
        transformationContext.getJson().writeString(type);
      }

      transformationContext.getJson().writeEndArray();
    }
  }
}
