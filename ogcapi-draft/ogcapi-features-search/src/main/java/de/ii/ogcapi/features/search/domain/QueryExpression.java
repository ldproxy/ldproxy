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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
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
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "queries")
@JsonDeserialize(builder = ImmutableQueryExpression.Builder.class)
@SuppressWarnings("PMD.TooManyMethods")
public interface QueryExpression extends StoredValue {

  String ADHOC_QUERY_ID = "_adhoc_query_";

  @SuppressWarnings("UnstableApiUsage")
  Funnel<QueryExpression> FUNNEL =
      (from, into) -> {
        into.putString(from.getId(), StandardCharsets.UTF_8);
        from.getTitle().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getDescription().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getQueries().forEach(q -> SingleQuery.FUNNEL.funnel(q, into));
        from.getCollections().forEach(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getFilter().forEach((key, value) -> into.putString(key, StandardCharsets.UTF_8));
        from.getFilterOperator().ifPresent(v -> into.putString(v.name(), StandardCharsets.UTF_8));
        from.getSortby().forEach(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getProperties().forEach(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getCrs().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getVerticalCrs().ifPresent(s -> into.putString(s, StandardCharsets.UTF_8));
        from.getMaxAllowableOffset().ifPresent(into::putDouble);
        from.getLimit().ifPresent(into::putInt);
        from.getOffset().ifPresent(into::putInt);
        from.getProfiles().forEach(s -> into.putString(s, StandardCharsets.UTF_8));
      };

  ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  String SCHEMA_REF = "#/components/schemas/QueryExpression";
  String REF = "$ref";
  String PARAMETER = "$parameter";

  enum FilterOperator {
    AND,
    OR
  }

  abstract class Builder implements ValueBuilder<QueryExpression> {}

  static QueryExpression of(byte[] requestBody) throws IOException {
    return MAPPER.readValue(requestBody, QueryExpression.class);
  }

  static QueryExpression of(InputStream requestBody) throws IOException {
    return MAPPER.readValue(requestBody, QueryExpression.class);
  }

  @Value.Default
  default String getId() {
    return ADHOC_QUERY_ID;
  }

  Optional<String> getTitle();

  Optional<String> getDescription();

  List<SingleQuery> getQueries();

  List<String> getCollections(); // String or Parameter

  Map<String, Object> getFilter();

  Optional<String> getFilterCrs();

  Optional<FilterOperator> getFilterOperator();

  List<String> getSortby(); // String or Parameter

  List<String> getProperties(); // String or Parameter

  Optional<String> getCrs(); // String or Parameter

  Optional<String> getVerticalCrs(); // String or Parameter

  Optional<Double> getMaxAllowableOffset(); // Double or Parameter

  Optional<Integer> getLimit(); // Integer or Parameter

  Optional<Integer> getOffset();

  List<String> getProfiles();

  Map<String, JsonNode> getParameters();

  @Value.Check
  default void check() {
    Preconditions.checkState(
        getQueries().isEmpty() && getCollections().size() == 1
            || !getQueries().isEmpty() && getCollections().isEmpty(),
        "Either one or more queries must be provided or a single collection. Query: %s. Collections: %s.",
        getQueries(),
        getCollections());
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
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    builder.addAll(getParameterNamesFromFilter(getFilterAsNode(getFilter())));
    for (SingleQuery q : getQueries()) {
      builder.addAll(getParameterNamesFromFilter(getFilterAsNode(q.getFilter())));
    }
    return builder.build();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default Map<String, JsonNode> getParametersAsNodes() {
    Map<String, JsonNode> params =
        getParametersFromFilter(getFilterAsNode(getFilter()), getParameters());
    for (SingleQuery q : getQueries()) {
      params =
          mergeMaps(
              params, getParametersFromFilter(getFilterAsNode(q.getFilter()), getParameters()));
    }
    return params;
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

      ObjectNode queryNode = MAPPER.valueToTree(this);
      replaceParameters(queryNode, builder.build());
      queryNode.remove("parameters");
      try {
        return MAPPER.treeToValue(queryNode, QueryExpression.class);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
    }
    return this;
  }

  @SuppressWarnings("PMD.NullAssignment")
  private JsonNode getValue(
      String value, StringBuilder valueAsString, Entry<String, JsonNode> entry) {
    JsonNode valueAsNode;
    Schema<?> schema = deriveSchema(entry.getValue());
    if (schema instanceof ArraySchema && !value.trim().startsWith("[")) {
      Schema<?> itemsSchema = ((ArraySchema) schema).getItems();
      if (itemsSchema instanceof StringSchema) {
        valueAsString.append(
            String.format(
                "[\"%s\"]",
                Splitter.on(',')
                    .trimResults()
                    .splitToStream(value)
                    .map(s -> s.replace("\"", "\\\""))
                    .collect(Collectors.joining("\",\""))));
      } else {
        valueAsString.append(
            String.format(
                "[%s]",
                Splitter.on(',')
                    .trimResults()
                    .splitToStream(value)
                    .map(s -> s.replace("\"", "\\\""))
                    .collect(Collectors.joining(","))));
      }
    } else if (schema instanceof ObjectSchema && !value.trim().startsWith("{")) {
      Map<String, Schema> properties = schema.getProperties();
      valueAsString.append('{');
      String key = null;
      for (String s : Splitter.on(',').trimResults().split(value)) {
        if (Objects.isNull(key)) {
          key = s;
          valueAsString.append(String.format("\"%s\":", s));
        } else {
          if (properties.containsKey(key) && properties.get(key) instanceof StringSchema) {
            valueAsString.append(String.format("\"%s\"", s));
          } else {
            valueAsString.append(s);
          }
          key = null;
        }
      }
      valueAsString.append('}');

    } else {
      if (schema instanceof StringSchema) {
        valueAsString.append(String.format("\"%s\"", value.replace("\"", "\\\"")));
      } else {
        valueAsString.append(value);
      }
    }
    try {
      // convert to a JSON Node for processing
      valueAsNode = MAPPER.readTree(valueAsString.toString());
    } catch (JsonProcessingException e) {
      throw new BadRequestException(
          String.format(
              "The value '%s' provided for parameter '%s' could not be converted to a value for the parameter.",
              value, entry.getKey()),
          e);
    }
    return valueAsNode;
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
      switch (schemaNode.get("type").asText()) {
        case "object":
          schema = getObjectSchema(schemaNode);
          break;
        case "array":
          schema = getArraySchema(schemaNode);
          break;
        case "string":
          schema = getStringSchema(schemaNode);
          break;
        case "number":
          schema = getNumberSchema(schemaNode);
          break;
        case "integer":
          schema = getIntegerSchema(schemaNode);
          break;
        case "boolean":
          schema = new BooleanSchema();
          break;
        default:
          throw new IllegalArgumentException(
              String.format(
                  "Found unsupported 'type'. Found: %s", schemaNode.get("type").asText()));
      }
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

  private Map<String, JsonNode> getParametersFromFilter(
      JsonNode node, Map<String, JsonNode> predefinedParameters) {
    if (Objects.isNull(node)) {
      return ImmutableMap.of();
    }

    Map<String, JsonNode> params = ImmutableMap.of();
    if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        params = mergeMaps(params, getParametersFromFilter(arrayNode.get(i), predefinedParameters));
      }
    } else if (node.isObject()) {
      JsonNode param = node.get(PARAMETER);
      if (Objects.isNull(param)) {
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
        while (iter.hasNext()) {
          Map.Entry<String, JsonNode> entry = iter.next();
          params =
              mergeMaps(params, getParametersFromFilter(entry.getValue(), predefinedParameters));
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

  private Set<String> getParameterNamesFromFilter(JsonNode node) {
    if (Objects.isNull(node)) {
      return ImmutableSet.of();
    }

    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>();
    if (node.isArray()) {
      ArrayNode arrayNode = (ArrayNode) node;
      for (int i = 0; i < arrayNode.size(); i++) {
        builder.addAll(getParameterNamesFromFilter(arrayNode.get(i)));
      }
    } else if (node.isObject()) {
      JsonNode param = node.get(PARAMETER);
      if (Objects.isNull(param)) {
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
        while (iter.hasNext()) {
          Map.Entry<String, JsonNode> entry = iter.next();
          builder.addAll(getParameterNamesFromFilter(entry.getValue()));
        }
      } else if (param.isObject()) {
        builder.add(getParameterName(param));
      }
    }
    return builder.build();
  }

  private JsonNode getFilterAsNode(Map<String, Object> filter) {
    return MAPPER.valueToTree(filter);
  }
}
