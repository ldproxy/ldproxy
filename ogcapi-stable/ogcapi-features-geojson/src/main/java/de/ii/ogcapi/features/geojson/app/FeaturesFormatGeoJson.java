/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import de.ii.ogcapi.collections.domain.CollectionsConfiguration;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation;
import de.ii.ogcapi.features.core.domain.ItemTypeSpecificConformanceClass;
import de.ii.ogcapi.features.core.domain.SchemaGeneratorCollectionOpenApi;
import de.ii.ogcapi.features.core.domain.SchemaGeneratorOpenApi;
import de.ii.ogcapi.features.geojson.domain.FeatureEncoderGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterRegistry;
import de.ii.ogcapi.features.geojson.domain.ImmutableFeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.ProfileGeoJson;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

/**
 * @title GeoJSON
 */
@Singleton
@AutoBind
public class FeaturesFormatGeoJson extends FeatureFormatExtension
    implements ItemTypeSpecificConformanceClass {

  private static final String CONFORMANCE_CLASS_FEATURES =
      "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/geojson";
  private static final String CONFORMANCE_CLASS_RECORDS =
      "http://www.opengis.net/spec/ogcapi-records-1/0.0/conf/json";
  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "geo+json"))
          .label("GeoJSON")
          .parameter("json")
          .build();

  private final Values<Codelist> codelistStore;
  private final FeaturesCoreValidation featuresCoreValidator;
  private final SchemaGeneratorOpenApi schemaGeneratorFeature;
  private final SchemaGeneratorCollectionOpenApi schemaGeneratorFeatureCollection;
  private final GeoJsonWriterRegistry geoJsonWriterRegistry;

  @Inject
  public FeaturesFormatGeoJson(
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      FeaturesCoreValidation featuresCoreValidator,
      SchemaGeneratorOpenApi schemaGeneratorFeature,
      SchemaGeneratorCollectionOpenApi schemaGeneratorFeatureCollection,
      GeoJsonWriterRegistry geoJsonWriterRegistry,
      ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, providers);
    this.codelistStore = valueStore.forType(Codelist.class);
    this.featuresCoreValidator = featuresCoreValidator;
    this.schemaGeneratorFeature = schemaGeneratorFeature;
    this.schemaGeneratorFeatureCollection = schemaGeneratorFeatureCollection;
    this.geoJsonWriterRegistry = geoJsonWriterRegistry;
  }

  @Override
  public boolean isRestrictedToSimpleFeaturesGeometries(List<Profile> profiles) {
    return profiles.stream()
        .filter(p -> p instanceof ProfileGeoJson)
        .findFirst()
        .map(profile -> ((ProfileGeoJson) profile).isRestrictedToSimpleFeaturesGeometries())
        .orElse(true);
  }

  @Override
  public boolean supportsEmbedding() {
    return true;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();

    if (isItemTypeUsed(apiData, FeaturesCoreConfiguration.ItemType.feature))
      builder.add(CONFORMANCE_CLASS_FEATURES);

    if (isItemTypeUsed(apiData, FeaturesCoreConfiguration.ItemType.record))
      builder.add(CONFORMANCE_CLASS_RECORDS);

    return builder.build();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeoJsonConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        || extensionRegistry.getExtensionsForType(ProfileGeoJson.class).stream()
            .anyMatch(
                profile ->
                    apiData.getCollections().keySet().stream()
                        .anyMatch(collectionId -> profile.isEnabledForApi(apiData, collectionId)));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        || extensionRegistry.getExtensionsForType(ProfileGeoJson.class).stream()
            .anyMatch(profile -> profile.isEnabledForApi(apiData, collectionId));
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == MODE.NONE) return ValidationResult.of();

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(api.getData());

    for (Map.Entry<String, FeatureSchema> entry : featureSchemas.entrySet()) {
      if (entry.getValue().getPrimaryGeometries().stream()
          .anyMatch(s -> !s.isSimpleFeatureGeometry())) {
        builder.addStrictErrors(
            String.format(
                "Feature type '%s' has a primary geometry that is not a Simple Feature geometry. GeoJSON only supports Simple Feature geometry types.",
                entry.getKey()));
      }
    }

    // get GeoJSON configurations to process
    Map<String, GeoJsonConfiguration> geoJsonConfigurationMap =
        api.getData().getCollections().entrySet().stream()
            .map(
                entry -> {
                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  final GeoJsonConfiguration config =
                      collectionData.getExtension(GeoJsonConfiguration.class).orElse(null);
                  if (Objects.isNull(config)) return null;
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, Collection<String>> keyMap =
        geoJsonConfigurationMap.entrySet().stream()
            .map(
                entry ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(), entry.getValue().getTransformations().keySet()))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    for (Map.Entry<String, Collection<String>> stringCollectionEntry :
        featuresCoreValidator.getInvalidPropertyKeys(keyMap, featureSchemas).entrySet()) {
      for (String property : stringCollectionEntry.getValue()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "A transformation for property ''{0}'' in collection ''{1}'' is invalid, because the property was not found in the provider schema.",
                property, stringCollectionEntry.getKey()));
      }
    }

    for (Map.Entry<String, GeoJsonConfiguration> entry : geoJsonConfigurationMap.entrySet()) {
      String collectionId = entry.getKey();
      for (Map.Entry<String, List<PropertyTransformation>> entry2 :
          entry.getValue().getTransformations().entrySet()) {
        String property = entry2.getKey();
        for (PropertyTransformation transformation : entry2.getValue()) {
          builder = transformation.validate(builder, collectionId, property, codelistStore.ids());
        }
      }
    }

    return builder.build();
  }

  @Override
  public ApiMediaTypeContent getContent() {
    Schema<?> schema = new ObjectSchema();
    String schemaRef = "https://geojson.org/schema/FeatureCollection.json";
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(OBJECT_SCHEMA)
        .schemaRef(OBJECT_SCHEMA_REF)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaTypeContent getFeatureContent(
      OgcApiDataV2 apiData, Optional<String> collectionId, boolean featureCollection) {
    String effectiveCollectionId;
    if (collectionId.isEmpty()) {
      if (apiData
          .getExtension(CollectionsConfiguration.class)
          .filter(config -> config.getCollectionDefinitionsAreIdentical().orElse(false))
          .isPresent()) {
        effectiveCollectionId = apiData.getCollections().keySet().iterator().next();
      } else {
        return getContent();
      }
    } else {
      effectiveCollectionId = collectionId.get();
    }

    return new ImmutableApiMediaTypeContent.Builder()
        .schema(
            featureCollection
                ? schemaGeneratorFeatureCollection.getSchema(apiData, effectiveCollectionId)
                : schemaGeneratorFeature.getSchema(apiData, effectiveCollectionId))
        .schemaRef(
            featureCollection
                ? schemaGeneratorFeatureCollection.getSchemaReference(effectiveCollectionId)
                : schemaGeneratorFeature.getSchemaReference(effectiveCollectionId))
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaType getCollectionMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public boolean canEncodeFeatures() {
    return true;
  }

  @Override
  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {

    // TODO support language
    ImmutableSortedSet<GeoJsonWriter> geoJsonWriters =
        geoJsonWriterRegistry.getWriters().stream()
            .map(GeoJsonWriter::create)
            .collect(
                ImmutableSortedSet.toImmutableSortedSet(
                    Comparator.comparingInt(GeoJsonWriter::getSortPriority)));

    ImmutableFeatureTransformationContextGeoJson transformationContextGeoJson =
        ImmutableFeatureTransformationContextGeoJson.builder()
            .from(transformationContext)
            .geoJsonConfig(
                transformationContext
                    .getApiData()
                    .getCollections()
                    .get(transformationContext.getCollectionId())
                    .getExtension(GeoJsonConfiguration.class)
                    .get())
            .prettify(transformationContext.getPrettify())
            .build();

    return Optional.of(new FeatureEncoderGeoJson(transformationContextGeoJson, geoJsonWriters));
  }

  @Override
  public boolean supportsHitsOnly() {
    return true;
  }

  @Override
  public boolean isComplex() {
    return true;
  }

  @Override
  public boolean supportsRootConcat() {
    return true;
  }
}
