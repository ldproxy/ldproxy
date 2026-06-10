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
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @title collectionId
 * @endpoints Time Map
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdVersions implements OgcApiPathParameter {

  public static final String COLLECTION_ID_PATTERN = "[\\w\\-]+";

  private final SchemaValidator schemaValidator;

  @Inject
  PathParameterCollectionIdVersions(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getPattern() {
    return COLLECTION_ID_PATTERN;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return apiData.getCollections().keySet().stream()
        .filter(apiData::isCollectionEnabled)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new StringSchema()._enum(ImmutableList.copyOf(getValues(apiData)));
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getName() {
    return "collectionId";
  }

  @Override
  public String getId() {
    return "collectionIdVersions";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a feature collection.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && "/collections/{collectionId}/items/{featureId}/versions".equals(definitionPath);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }
}
