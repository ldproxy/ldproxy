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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

  /**
   * Reads a stored query from a request body in the format of the stored query files. The id is the
   * one of the request path, an id in the body is not required.
   */
  static StoredQueryExpression of(InputStream requestBody, String id, Cql cql) throws IOException {
    JsonNode body = MAPPER.readTree(requestBody);
    // also an empty body or the JSON literal null
    if (body == null || !body.isObject()) {
      throw new IOException("the request body does not contain a query expression");
    }

    ((ObjectNode) body).put("id", id);
    StoredQueryValue value = MAPPER.readerFor(StoredQueryValue.class).readValue(body);

    // also derives the schemas of all parameters, as for the stored query files
    return from(value, cql);
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
