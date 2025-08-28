/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.ValueEncoding.FORMAT;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "queries", defaultFormat = FORMAT.JSON)
@JsonDeserialize(builder = ImmutableStoredQueryExpression.Builder.class)
@SuppressWarnings("PMD.TooManyMethods")
public interface StoredQueryExpression extends StoredValue {

  ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @SuppressWarnings("UnstableApiUsage")
  Funnel<StoredQueryExpression> FUNNEL =
      (from, into) -> {
        try {
          into.putString(MAPPER.writeValueAsString(from.asNode()), StandardCharsets.UTF_8);
        } catch (JsonProcessingException ignore) {
          // ignore
          into.putInt(from.hashCode());
        }
      };

  String SCHEMA_REF = "#/components/schemas/StoredQueryExpression";
  String REF = "$ref";
  String PARAMETER = "$parameter";

  abstract class Builder implements ValueBuilder<StoredQueryExpression> {}

  static StoredQueryExpression of(InputStream requestBody) throws IOException {
    return MAPPER.readValue(requestBody, StoredQueryExpression.class);
  }

  String getId();

  Optional<String> getTitle();

  Optional<String> getDescription();

  List<SingleQueryWithParameters> getQueries();

  // If provided, it must be a list with a single string or parameter
  List<JsonNode> getCollections();

  // CQL2 filter object
  Optional<JsonNode> getFilter();

  // CRS URI or a parameter
  Optional<JsonNode> getFilterCrs();

  // FilterOperator or parameter
  Optional<JsonNode> getFilterOperator();

  // List of string or parameter, or a parameter that is a string array
  Optional<JsonNode> getSortby();

  // List of string or parameter, or a parameter that is a string array
  Optional<JsonNode> getProperties();

  // CRS URI or a parameter
  Optional<JsonNode> getCrs();

  // CRS URI or a parameter
  Optional<JsonNode> getVerticalCrs();

  // Double or Parameter
  Optional<JsonNode> getMaxAllowableOffset();

  // Integer or Parameter
  Optional<JsonNode> getLimit();

  // should not be provided in a stored query as it will be set when executing the query
  Optional<Integer> getOffset();

  // List of string or parameter, or a parameter that is a string array
  Optional<JsonNode> getProfiles();

  // If provided, it must be an object with schemas as the values
  Map<String, JsonNode> getParameters();

