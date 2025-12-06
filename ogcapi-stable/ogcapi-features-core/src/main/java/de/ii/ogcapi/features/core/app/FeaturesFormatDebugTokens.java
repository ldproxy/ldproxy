/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoderDebug;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Debug Tokens
 */
@Singleton
@AutoBind
public class FeaturesFormatDebugTokens extends FeatureFormatExtension implements ConformanceClass {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesFormatDebugTokens.class);

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("text", "plain"))
          .label("Debug Tokens")
          .parameter("tokens")
          .build();
  public static final ApiMediaType COLLECTION_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "json"))
          .label("JSON")
          .parameter("json")
          .build();

  private final boolean isDevEnv;

  @Inject
  public FeaturesFormatDebugTokens(
      AppContext appContext, FeaturesCoreProviders providers, ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, providers);
    this.isDevEnv = appContext.isDevEnv();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return isDevEnv && super.isEnabledForApi(apiData, collectionId);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isDevEnv && super.isEnabledForApi(apiData);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaType getCollectionMediaType() {
    return COLLECTION_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    // TODO Should we describe the schema? As an OpenAPI schema?
    String schemaRef = "#/components/schemas/tokens";
    Schema<?> schema = new StringSchema();
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schema)
        .schemaRef(schemaRef)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public boolean canEncodeFeatures() {
    return true;
  }

  @Override
  public boolean supportsRootConcat() {
    return true;
  }

  @Override
  public boolean supportsEmbedding() {
    return true;
  }

  @Override
  public boolean supportsHitsOnly() {
    return true;
  }

  @Override
  public boolean supportsNullVsMissing() {
    return true;
  }

  @Override
  public boolean isComplex() {
    return true;
  }

  @Override
  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {
    return Optional.of(new FeatureTokenEncoderDebug());
  }
}
