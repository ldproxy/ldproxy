/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import de.ii.ogcapi.features.search.domain.ParameterFormat;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;

/** The name of a parameter of one stored query, the values are the parameters of the query. */
class PathParameterStoredQueryParameterName implements OgcApiPathParameter {

  private final SchemaValidator schemaValidator;
  private final String queryId;
  private final List<String> names;

  PathParameterStoredQueryParameterName(
      SchemaValidator schemaValidator, String queryId, List<String> names) {
    this.schemaValidator = schemaValidator;
    this.queryId = queryId;
    this.names = names.stream().sorted().toList();
  }

  // the parameters of the stored queries have ids "{name}_{queryId}", the dot cannot occur in them
  @Override
  public String getId() {
    return String.format("storedQuery.%s.parameterName", queryId);
  }

  @Override
  public String getName() {
    return "name";
  }

  @Override
  public String getDescription() {
    return String.format("The name of a parameter of the stored query '%s'.", queryId);
  }

  @Override
  public String getPattern() {
    return ParameterFormat.PARAMETER_NAME_PATTERN;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return names;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return new StringSchema()._enum(names);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return definitionPath.equals(String.format("/search/%s/parameters/{name}", queryId));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }
}
