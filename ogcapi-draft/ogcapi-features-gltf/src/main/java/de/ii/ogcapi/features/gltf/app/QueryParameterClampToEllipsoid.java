/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gltf.domain.GltfConfiguration;
import de.ii.ogcapi.features.gltf.domain.GltfQueryParameter;
import de.ii.ogcapi.features.gltf.domain.ImmutableFeatureTransformationContextGltf;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @name clampToEllipsoid
 * @endpoints Features, Feature
 * @langEn If set to `true`, the z coordinates of each feature will be changed so that the bottom of
 *     the feature is on the WGS84 ellipsoid. This parameter only affects glTF models.
 * @langDe Wenn dieser Parameter auf `true` gesetzt ist, werden die z-Koordinaten jedes Features so
 *     verändert, dass der Boden des Features auf dem WGS84-Ellipsoid liegt. Dieser Parameter wirkt
 *     sich nur auf glTF-Modelle aus.
 */
@Singleton
@AutoBind
public class QueryParameterClampToEllipsoid extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Boolean>, GltfQueryParameter {

  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterClampToEllipsoid(SchemaValidator schemaValidator) {
    super();
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId() {
    return "clampToEllipsoid";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath)
        || "/collections/{collectionId}/items/{featureId}".equals(definitionPath);
  }

  @Override
  public String getName() {
    return "clampToEllipsoid";
  }

  @Override
  public Boolean parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    return Objects.nonNull(value) && Boolean.parseBoolean(value);
  }

  @Override
  public String getDescription() {
    return "If set to `true`, the z coordinates of each feature will be changed so that the bottom of the feature is on the WGS84 ellipsoid. This parameter only affects glTF models.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new BooleanSchema()._default(false);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData.getCollections().containsKey(collectionId)
        && apiData.getCollections().get(collectionId).getEnabled()
        && apiData
            .getExtension(GltfConfiguration.class, collectionId)
            .map(GltfConfiguration::isEnabled)
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GltfConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }

  @Override
  public void applyTo(
      ImmutableFeatureTransformationContextGltf.Builder builder,
      QueryParameterSet queryParameterSet) {
    builder.clampToEllipsoid(queryParameterSet.getValue(this).orElse(false));
  }
}
