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
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

  @Inject
  QueryParameterDatetime(SchemaValidator schemaValidator, FeaturesCoreProviders providers) {
    super(schemaValidator);
    this.providers = providers;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
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

    FeatureTypeConfigurationOgcApi collectionData =
        optionalCollectionData.orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "The parameter '%s' could not be processed, no collection provided.",
                        getName())));

    // valid values: timestamp or time interval;
    // this includes open intervals indicated by ".." (see ISO 8601-2);
    // accept also unknown ("") with the same interpretation;
    // in addition, "now" is accepted for the current time

    TemporalLiteral temporalLiteral;
    try {
      if (value.contains(DATETIME_INTERVAL_SEPARATOR)) {
        temporalLiteral =
            TemporalLiteral.of(Splitter.on(DATETIME_INTERVAL_SEPARATOR).splitToList(value));
      } else {
        temporalLiteral = TemporalLiteral.of(value);
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
