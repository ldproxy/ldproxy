/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.AbstractPathParameterCollectionId;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * @title collectionId
 * @endpoints Features, Feature
 * @langEn The identifier of the feature collection.
 * @langDe Der Identifikator der Feature Collection.
 */
@Singleton
@AutoBind
public class PathParameterCollectionIdFeatures extends AbstractPathParameterCollectionId {

  @Inject
  PathParameterCollectionIdFeatures(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath)
        || "/collections/{collectionId}/items/{featureId}".equals(definitionPath);
  }

  @Override
  public String getId() {
    return "collectionIdFeatures";
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesCoreConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.STABLE_OGC);
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return Optional.of(
        ExternalDocumentation.of(
            "https://docs.ogc.org/is/17-069r4/17-069r4.html", "OGC API - Features - Part 1: Core"));
  }
}
