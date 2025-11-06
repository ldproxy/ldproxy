/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.domain;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.AContains;
import de.ii.xtraplatform.cql.domain.ArrayLiteral;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Eq;
import de.ii.xtraplatform.cql.domain.Function;
import de.ii.xtraplatform.cql.domain.Like;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.cql.domain.TemporalLiteral;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
public abstract class QueryParameterTemplateQueryable extends OgcApiQueryParameterBase
    implements FeatureQueryParameter, TypedQueryParameter<Cql2Expression> {

  private static final Splitter ARRAY_SPLITTER = Splitter.on(',').trimResults();

  public abstract String getApiId();

  public abstract String getCollectionId();

  public abstract Schema<?> getSchema();

  @Override
  public abstract SchemaValidator getSchemaValidator();

  @Override
  @Value.Default
  public String getId() {
    return getName() + "_" + getCollectionId();
  }

  @Override
  public abstract String getName();

  @Override
  public abstract String getDescription();

  public abstract SchemaBase.Type getType();

  public abstract Optional<SchemaBase.Type> getValueType();

  @Override
  @Value.Default
  public boolean getExplode() {
    return false;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return getSchema();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return QueryablesConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return Objects.equals(apiData.getId(), getApiId());
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return Objects.equals(apiData.getId(), getApiId())
        && Objects.equals(collectionId, getCollectionId());
  }

  @Override
  public boolean isFilterParameter() {
    return true;
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

    // handle array case
    if (getType() == Type.VALUE_ARRAY) {
      if (value.contains("*")) {
        return Function.of(
            "ALIKE",
            ImmutableList.of(
                Property.of(getName()), ScalarLiteral.of(value.replaceAll("\\*", "%"))));
      }
      return AContains.of(
          Property.of(getName()),
          ArrayLiteral.of(
              ARRAY_SPLITTER.splitToList(value).stream()
                  .map(s -> getScalarLiteral(s, getValueType().orElse(Type.STRING)))
                  .collect(Collectors.toUnmodifiableList())));
    }

    Type t = getValueType().orElse(getType());
    switch (t) {
      case INTEGER:
      case FLOAT:
      case BOOLEAN:
        return Eq.of(getName(), getScalarLiteral(value, t));
      case STRING:
        if (value.contains("*")) {
          return Like.of(getName(), ScalarLiteral.of(value.replaceAll("\\*", "%")));
        }
        return Eq.of(getName(), ScalarLiteral.of(value));
      case DATE:
      case DATETIME:
        return Eq.of(getName(), TemporalLiteral.of(value));
      default:
        return BooleanValue2.of(false);
    }
  }

  @Override
  @Value.Default
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.STABLE_OGC);
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return Optional.of(
        ExternalDocumentation.of(
            "https://docs.ogc.org/is/17-069r4/17-069r4.html", "OGC API - Features - Part 1: Core"));
  }

  private ScalarLiteral getScalarLiteral(String value, SchemaBase.Type valueType) {
    switch (valueType) {
      case INTEGER:
        return ScalarLiteral.of(Integer.parseInt(value));
      case FLOAT:
        return ScalarLiteral.of(Double.parseDouble(value));
      case BOOLEAN:
        return ScalarLiteral.of(Boolean.parseBoolean(value));
      default:
        return ScalarLiteral.of(value);
    }
  }
}
