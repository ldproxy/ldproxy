/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.base.Splitter;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.Scalar;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.features.domain.SchemaBase;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * @title ids
 * @endpoints Features
 * @langEn Select only features with one of the listed feature identifiers. Feature identifiers that
 *     contain a comma are not supported.
 * @langDe Es werden nur Features mit einem der aufgelisteten Feature-Identifikatoren ausgewählt.
 *     Feature-Identifikatoren, die ein Komma enthalten, werden nicht unterstützt.
 */
@Singleton
@AutoBind
public class QueryParameterIds extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Cql2Expression>, FeatureQueryParameter {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/17-069r5.html",
              "OGC API - Features - Part 1: Core (DRAFT)"));

  private static final Splitter ARRAY_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private final Schema<?> schema;
  private final SchemaValidator schemaValidator;
  private final FeaturesCoreProviders providers;

  @Inject
  public QueryParameterIds(SchemaValidator schemaValidator, FeaturesCoreProviders providers) {
    this.schemaValidator = schemaValidator;
    this.providers = providers;
    this.schema = new ArraySchema().items(new StringSchema());
  }

  @Override
  public String getName() {
    return "ids";
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .anyMatch(collectionId -> isEnabledForApi(apiData, collectionId));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData.isCollectionEnabled(collectionId)
        && isExtensionEnabled(
            apiData.getCollections().get(collectionId),
            FeaturesCoreConfiguration.class,
            FeaturesCoreConfiguration::supportsIds);
  }

  @Override
  public Cql2Expression parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      return null;
    }

    // a value that cannot be the identifier of a feature does not select a feature
    Predicate<String> isPossibleId =
        optionalCollectionData
            .flatMap(collectionData -> providers.getFeatureSchema(api.getData(), collectionData))
            .flatMap(SchemaBase::getIdProperty)
            .filter(idProperty -> SchemaBase.Type.INTEGER.equals(idProperty.getType()))
            .map(ignore -> (Predicate<String>) QueryParameterIds::isInteger)
            .orElse(id -> true);

    List<Scalar> ids =
        ARRAY_SPLITTER
            .splitToStream(value)
            .filter(isPossibleId)
            .distinct()
            .map(id -> (Scalar) ScalarLiteral.of(id))
            .toList();

    if (ids.isEmpty()) {
      return BooleanValue2.of(false);
    }

    return In.of(ids);
  }

  private static boolean isInteger(String id) {
    try {
      Integer.parseInt(id);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  @Override
  public String getDescription() {
    return "Only features with one of the listed feature identifiers are selected.\n\n"
        + "The feature identifier is the value that identifies the feature in the `featureId` "
        + "path parameter of the Feature resource. Feature identifiers that contain a comma "
        + "are not supported.\n\n"
        + "A value that is not the identifier of a feature in the collection is not an error, "
        + "it just does not select a feature.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath);
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public boolean isFilterParameter() {
    return true;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return SPEC;
  }
}
