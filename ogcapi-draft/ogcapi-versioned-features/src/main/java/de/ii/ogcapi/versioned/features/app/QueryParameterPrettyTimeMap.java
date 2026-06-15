/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterPretty;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * @title pretty
 * @endpoints Time Map
 * @langEn Selects whether the JSON response should be pretty-printed or not.
 * @langDe Bestimmt, ob die JSON-Antwort formatiert wird, oder nicht.
 */
@Singleton
@AutoBind
public class QueryParameterPrettyTimeMap extends QueryParameterPretty
    implements TypedQueryParameter<Boolean> {

  @Inject
  public QueryParameterPrettyTimeMap(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "prettyTimeMap";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items/{featureId}/versions".equals(definitionPath);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }
}
