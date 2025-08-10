/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title pretty
 * @endpoints *
 * @langEn Selects whether a JSON response should be pretty-printed or not.
 * @langDe Bestimmt, ob eine JSON-Antwort formatiert wird, oder nicht.
 */
@Singleton
@AutoBind
public class QueryParameterPrettyCommon extends QueryParameterPretty {

  @Inject
  public QueryParameterPrettyCommon(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "prettyCommon";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return !definitionPath.equals("/collections/{collectionId}/items")
        && !definitionPath.equals("/collections/{collectionId}/items/{featureId}");
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CommonConfiguration.class;
  }
}
