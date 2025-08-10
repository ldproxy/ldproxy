/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HttpRequestOverrideQueryParameter;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.web.domain.JsonPretty;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.container.ContainerRequestContext;

public abstract class QueryParameterPretty extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Boolean>, HttpRequestOverrideQueryParameter {

  private Schema<?> schema = null;
  private final SchemaValidator schemaValidator;

  public QueryParameterPretty(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "pretty";
  }

  @Override
  public Boolean parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // default value
      return false;
    }

    return Boolean.parseBoolean(value);
  }

  @Override
  public void applyTo(ContainerRequestContext requestContext, QueryParameterSet parameters) {
    if (parameters.getTypedValues().containsKey(getName())) {
      Boolean value = (Boolean) parameters.getTypedValues().get(getName());
      requestContext.setProperty(JsonPretty.JSON_PRETTY_HEADER, value.toString());
    }
  }

  @Override
  public String getDescription() {
    return "Controls whether the response content should be pretty-printed. Only applicable for JSON responses.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    if (schema == null) {
      schema = new BooleanSchema()._default(false);
    }
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }
}
