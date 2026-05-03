/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title collectionId
 * @endpoints Collection Styles, Collection Style, Collection Style Metadata, Collection Style
 *     Legend
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdStyles extends AbstractPathParameterCollectionId {

  @Inject
  PathParameterCollectionIdStyles(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    if (!apiCollectionMap.containsKey(apiData.hashCode())) {
      apiCollectionMap.put(
          apiData.hashCode(),
          apiData.getCollections().values().stream()
              .filter(collection -> apiData.isCollectionEnabled(collection.getId()))
              .filter(
                  collection ->
                      collection
                          .getExtension(StylesConfiguration.class)
                          .filter(ExtensionConfiguration::isEnabled)
                          .isPresent())
              .map(FeatureTypeConfiguration::getId)
              .collect(Collectors.toUnmodifiableList()));
    }

    return apiCollectionMap.get(apiData.hashCode());
  }

  @Override
  public boolean isExplodeInOpenApi(OgcApiDataV2 apiData) {
    return false;
  }

  @Override
  public String getId() {
    return "collectionIdStyles";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.startsWith("/collections/{collectionId}/styles");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return StylesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return StylesBuildingBlock.SPEC;
  }
}
