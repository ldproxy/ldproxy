/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.csv.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureSchemaCache;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SchemaCacheSfFlat;
import de.ii.ogcapi.features.csv.domain.CsvConfiguration;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title CSV
 */
@Singleton
@AutoBind
public class FeaturesFormatCsv extends FeatureFormatExtension implements ConformanceClass {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesFormatCsv.class);

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("text", "csv"))
          .label("CSV")
          .parameter("csv")
          .build();
  public static final ApiMediaType COLLECTION_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "json"))
          .label("JSON")
          .parameter("json")
          .build();

  private final FeatureSchemaCache schemaCache;

  @Inject
  public FeaturesFormatCsv(FeaturesCoreProviders providers, ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, providers);
    this.schemaCache = new SchemaCacheSfFlat();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CsvConfiguration.class;
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
    String schemaRef = "#/components/schemas/csv";
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
  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {

    OgcApiDataV2 apiData = transformationContext.getApiData();
    String collectionId = transformationContext.getCollectionId();
    FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();
    CsvConfiguration configuration =
        collectionData.getExtension(CsvConfiguration.class).orElseThrow();

    FeatureSchema schema =
        schemaCache.getSchema(
            providers
                .getFeatureSchema(apiData, collectionData)
                .orElse(
                    new ImmutableFeatureSchema.Builder()
                        .name(collectionId)
                        .type(SchemaBase.Type.OBJECT)
                        .build()),
            apiData,
            collectionData,
            configuration,
            configuration);

    return Optional.of(
        new FeatureEncoderCsv(
            ImmutableEncodingContextCsv.builder()
                .from(transformationContext)
                .collectionId(collectionId)
                .schema(schema)
                .build()));
  }
}