  @JsonIgnore
  @Value.Check
  default void check() {
    Preconditions.checkState(
        getQueries().isEmpty() && getCollections().size() == 1
            || !getQueries().isEmpty() && getCollections().isEmpty(),
        "Either one or more queries must be provided or a single collection. Query: %s. Collections: %s.",
        getQueries(),
        getCollections());
    Preconditions.checkState(
        getCollections().stream()
            .allMatch(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER))),
        "Each collection must be a string or a parameter. Collections: %s.",
        getCollections());
    Preconditions.checkState(
        getFilterOperator().isEmpty()
            || getFilterOperator()
                .filter(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER)))
                .isPresent(),
        "The filter operator must be a string or a parameter. Filter operator: %s.",
        getFilterOperator());
    Preconditions.checkState(
        getFilterCrs().isEmpty()
            || getFilterCrs()
                .filter(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER)))
                .isPresent(),
        "The filter CRS must be a string or a parameter. Filter CRS: %s.",
        getFilterCrs());
    Preconditions.checkState(
        getCrs().isEmpty()
            || getCrs()
                .filter(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER)))
                .isPresent(),
        "The CRS must be a string or a parameter. CRS: %s.",
        getCrs());
    Preconditions.checkState(
        getVerticalCrs().isEmpty()
            || getVerticalCrs()
                .filter(v -> v.isTextual() || (v.isObject() && v.has(PARAMETER)))
                .isPresent(),
        "The vertical CRS must be a string or a parameter. Vertical CRS: %s.",
        getVerticalCrs());
    Preconditions.checkState(
        getLimit().isEmpty()
            || getLimit().filter(v -> v.isInt() || (v.isObject() && v.has(PARAMETER))).isPresent(),
        "The limit must be an integer or a parameter. Limit: %s.",
        getLimit());
    Preconditions.checkState(
        getMaxAllowableOffset().isEmpty()
            || getMaxAllowableOffset()
                .filter(v -> v.isDouble() || (v.isObject() && v.has(PARAMETER)))
                .isPresent(),
        "The maxAllowableOffset must be a double or a parameter. MaxAllowableOffset: %s.",
        getMaxAllowableOffset());
    Preconditions.checkState(
        getSortby().isEmpty()
            || getSortby().filter(v -> v.isObject() && v.has(PARAMETER)).isPresent()
            || getSortby()
                .filter(
                    v ->
                        v.isArray()
                            && StreamSupport.stream(v.spliterator(), false)
                                .allMatch(
                                    v2 -> v2.isTextual() || (v2.isObject() && v2.has(PARAMETER))))
                .isPresent(),
        "Sortby must be a list of strings/parameters or a parameter that is a string array. Value: %s.",
        getSortby());
    Preconditions.checkState(
        getProperties().isEmpty()
            || getProperties().filter(v -> v.isObject() && v.has(PARAMETER)).isPresent()
            || getProperties()
                .filter(
                    v ->
                        v.isArray()
                            && StreamSupport.stream(v.spliterator(), false)
                                .allMatch(
                                    v2 -> v2.isTextual() || (v2.isObject() && v2.has(PARAMETER))))
                .isPresent(),
        "Properties must be a list of strings/parameters or a parameter that is a string array. Value: %s.",
        getProperties());
    Preconditions.checkState(
        getProfiles().isEmpty()
            || getProfiles().filter(v -> v.isObject() && v.has(PARAMETER)).isPresent()
            || getProfiles()
                .filter(
                    v ->
                        v.isArray()
                            && StreamSupport.stream(v.spliterator(), false)
                                .allMatch(
                                    v2 -> v2.isTextual() || (v2.isObject() && v2.has(PARAMETER))))
                .isPresent(),
        "Profiles must be a list of strings/parameters or a parameter that is a string array. Value: %s.",
        getProfiles());
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default ObjectNode asNode() {
    return MAPPER.valueToTree(this);
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean hasParameters() {
    return !getParameterNames().isEmpty();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Set<String> getParameterNames() {
    return ImmutableSet.<String>builder().addAll(getParameterNamesFromJson(asNode())).build();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Map<String, JsonNode> getParametersAsNodes() {
    return getParametersFromJson(asNode(), getParameters());
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Map<String, Schema<?>> getParametersWithOpenApiSchema() {
    ImmutableMap.Builder<String, Schema<?>> paramBuilder = ImmutableMap.builder();
    getParametersAsNodes().forEach((key, value) -> paramBuilder.put(key, deriveSchema(value)));
    return paramBuilder.build();
  }

  default QueryExpression resolveParameters(
      QueryParameterSet queryParameterSet, SchemaValidator schemaValidator) {
    ObjectNode query = asNode();

    Map<String, JsonNode> params = getParametersAsNodes();
    if (!params.isEmpty()) {
      ImmutableMap.Builder<String, JsonNode> builder = ImmutableMap.builder();
      JsonNode valueAsNode;
      for (Map.Entry<String, JsonNode> entry : params.entrySet()) {

        // get the JSON Schema of the parameter as a string for validation
        String schemaAsString;
        try {
          schemaAsString = MAPPER.writeValueAsString(entry.getValue());
        } catch (JsonProcessingException e) {
          throw new IllegalStateException(
              String.format(
                  "Could not read the schema of parameter '%s' in a query.", entry.getKey()),
              e);
        }

        // get value as node (for the result)
        if (queryParameterSet.getTypedValues().containsKey(entry.getKey())) {
          valueAsNode = (JsonNode) queryParameterSet.getTypedValues().get(entry.getKey());
        } else {
          // no value provided, use default or throw an exception
          valueAsNode = useDefault(entry);
        }

        validateParameter(schemaValidator, valueAsNode, entry, schemaAsString);
        builder.put(entry.getKey(), valueAsNode);
      }

      replaceParameters(query, builder.build());
    }

    if (query.get("sortby").isNull()) {
      query.putArray("sortby");
    }
    if (query.get("properties").isNull()) {
      query.putArray("properties");
    }
    if (query.get("profiles").isNull()) {
      query.putArray("profiles");
    }
    if (query.get("queries").isNull()) {
      query.putArray("queries");
    } else {
      for (Iterator<JsonNode> it = query.get("queries").elements(); it.hasNext(); ) {
        ObjectNode singleQuery = (ObjectNode) it.next();
        if (singleQuery.get("sortby").isNull()) {
          singleQuery.putArray("sortby");
        }
        if (singleQuery.get("properties").isNull()) {
          singleQuery.putArray("properties");
        }
      }
    }

    query.remove("parameters");

    try {
      return MAPPER.treeToValue(query, QueryExpression.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private JsonNode useDefault(Entry<String, JsonNode> entry) {
    JsonNode valueAsNode;
    valueAsNode = entry.getValue().get("default");
    if (Objects.isNull(valueAsNode) || valueAsNode.isNull()) {
      throw new BadRequestException(
          String.format("No value provided for parameter '%s'.", entry.getKey()));
    }
    return valueAsNode;
  }

  private void validateParameter(
      SchemaValidator schemaValidator,
      JsonNode valueAsNode,
      Entry<String, JsonNode> entry,
      String schemaAsString) {
    final String value;
    try {
      // convert to string for validation
      value = MAPPER.writeValueAsString(valueAsNode);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          String.format(
              "The JSON value derived for parameter '%s' could not be converted to a string value for validation.",
              entry.getKey()),
          e);
    }
    // validate parameter
    try {
      schemaValidator
          .validate(schemaAsString, value)
          .ifPresent(
              error -> {
                throw new BadRequestException(
                    String.format(
                        "Parameter '%s' is invalid, the value '%s' does not conform to the schema '%s'. Reason: %s",
                        entry.getKey(), value, schemaAsString, error));
              });
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format(
              "Could not validate value '%s' of parameter '%s' against its schema '%s'",
              value, entry.getKey(), schemaAsString),
          e);
    }
  }

  private Schema<?> deriveSchema(@NotNull JsonNode schemaNode) {
    if (!schemaNode.isObject()) {
      throw new IllegalArgumentException(
          String.format("Found a parameter without a schema object. Found %s", schemaNode));
    }

    Schema<?> schema;
    if (Objects.nonNull(schemaNode.get(REF))) {
      schema = new ObjectSchema();
      schema.$ref(schemaNode.get(REF).asText());
    } else if (Objects.isNull(schemaNode.get("type"))) {
      throw new IllegalArgumentException(
          "Found a parameter without a 'type' member in the schema.");
    } else {
      schema =
          switch (schemaNode.get("type").asText()) {
            case "object" -> getObjectSchema(schemaNode);
            case "array" -> getArraySchema(schemaNode);
            case "string" -> getStringSchema(schemaNode);
            case "number" -> getNumberSchema(schemaNode);
            case "integer" -> getIntegerSchema(schemaNode);
            case "boolean" -> new BooleanSchema();
            default -> throw new IllegalArgumentException(
                String.format(
                    "Found unsupported 'type'. Found: %s", schemaNode.get("type").asText()));
          };
    }
    setMetadata(schemaNode, schema);
    return schema;
  }

  private Schema<?> getObjectSchema(JsonNode schemaNode) {
    ObjectSchema schema = new ObjectSchema();
    ObjectNode properties = (ObjectNode) schemaNode.get("properties");
    if (Objects.nonNull(properties)) {
      JsonNode node = schemaNode.get("required");
      if (Objects.nonNull(node)) {
        Iterator<JsonNode> iter = node.elements();
        while (iter.hasNext()) {
          schema.addRequiredItem(iter.next().asText());
        }
      }
      Iterator<Entry<String, JsonNode>> iter = properties.fields();
      while (iter.hasNext()) {
        Entry<String, JsonNode> entry = iter.next();
        schema.addProperties(entry.getKey(), deriveSchema(entry.getValue()));
      }
      node = schemaNode.get("minProperties");
      if (Objects.nonNull(node)) {
        schema.minProperties(node.asInt());
      }
      node = schemaNode.get("maxProperties");
      if (Objects.nonNull(node)) {
        schema.maxProperties(node.asInt());
      }
    }
    // see limitation, not supported: patternProperties, additionalProperties, allOf, oneOf
    return schema;
  }

  private Schema<?> getArraySchema(JsonNode schemaNode) {
    ArraySchema schema = new ArraySchema();
    JsonNode node = schemaNode.get("items");
    if (Objects.nonNull(node)) {
      schema.items(deriveSchema(node));
    }
    node = schemaNode.get("minItems");
    if (Objects.nonNull(node)) {
      schema.minItems(node.asInt());
    }
    node = schemaNode.get("maxItems");
    if (Objects.nonNull(node)) {
      schema.maxItems(node.asInt());
    }
    node = schemaNode.get("uniqueObject");
    if (Objects.nonNull(node)) {
      schema.uniqueItems(node.asBoolean());
    }
    // see limitation, not supported: prefixItems, additionalItems, items:false
    return schema;
  }

  private Schema<?> getStringSchema(JsonNode schemaNode) {
    StringSchema schema = new StringSchema();
    JsonNode node = schemaNode.get("format");
    if (Objects.nonNull(node)) {
      schema.format(node.asText());
    }
    node = schemaNode.get("minLength");
    if (Objects.nonNull(node)) {
      schema.minLength(node.asInt());
    }
    node = schemaNode.get("maxLength");
    if (Objects.nonNull(node)) {
      schema.maxLength(node.asInt());
    }
    node = schemaNode.get("pattern");
    if (Objects.nonNull(node)) {
      schema.pattern(node.asText());
    }
    JsonNode enums = schemaNode.get("enum");
    if (Objects.nonNull(enums)) {
      Iterator<JsonNode> iter = enums.elements();
      while (iter.hasNext()) {
        schema.addEnumItem(iter.next().asText());
      }
    }
    return schema;
  }

  private Schema<?> getNumberSchema(JsonNode schemaNode) {
    NumberSchema schema = new NumberSchema();
    JsonNode node = schemaNode.get("multipleOf");
    if (Objects.nonNull(node)) {
      schema.multipleOf(node.decimalValue());
    }
    node = schemaNode.get("minimum");
    if (Objects.nonNull(node)) {
      schema.minimum(node.decimalValue());
    }
    node = schemaNode.get("exclusiveMinimum");
    if (Objects.nonNull(node)) {
      schema.minimum(node.decimalValue());
      schema.exclusiveMinimum(true);
    }
    node = schemaNode.get("maximum");
    if (Objects.nonNull(node)) {
      schema.maximum(node.decimalValue());
    }
    node = schemaNode.get("exclusiveMaximum");
    if (Objects.nonNull(node)) {
      schema.maximum(node.decimalValue());
      schema.exclusiveMaximum(true);
    }
    JsonNode enums = schemaNode.get("enum");
    if (Objects.nonNull(enums)) {
      Iterator<JsonNode> iter = enums.elements();
      while (iter.hasNext()) {
        schema.addEnumItem(iter.next().decimalValue());
      }
    }
    return schema;
  }

  private Schema<?> getIntegerSchema(JsonNode schemaNode) {
    IntegerSchema schema = new IntegerSchema();
    JsonNode node = schemaNode.get("multipleOf");
    if (Objects.nonNull(node)) {
      schema.multipleOf(node.decimalValue());
    }
    node = schemaNode.get("minimum");
    if (Objects.nonNull(node)) {
      schema.minimum(node.decimalValue());
    }
    node = schemaNode.get("exclusiveMinimum");
    if (Objects.nonNull(node)) {
      schema.minimum(node.decimalValue());
      schema.exclusiveMinimum(true);
    }
    node = schemaNode.get("maximum");
    if (Objects.nonNull(node)) {
      schema.maximum(node.decimalValue());
    }
    node = schemaNode.get("exclusiveMaximum");
    if (Objects.nonNull(node)) {
      schema.maximum(node.decimalValue());
      schema.exclusiveMaximum(true);
    }
    JsonNode enums = schemaNode.get("enum");
    if (Objects.nonNull(enums)) {
      Iterator<JsonNode> iter = enums.elements();
      while (iter.hasNext()) {
        schema.addEnumItem(iter.next().decimalValue());
      }
    }
    return schema;
  }

  private void setMetadata(JsonNode schemaNode, Schema<?> schema) {
    JsonNode node = schemaNode.get("title");
    if (Objects.nonNull(node)) {
      schema.title(node.asText());
    }
    node = schemaNode.get("description");
    if (Objects.nonNull(node)) {
      schema.description(node.asText());
    }
    node = schemaNode.get("example");
    if (Objects.nonNull(node)) {
      try {
        schema.example(MAPPER.treeToValue(node, Object.class));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
    node = schemaNode.get("default");
    if (Objects.nonNull(node)) {
      try {
        schema.setDefault(MAPPER.treeToValue(node, Object.class));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private boolean isParameterNode(JsonNode node) {
    return Objects.nonNull(node) && node.isObject() && Objects.nonNull(node.get(PARAMETER));
  }

  private String getParameterName(JsonNode valueNode) {
    if (Objects.nonNull(valueNode) && valueNode.isObject()) {
      if (valueNode.fields().hasNext()) {
        Entry<String, JsonNode> firstMember = valueNode.fields().next();
        if (REF.equals(firstMember.getKey())) {
          if (firstMember.getValue().isTextual()
              && firstMember.getValue().textValue().startsWith("#/parameters/")) {
            return firstMember.getValue().textValue().substring(13);
          } else {
            throw new IllegalArgumentException(
                String.format(
                    "Found '$ref' object where the value is not a JSON Reference to a member in '#/parameters'. Found: %s",
                    firstMember.getValue()));
          }
        } else {
          return firstMember.getKey();
        }
      } else {
        throw new IllegalArgumentException(
            "Found empty '$parameter' object. Either the JSON Schema of the parameter must be provided or a JSON Reference to the top-level 'parameters' member.");
      }
    }

    throw new IllegalArgumentException(
        String.format(
            "Illegal value of a '$parameter' key. The value must be either a local JSON Reference to a parameter defined in the 'parameters' member or a JSON Schema object. Found: %s",
            valueNode));
  }

  private void replaceParameters(JsonNode node, Map<String, JsonNode> params) {
    if (Objects.nonNull(node)) {
      if (node.isArray()) {
        ArrayNode arrayNode = (ArrayNode) node;
        for (int i = 0; i < arrayNode.size(); i++) {
          if (isParameterNode(arrayNode.get(i))) {
            arrayNode.set(i, params.get(getParameterName(arrayNode.get(i).get(PARAMETER))));
          } else {
            replaceParameters(arrayNode.get(i), params);
          }
        }
      } else if (node.isObject()) {
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Entry<String, JsonNode>> iter = objectNode.fields();
        while (iter.hasNext()) {
          Map.Entry<String, JsonNode> entry = iter.next();
          if (isParameterNode(entry.getValue())) {
            objectNode.set(
                entry.getKey(), params.get(getParameterName(entry.getValue().get(PARAMETER))));
          } else {
            replaceParameters(entry.getValue(), params);
          }
        }
      } else if (!node.isNull() && !node.isValueNode()) {
        throw new IllegalStateException(
            String.format("Support for schema type %s not implemented.", node.getNodeType()));
      }
    }
  }

  private Map<String, JsonNode> getParametersFromJson(
      JsonNode node, Map<String, JsonNode> predefinedParameters) {
    if (Objects.isNull(node)) {
      return ImmutableMap.of();
    }

    Map<String, JsonNode> params = ImmutableMap.of();
    if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        params = mergeMaps(params, getParametersFromJson(arrayNode.get(i), predefinedParameters));
      }
    } else if (node.isObject()) {
      JsonNode param = node.get(PARAMETER);
      if (Objects.isNull(param)) {
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
        while (iter.hasNext()) {
          Map.Entry<String, JsonNode> entry = iter.next();
          params = mergeMaps(params, getParametersFromJson(entry.getValue(), predefinedParameters));
        }
      } else if (param.isObject()) {
        String name = getParameterName(param);
        // ignore multiple definitions of the same parameter
        if (!params.containsKey(name)) {
          Map.Entry<String, JsonNode> entry = param.fields().next();
          if (REF.equals(entry.getKey())) {
            if (predefinedParameters.containsKey(name)) {
              params = mergeMaps(params, ImmutableMap.of(name, predefinedParameters.get(name)));
            } else {
              throw new IllegalArgumentException(
                  String.format(
                      "Undefined parameter '%s' referenced in query.", param.textValue()));
            }
          } else {
            params = mergeMaps(params, ImmutableMap.of(entry.getKey(), entry.getValue()));
          }
        }
      }
    }
    return params;
  }

  private Map<String, JsonNode> mergeMaps(
      Map<String, JsonNode> current, Map<String, JsonNode> additional) {
    return Stream.of(current, additional)
        .flatMap(map -> map.entrySet().stream())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (n1, n2) -> n1));
  }

  private Set<String> getParameterNamesFromJson(JsonNode node) {
    if (Objects.isNull(node)) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>();
    if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        builder.addAll(getParameterNamesFromJson(arrayNode.get(i)));
      }
    } else if (node.isObject()) {
      JsonNode param = node.get(PARAMETER);
      if (Objects.isNull(param)) {
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
        while (iter.hasNext()) {
          Map.Entry<String, JsonNode> entry = iter.next();
          builder.addAll(getParameterNamesFromJson(entry.getValue()));
        }
      } else if (param.isObject()) {
        builder.add(getParameterName(param));
      }
    }
    return builder.build();
  }
}
