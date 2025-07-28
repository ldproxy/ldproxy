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
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title pretty
 * @endpoints *
 * @langEn Selects whether the json-response should be pretty-printed or not. False by default
 * @langDe Bestimmt, ob die Json-Antwort formatiert wird, oder nicht. Der Standartwert ist false.
 */
@Singleton
@AutoBind
public class QueryParameterPrettyCommon extends QueryParameterPretty {

  @Inject
  public QueryParameterPrettyCommon(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    super(extensionRegistry, schemaValidator);
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
