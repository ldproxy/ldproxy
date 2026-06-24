/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.DATETIME_INTERVAL_SEPARATOR;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.AbstractQueryParameterDatetime;
import de.ii.ogcapi.features.core.domain.DatetimeDefaultProvider;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Interval;
import de.ii.xtraplatform.cql.domain.IsNull;
import de.ii.xtraplatform.cql.domain.Or;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.TIntersects;
import de.ii.xtraplatform.cql.domain.TemporalLiteral;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.Tuple;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @title datetime
 * @endpoints Features
 * @langEn Select only features that have a primary instant or interval that intersects the provided
 *     instant or interval.
 * @langDe Es werden nur Features ausgewählt, deren primäre zeitliche Eigenschaft den angegebenen
 *     Wert (Zeitstempel, Datum oder Intervall) schneidet.
 */
@Singleton
@AutoBind
public class QueryParameterDatetime extends AbstractQueryParameterDatetime
    implements TypedQueryParameter<Cql2Expression>, FeatureQueryParameter {

  private final FeaturesCoreProviders providers;
  private final ExtensionRegistry extensionRegistry;
  private final Map<Integer, Map<String, Schema<?>>> schemaMap = new ConcurrentHashMap<>();

  @Inject
  QueryParameterDatetime(
      SchemaValidator schemaValidator,
      FeaturesCoreProviders providers,
      ExtensionRegistry extensionRegistry) {
    super(schemaValidator);
    this.providers = providers;
    this.extensionRegistry = extensionRegistry;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    return schemaMap
        .computeIfAbsent(apiHashCode, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(collectionId, k -> buildSchema(apiData, collectionId));
  }

  private Schema<?> buildSchema(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    if (collectionData != null) {
      Optional<String> defaultValue =
          extensionRegistry.getExtensionsForType(DatetimeDefaultProvider.class).stream()
              .filter(provider -> provider.isEnabledForApi(apiData, collectionId))
              .map(provider -> provider.getDefault(apiData, collectionData))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .findFirst();
      if (defaultValue.isPresent()) {
        Schema<?> schema = getCopyOfBaseSchema();
        schema.setDefault(defaultValue.get());
        return schema;
      }
    }
    return super.getSchema(apiData, collectionId);
  }

  @Override
  public Cql2Expression parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    FeatureTypeConfigurationOgcApi collectionData = optionalCollectionData.orElse(null);

    String effectiveValue = value;
    if (Objects.isNull(effectiveValue)) {
      if (collectionData == null) {
        return null;
      }
      effectiveValue =
          extensionRegistry.getExtensionsForType(DatetimeDefaultProvider.class).stream()
              .filter(
                  provider ->
                      optionalCollectionData
                          .map(cd -> provider.isEnabledForApi(api.getData(), cd.getId()))
                          .orElse(provider.isEnabledForApi(api.getData())))
              .map(provider -> provider.getDefault(api.getData(), collectionData))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .findFirst()
              .orElse(null);
      if (Objects.isNull(effectiveValue)) {
        return null;
      }
    }

    if (collectionData == null) {
      throw new IllegalStateException(
          String.format(
              "The parameter '%s' could not be processed, no collection provided.", getName()));
    }

    // valid values: timestamp or time interval;
    // this includes open intervals indicated by ".." (see ISO 8601-2);
    // accept also unknown ("") with the same interpretation;
    // in addition, "now" is accepted for the current time

    TemporalLiteral temporalLiteral;
    try {
      if (effectiveValue.contains(DATETIME_INTERVAL_SEPARATOR)) {
        temporalLiteral =
            TemporalLiteral.of(
                Splitter.on(DATETIME_INTERVAL_SEPARATOR).splitToList(effectiveValue));
      } else {
        temporalLiteral = TemporalLiteral.of(effectiveValue);
      }
    } catch (Throwable e) {
      throw new IllegalArgumentException(
          String.format("Invalid value for query parameter '%s'.", getName()), e);
    }

    FeatureSchema featureSchema =
        providers
            .getQueryablesSchema(api.getData(), collectionData)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "The parameter '%s' could not be processed, no feature schema provided.",
                            getName())));

    Optional<FeatureSchema> primaryInstant = featureSchema.getPrimaryInstant();
    if (primaryInstant.isPresent()) {
      Property property = Property.of(primaryInstant.get().getFullPathAsString());
      if (primaryInstant.get().isRequired()
          || !providers
              .getFeatureProvider(api.getData(), collectionData)
              .filter(provider -> provider instanceof FeatureQueries)
              .map(provider -> ((FeatureQueries) provider).supportsIsNull())
              .orElse(false)) {
        return TIntersects.of(property, temporalLiteral);
      }
      return Or.of(TIntersects.of(property, temporalLiteral), IsNull.of(property));
    }
    Optional<Tuple<FeatureSchema, FeatureSchema>> primaryInterval =
        featureSchema.getPrimaryInterval();
    if (primaryInterval.isPresent()) {
      FeatureSchema begin = primaryInterval.get().first();
      FeatureSchema end = primaryInterval.get().second();
      if (begin != null && end != null) {
        return TIntersects.of(
            Interval.of(
                ImmutableList.of(
                    Property.of(begin.getFullPathAsString()),
                    Property.of(end.getFullPathAsString()))),
            temporalLiteral);
      } else if (begin != null) {
        return TIntersects.of(
            Interval.of(
                ImmutableList.of(
                    Property.of(begin.getFullPathAsString()), TemporalLiteral.of(Instant.MAX))),
            temporalLiteral);
      } else if (end != null) {
        return TIntersects.of(
            Interval.of(
                ImmutableList.of(
                    TemporalLiteral.of(Instant.MIN), Property.of(end.getFullPathAsString()))),
            temporalLiteral);
      }
    }

    // no spatial property or unbounded interval, matches all features
    return BooleanValue2.of(true);
  }

  @Override
  public boolean isFilterParameter() {
    return true;
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
