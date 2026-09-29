/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@AutoMultiBind
public interface OgcApiQueryParameter extends ParameterExtension {

  default String getStyle() {
    return "form";
  }

  /**
   * Internal parameters are not part of the API definition and are rejected, if they are set by a
   * client. They can only be set by ldproxy itself, e.g. from policy obligations.
   */
  default boolean isInternal() {
    return false;
  }

  boolean matchesPath(String definitionPath);

  boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method);

  boolean isApplicable(
      OgcApiDataV2 apiData, String definitionPath, String collectionId, HttpMethods method);

  default void updateOpenApiDefinition(
      OgcApiDataV2 apiData, Optional<String> collectionId, OpenAPI openAPI, Operation op) {
    String id = getId(collectionId);
    op.addParametersItem(new Parameter().$ref("#/components/parameters/" + id));
    if (Objects.isNull(openAPI.getComponents().getParameters().get(id))) {
      openAPI.getComponents().addParameters(id, newQueryParameter(apiData, collectionId));
    }
  }

  private Parameter newQueryParameter(OgcApiDataV2 apiData, Optional<String> collectionId) {
    Parameter param =
        new QueryParameter()
            .name(getName())
            .required(getRequired(apiData, collectionId))
            .schema(getSchema(apiData, collectionId))
            .style(Parameter.StyleEnum.valueOf(getStyle().toUpperCase(Locale.ROOT)))
            .explode(getExplode());
    setOpenApiDescription(apiData, param);
    return param;
  }
}
