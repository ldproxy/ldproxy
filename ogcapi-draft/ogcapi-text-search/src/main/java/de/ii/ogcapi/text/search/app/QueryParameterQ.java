/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.text.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.text.search.domain.TextSearchConfiguration;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Function;
import de.ii.xtraplatform.cql.domain.Like;
import de.ii.xtraplatform.cql.domain.Or;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @title q
 * @endpoints Features
 * @langEn General text search in multiple text properties of the data. Search terms are separated
 *     by comma. If at least one of the search terms is included in an item, it is included in the
 *     result set.
 * @langDe Allgemeine Textsuche in mehreren Texteigenschaften der Daten. Die Suchbegriffe werden
 *     durch ein Komma getrennt. Wenn mindestens einer der Suchbegriffe in einem Objekt enthalten
 *     ist, wird er in die Ergebnismenge aufgenommen.
 */
@Singleton
@AutoBind
public class QueryParameterQ extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<Cql2Expression>, ConformanceClass {

  private static final String PARAMETER_Q = "q";

  private final Schema<?> baseSchema;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterQ(SchemaValidator schemaValidator, Cql cql) {
    this.schemaValidator = schemaValidator;
    this.baseSchema = new ArraySchema().items(new StringSchema());
  }

  @Override
  public String getName() {
    return PARAMETER_Q;
  }

  @Override
  public Cql2Expression parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    Set<String> textSearchProperties =
        optionalCollectionData
            .map(cd -> cd.getExtension(TextSearchConfiguration.class))
            .orElse(api.getData().getExtension(TextSearchConfiguration.class))
            .map(TextSearchConfiguration::getProperties)
            .map(Set::copyOf)
            .orElse(ImmutableSet.of());

    return qToCql(textSearchProperties, Splitter.on(",").trimResults().splitToList(value));
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public String getDescription() {
    return "General text search in multiple text properties of the data. Separate search terms by comma."
        + "If at least one of the search terms is included in an item, it is included in the result set.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return isExtensionEnabled(
        apiData.getCollections().get(collectionId),
        TextSearchConfiguration.class,
        config -> !config.getProperties().isEmpty());
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return baseSchema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return baseSchema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TextSearchConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return TextSearchBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return TextSearchBuildingBlock.SPEC;
  }

  private Cql2Expression qToCql(Set<String> qFields, List<String> qValues) {
    if (qFields.isEmpty() || qValues.isEmpty()) {
      // nothing to filter, ignore
      return null;
    }

    if (qFields.size() == 1 && qValues.size() == 1) {
      return qToLike(qFields.iterator().next(), qValues.get(0));
    }

    return Or.of(
        qFields.stream()
            .map(
                qField ->
                    qValues.stream()
                        .map(qValue -> qToLike(qField, qValue))
                        .collect(Collectors.toUnmodifiableList()))
            .flatMap(Collection::stream)
            .collect(Collectors.toUnmodifiableList()));
  }

  private Like qToLike(String qField, String qValue) {
    return Like.ofFunction(
        Function.of("lower", ImmutableList.of(Property.of(qField))),
        ScalarLiteral.of("%" + qValue.toLowerCase(Locale.ROOT) + "%"));
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of(
        "http://www.opengis.net/spec/ogcapi-features-9/0.0/conf/text-search",
        "http://www.opengis.net/spec/ogcapi-features-9/0.0/conf/features-text-search");
  }
}
