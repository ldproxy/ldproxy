/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.AbstractQueryParameterDatetime;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Interval;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.TIntersects;
import de.ii.xtraplatform.cql.domain.TemporalLiteral;
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
 * @endpoints Feature
 * @langEn Select the version of the feature whose primary instant or interval covers the provided
 *     date or date-time. The default is the request time, which selects the current version. Unlike
 *     the {@code datetime} parameter on the Features resource, intervals are not accepted here
 *     because the response carries a single feature representation.
 * @langDe Wählt die Version des Features aus, deren primäre zeitliche Eigenschaft den angegebenen
 *     Wert (Datum oder Zeitstempel) abdeckt. Standardwert ist der Anfragezeitpunkt, womit die
 *     aktuelle Version geliefert wird. Anders als der Parameter {@code datetime} auf der
 *     Features-Ressource sind hier keine Intervalle erlaubt, weil die Antwort genau eine Version
 *     liefert.
 */
@Singleton
@AutoBind
public class QueryParameterDatetimeFeature extends AbstractQueryParameterDatetime
    implements TypedQueryParameter<Cql2Expression>, FeatureQueryParameter {

  private final FeaturesCoreProviders providers;
  private final Map<Integer, Map<String, Schema<?>>> schemaMap = new ConcurrentHashMap<>();

  @Inject
  QueryParameterDatetimeFeature(SchemaValidator schemaValidator, FeaturesCoreProviders providers) {
    super(schemaValidator);
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items/{featureId}".equals(definitionPath);
  }

  @Override
  public String getDescription() {
    return "A date or date-time (RFC 3339), or \"now\".\n\n"
        + "Examples:\n\n"
        + "* A date-time: \"2018-02-12T23:20:50Z\"\n"
        + "* A date: \"2018-02-12\"\n"
        + "* \"now\" (default)\n\n"
        + "Selects the version of the feature whose primary temporal property covers the supplied"
        + " value. Intervals are not supported on this resource.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    return schemaMap
        .computeIfAbsent(apiHashCode, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(collectionId, k -> buildSchema(apiData, collectionId));
  }

  private Schema<?> buildSchema(OgcApiDataV2 apiData, String collectionId) {
    Schema<?> schema = getCopyOfInstantBaseSchema();
    if (isEnabledForApi(apiData, collectionId)) {
      schema.setDefault("now");
    }
    return schema;
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
      if (collectionData == null || !isEnabledForApi(api.getData(), collectionData.getId())) {
        return null;
      }
      effectiveValue = "now";
    }

    if (collectionData == null) {
      throw new IllegalStateException(
          String.format(
              "The parameter '%s' could not be processed, no collection provided.", getName()));
    }

    if (effectiveValue.contains("/")) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid value for query parameter '%s': intervals are not supported on a single"
                  + " feature, only a date, date-time, or \"now\".",
              getName()));
    }

    TemporalLiteral temporalLiteral;
    try {
      temporalLiteral = TemporalLiteral.of(effectiveValue);
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

    return BooleanValue2.of(true);
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return VersionedFeaturesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return VersionedFeaturesBuildingBlock.SPEC;
  }
}
