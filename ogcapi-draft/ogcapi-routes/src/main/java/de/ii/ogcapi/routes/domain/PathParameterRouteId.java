/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title routeId
 * @endpoints Route, Route Definition
 * @langEn The local identifier of the route.
 * @langDe Der lokale Identifikator der Route.
 */
@Singleton
@AutoBind
public class PathParameterRouteId implements OgcApiPathParameter {

  private static final Logger LOGGER = LoggerFactory.getLogger(PathParameterRouteId.class);

  public static final String ROUTE_ID_PATTERN = "[a-z0-9]+";

  protected final SchemaValidator schemaValidator;

  @Inject
  PathParameterRouteId(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getPattern() {
    return ROUTE_ID_PATTERN;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return ImmutableList.of();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new StringSchema().pattern(getPattern());
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public String getName() {
    return "routeId";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a route, unique within the API.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && (definitionPath.endsWith("/routes/{routeId}")
            || definitionPath.endsWith("/routes/{routeId}/definition"));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return RoutingConfiguration.class;
  }
}
