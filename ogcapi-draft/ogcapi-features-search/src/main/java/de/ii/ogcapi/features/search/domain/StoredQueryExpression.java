/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Set;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
public interface StoredQueryExpression extends StoredQueryBase, StoredQueryComponent {

  ObjectMapper MAPPER = QueryExpression.MAPPER;

  @SuppressWarnings("UnstableApiUsage")
  Funnel<StoredQueryExpression> FUNNEL =
      (from, into) -> {
        try {
          into.putString(MAPPER.writeValueAsString(from), StandardCharsets.UTF_8);
        } catch (JsonProcessingException ignore) {
          // ignore
          into.putInt(from.hashCode());
        }
      };

  String SCHEMA_REF = "#/components/schemas/StoredQueryExpression";
  String REF = "$ref";
  String PARAMETER = "$parameter";

  static StoredQueryExpression of(InputStream requestBody) throws IOException {
    return MAPPER.readValue(requestBody, StoredQueryExpression.class);
  }

  static StoredQueryExpression from(StoredQueryValue storedQueryValue, Cql cql) {
    Map<String, JsonSchema> allParameters =
        storedQueryValue.accept(new ParametersInStoredQuery(storedQueryValue.getParameters(), cql));

    return new ImmutableStoredQueryExpression.Builder()
        .from(storedQueryValue)
        .allParameters(allParameters)
        .build();
  }

  @JsonIgnore
  @Value.Auxiliary
  Map<String, JsonSchema> getAllParameters();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean hasParameters() {
    return !getAllParameters().isEmpty();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Set<String> getParameterNames() {
    return getAllParameters().keySet();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Map<String, Schema<?>> getParametersWithOpenApiSchema() {
    OpenApiSchemaDeriver schemaDeriver = new OpenApiSchemaDeriver();
    return getAllParameters().entrySet().stream()
        .map(
            entry ->
                new SimpleImmutableEntry<>(entry.getKey(), entry.getValue().accept(schemaDeriver)))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
