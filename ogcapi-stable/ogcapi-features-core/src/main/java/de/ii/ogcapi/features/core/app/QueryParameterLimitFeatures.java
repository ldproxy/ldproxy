/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ExtendableConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery.Builder;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @title limit
 * @endpoints Features
 * @langEn The maximum number of features that are presented in the response document. If more
 *     features are available, a link to the next page is provided with the response. If no
 *     parameter value is provided, the default value that is configured for the API applies.
 * @langDe Die maximale Anzahl von Features, die im Antwortdokument zurückgegeben werden. Wenn mehr
 *     Features verfügbar sind, wird ein Link zur nächsten Seite mit der Antwort zurückgeliefert.
 *     Wird kein Wert für den Parameter angegeben, gilt der Standardwert, der für die API
 *     konfiguriert ist.
 */
@Singleton
@AutoBind
public class QueryParameterLimitFeatures extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Integer>, FeatureQueryParameter {

  private final SchemaValidator schemaValidator;

  @Inject
  QueryParameterLimitFeatures(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId(String collectionId) {
    return "limitFeatures_" + collectionId;
  }

  @Override
  public String getName() {
    return "limit";
  }

  @Override
  public Integer parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    FeaturesCoreConfiguration cfg =
        optionalCollectionData
            .map(cd -> cd.getExtension(FeaturesCoreConfiguration.class))
            .orElse(api.getData().getExtension(FeaturesCoreConfiguration.class))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "Could not process query parameter '%s', paging default values not provided.",
                            getName())));

    if (value == null) {
      return cfg.getDefaultPageSize();
    }

    int limit;
    try {
      limit = Integer.parseInt(value);
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid value for query parameter '%s'. The value must be a non-negative integer. Found: %s.",
              getName(), value),
          e);
    }
    //noinspection ConstantConditions
    if (limit < Integer.max(cfg.getMinimumPageSize(), 1)) {
      throw new IllegalArgumentException(
          "Invalid value for query parameter 'limit'. The value must be at least "
              + cfg.getMinimumPageSize()
              + ". Found: "
              + value);
    }
    //noinspection ConstantConditions
    if (limit > cfg.getMaximumPageSize()) {
      limit = cfg.getMaximumPageSize();
    }

    return limit;
  }

  @Override
  public String getDescription() {
    return "The optional limit parameter limits the number of items that are presented in the response document. "
        + "If more items are selected, only the number of items as specified with the limit parameter are included in the response document. "
        + "That is, the results is provided in pages ('paging'). "
        + "Only items are counted that are on the first level of the collection in the response document. "
        + "Nested objects contained within the explicitly requested items are not counted. "
        + "If the value is higher than the maximum value, the maximum value is used instead of the parameter value."
        + System.lineSeparator()
        + "If no parameter value is provided, the default value applies. "
        + "If more items are available, a link to the next page with more results is provided with the response (link relation type: 'next'). "
        + "To avoid receiving the result in pages, try to set the limit parameter to the maximum value.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
  }

  private final ConcurrentMap<Integer, ConcurrentMap<String, Schema<?>>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    String collectionId = "*";
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      Schema<?> schema = new IntegerSchema();
      Optional<Integer> minimumPageSize =
          apiData
              .getExtension(FeaturesCoreConfiguration.class)
              .map(FeaturesCoreConfiguration::getMinimumPageSize);
      minimumPageSize.ifPresent(integer -> schema.minimum(BigDecimal.valueOf(integer)));

      Optional<Integer> maxPageSize =
          apiData
              .getExtension(FeaturesCoreConfiguration.class)
              .map(FeaturesCoreConfiguration::getMaximumPageSize);
      maxPageSize.ifPresent(integer -> schema.maximum(BigDecimal.valueOf(integer)));

      Optional<Integer> defaultPageSize =
          apiData
              .getExtension(FeaturesCoreConfiguration.class)
              .map(FeaturesCoreConfiguration::getDefaultPageSize);
      defaultPageSize.ifPresent(integer -> schema.setDefault(BigDecimal.valueOf(integer)));

      schemaMap.get(apiHashCode).put(collectionId, schema);
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      Schema<?> schema = new IntegerSchema();
      FeatureTypeConfigurationOgcApi featureType = apiData.getCollections().get(collectionId);

      Optional<Integer> minimumPageSize =
          featureType
              .getExtension(FeaturesCoreConfiguration.class)
              .map(FeaturesCoreConfiguration::getMinimumPageSize);
      minimumPageSize.ifPresent(integer -> schema.minimum(BigDecimal.valueOf(integer)));

      Optional<Integer> maxPageSize =
          featureType
              .getExtension(FeaturesCoreConfiguration.class)
              .map(FeaturesCoreConfiguration::getMaximumPageSize);
      maxPageSize.ifPresent(integer -> schema.maximum(BigDecimal.valueOf(integer)));

      Optional<Integer> defaultPageSize =
          featureType
              .getExtension(FeaturesCoreConfiguration.class)
              .map(FeaturesCoreConfiguration::getDefaultPageSize);
      defaultPageSize.ifPresent(integer -> schema.setDefault(BigDecimal.valueOf(integer)));

      schemaMap.get(apiHashCode).put(collectionId, schema);
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Optional<String> validateSchema(
      OgcApiDataV2 apiData, Optional<String> collectionId, List<String> values) {
    // special validation to support limit values higher than the maximum,
    // the limit will later be reduced to the maximum

    if (values.size() != 1)
      return Optional.of(
          String.format(
              "Parameter value '%s' is invalid for parameter '%s': The must be a single value.",
              values, getName()));

    int limit;
    try {
      limit = Integer.parseInt(values.get(0));
    } catch (NumberFormatException exception) {
      return Optional.of(
          String.format(
              "Parameter value '%s' is invalid for parameter '%s': The value is not an integer.",
              values, getName()));
    }

    ExtendableConfiguration context =
        collectionId.isPresent() ? apiData.getCollections().get(collectionId.get()) : apiData;
    Optional<Integer> minimumPageSize =
        context
            .getExtension(FeaturesCoreConfiguration.class)
            .map(FeaturesCoreConfiguration::getMinimumPageSize);
    if (minimumPageSize.isPresent() && limit < minimumPageSize.get())
      return Optional.of(
          String.format(
              "Parameter value '%s' is invalid for parameter '%s': The value is smaller than the minimum value '%d'.",
              values, getName(), minimumPageSize.get()));

    return Optional.empty();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public void applyTo(
      Builder queryBuilder,
      QueryParameterSet parameters,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData) {
    parameters.getValue(this).ifPresent(queryBuilder::limit);
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return FeaturesCoreBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return FeaturesCoreBuildingBlock.SPEC;
  }
}
