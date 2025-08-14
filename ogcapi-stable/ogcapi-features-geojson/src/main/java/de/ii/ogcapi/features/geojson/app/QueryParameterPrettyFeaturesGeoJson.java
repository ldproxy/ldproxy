/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterPretty;
import de.ii.ogcapi.features.core.domain.FeatureTransformationQueryParameter;
import de.ii.ogcapi.features.core.domain.ImmutableFeatureTransformationContextGeneric.Builder;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title pretty
 * @endpoints Features, Feature
 * @langEn Selects whether a JSON response should be pretty-printed or not.
 * @langDe Bestimmt, ob eine JSON-Antwort formatiert wird, oder nicht.
 */
@Singleton
@AutoBind
public class QueryParameterPrettyFeaturesGeoJson extends QueryParameterPretty
    implements TypedQueryParameter<Boolean>, FeatureTransformationQueryParameter {

  @Inject
  public QueryParameterPrettyFeaturesGeoJson(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "prettyFeatures";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/collections/{collectionId}/items")
        || definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GeoJsonConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }

  @Override
  public void applyTo(Builder builder, QueryParameterSet queryParameterSet) {
    queryParameterSet.getValue(this).ifPresent(builder::prettify);
  }
}
