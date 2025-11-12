/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.app;

import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.ADDRESS;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.BOUNDARIES;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.CHILDREN;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.CITY_JSON;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.CITY_OBJECTS;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.COUNTRY_NAME;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.GEOGRAPHICAL_EXTENT;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.LOCALITY_NAME;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.LOCATION;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.LOD;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.METADATA;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.PARENTS;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.REFERENCE_SYSTEM;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.SCALE;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.SEMANTICS;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.SURFACES;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.SURFACE_TYPES;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.THOROUGHFARE_NAME;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.THOROUGHFARE_NUMBER;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.TITLE;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.TRANSFORM;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.TRANSLATE;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.TYPE;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.VALUES;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.VERSION;
import static de.ii.ogcapi.features.cityjson.domain.CityJsonWriter.VERTICES;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import de.ii.ogcapi.features.cityjson.domain.CityJsonConfiguration;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriter;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriterRegistry;
import de.ii.ogcapi.features.cityjson.domain.FeatureEncoderCityJson;
import de.ii.ogcapi.features.cityjson.domain.ImmutableFeatureTransformationContextCityJson;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation;
import de.ii.ogcapi.features.core.domain.SchemaGeneratorCollectionOpenApi;
import de.ii.ogcapi.features.core.domain.SchemaGeneratorOpenApi;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class FeaturesFormatCityJsonBase extends FeatureFormatExtension {

  public static final String IGNORE = "ignore";
  private final Values<Codelist> codelistStore;
  protected final FeaturesCoreValidation featuresCoreValidator;
  protected final SchemaGeneratorOpenApi schemaGeneratorFeature;
  protected final SchemaGeneratorCollectionOpenApi schemaGeneratorFeatureCollection;
  protected final CityJsonWriterRegistry cityJsonWriterRegistry;
  protected final CrsTransformerFactory crsTransformerFactory;
  protected final CrsInfo crsInfo;

  public FeaturesFormatCityJsonBase(
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      FeaturesCoreValidation featuresCoreValidator,
      SchemaGeneratorOpenApi schemaGeneratorFeature,
      SchemaGeneratorCollectionOpenApi schemaGeneratorFeatureCollection,
      CityJsonWriterRegistry cityJsonWriterRegistry,
      CrsTransformerFactory crsTransformerFactory,
      CrsInfo crsInfo,
      ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, providers);
    this.codelistStore = valueStore.forType(Codelist.class);
    this.featuresCoreValidator = featuresCoreValidator;
    this.schemaGeneratorFeature = schemaGeneratorFeature;
    this.schemaGeneratorFeatureCollection = schemaGeneratorFeatureCollection;
    this.cityJsonWriterRegistry = cityJsonWriterRegistry;
    this.crsTransformerFactory = crsTransformerFactory;
    this.crsInfo = crsInfo;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CityJsonConfiguration.class;
  }

  @Override
  public boolean supportsEmbedding() {
    return true;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == MODE.NONE) return ValidationResult.of();

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    OgcApiDataV2 apiData = api.getData();
    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(apiData);

    // get CityJSON configurations to process
    Map<String, CityJsonConfiguration> configurationMap =
        apiData.getCollections().entrySet().stream()
            .map(
                entry -> {
                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  final CityJsonConfiguration config =
                      collectionData.getExtension(CityJsonConfiguration.class).orElse(null);
                  if (Objects.isNull(config)) return null;
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, Collection<String>> keyMap =
        configurationMap.entrySet().stream()
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

    for (Map.Entry<String, CityJsonConfiguration> entry : configurationMap.entrySet()) {
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
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(getSchema(IGNORE))
        .schemaRef(getSchemaRef(IGNORE))
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaTypeContent getFeatureContent(
      OgcApiDataV2 apiData, Optional<String> collectionId, boolean featureCollection) {
    return getContent();
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
  public boolean supportsRootConcat() {
    return true;
  }

  @Override
  public boolean supportsNullVsMissing() {
    return true;
  }

  protected Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext,
      @SuppressWarnings("unused") Optional<Locale> language,
      CityJsonConfiguration.Version version,
      boolean textSequences) {

    // check that we have a 3D CRS, CityJSON does not support 2D CRSs
    EpsgCrs crs = transformationContext.getTargetCrs();
    if (!crsInfo.is3d(crs)) {
      throw new IllegalArgumentException(
          String.format(
              "CityJSON requires a 3D coordinate reference system. Found: %s", crs.toUriString()));
    }

    ImmutableSortedSet<CityJsonWriter> cityJsonWriters =
        cityJsonWriterRegistry.getCityJsonWriters().stream()
            .map(CityJsonWriter::create)
            .collect(
                ImmutableSortedSet.toImmutableSortedSet(
                    Comparator.comparingInt(CityJsonWriter::getSortPriority)));

    ImmutableFeatureTransformationContextCityJson transformationContextCityJson =
        ImmutableFeatureTransformationContextCityJson.builder()
            .from(transformationContext)
            .crs(crs)
            .textSequences(textSequences)
            .version(version)
            .build();

    return Optional.of(new FeatureEncoderCityJson(transformationContextCityJson, cityJsonWriters));
  }

  public static Schema<?> getSchema(@SuppressWarnings("unused") String collectionId) {
    return new ObjectSchema()
        .required(ImmutableList.of(TYPE, VERSION, CITY_OBJECTS, VERTICES))
        .addProperties(TYPE, new StringSchema()._enum(ImmutableList.of(CITY_JSON)))
        .addProperties(
            VERSION,
            new StringSchema()
                ._enum(
                    Arrays.stream(CityJsonConfiguration.Version.values())
                        .map(CityJsonConfiguration.Version::toString)
                        .collect(Collectors.toUnmodifiableList())))
        .addProperties(
            TRANSFORM,
            new ObjectSchema()
                .required(ImmutableList.of(SCALE, TRANSLATE))
                .addProperties(
                    SCALE, new ArraySchema().items(new NumberSchema()).minItems(3).maxItems(3))
                .addProperties(
                    TRANSLATE, new ArraySchema().items(new NumberSchema()).minItems(3).maxItems(3)))
        .addProperties(
            VERTICES,
            new ArraySchema()
                .items(new ArraySchema().items(new NumberSchema()).minItems(3).maxItems(3)))
        .addProperties(
            METADATA,
            new ObjectSchema()
                .addProperties(TITLE, new StringSchema())
                .addProperties(
                    REFERENCE_SYSTEM,
                    new StringSchema()
                        .pattern("^http://www.opengis.net/def/crs/(EPSG|OGC)/[^/]+/[^/]+$"))
                .addProperties(
                    GEOGRAPHICAL_EXTENT,
                    new ArraySchema().items(new NumberSchema()).minItems(6).maxItems(6)))
        .addProperties(
            CITY_OBJECTS,
            new ObjectSchema()
                .additionalProperties(
                    new ObjectSchema()
                        .required(ImmutableList.of(TYPE))
                        .addProperties(
                            TYPE,
                            new StringSchema()._enum(ImmutableList.of("Building", "BuildingPart")))
                        .addProperties(PARENTS, new ArraySchema().items(new StringSchema()))
                        .addProperties(CHILDREN, new ArraySchema().items(new StringSchema()))
                        .addProperties(
                            "geometry",
                            new ArraySchema()
                                .items(
                                    new ObjectSchema()
                                        .required(ImmutableList.of(TYPE, LOD, BOUNDARIES))
                                        .addProperties(
                                            TYPE,
                                            new StringSchema()._enum(ImmutableList.of("Solid")))
                                        .addProperties(
                                            LOD,
                                            new StringSchema()._enum(ImmutableList.of("1", "2")))
                                        .addProperties(
                                            BOUNDARIES,
                                            new ArraySchema()
                                                .items(
                                                    new ArraySchema()
                                                        .items(
                                                            new ArraySchema()
                                                                .items(
                                                                    new ArraySchema()
                                                                        .items(
                                                                            new IntegerSchema())))))
                                        .addProperties(
                                            SEMANTICS,
                                            new ObjectSchema()
                                                .addProperties(
                                                    SURFACES,
                                                    new ArraySchema()
                                                        .items(
                                                            new ObjectSchema()
                                                                .addProperties(
                                                                    TYPE,
                                                                    new StringSchema()
                                                                        ._enum(SURFACE_TYPES))))
                                                .addProperties(
                                                    VALUES,
                                                    new ArraySchema()
                                                        .items(
                                                            new ArraySchema()
                                                                .items(new IntegerSchema()))))))
                        .addProperties(
                            ADDRESS,
                            new ArraySchema()
                                .items(
                                    new ObjectSchema()
                                        .addProperties(THOROUGHFARE_NAME, new StringSchema())
                                        .addProperties(THOROUGHFARE_NUMBER, new StringSchema())
                                        .addProperties(LOCALITY_NAME, new StringSchema())
                                        .addProperties(COUNTRY_NAME, new StringSchema())
                                        .addProperties(
                                            LOCATION,
                                            new ObjectSchema()
                                                .required(ImmutableList.of(TYPE, LOD, BOUNDARIES))
                                                .addProperties(
                                                    TYPE,
                                                    new StringSchema()
                                                        ._enum(ImmutableList.of("MultiPoint")))
                                                .addProperties(
                                                    LOD,
                                                    new StringSchema()
                                                        ._enum(ImmutableList.of("1", "2")))
                                                .addProperties(
                                                    BOUNDARIES,
                                                    new ArraySchema().items(new IntegerSchema())))))

                        // TODO: derive from feature schema
                        .addProperties("attributes", new ObjectSchema())));
  }

  public static String getSchemaRef(String collectionId) {
    return "#/components/schemas/CityJson_" + collectionId;
  }
}
