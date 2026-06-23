/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql.Format;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.CqlVisitorExtractParameters;
import de.ii.xtraplatform.cql.domain.CustomFunction;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class StoredQueryValidator implements StoredQueryVisitor<List<String>> {

  private final Map<String, JsonSchema> globalParameters;
  private final Optional<SchemaValidator> schemaValidator;
  private final Optional<Cql> cql;
  private final Optional<OgcApiDataV2> apiData;
  private final Optional<FeaturesCoreProviders> providers;
  private final ObjectMapper mapper;

  public StoredQueryValidator(Map<String, JsonSchema> globalParameters) {
    this(globalParameters, null, null);
  }

  public StoredQueryValidator(
      Map<String, JsonSchema> globalParameters, SchemaValidator schemaValidator, Cql cql) {
    this(globalParameters, schemaValidator, cql, null, null);
  }

  public StoredQueryValidator(
      Map<String, JsonSchema> globalParameters,
      SchemaValidator schemaValidator,
      Cql cql,
      OgcApiDataV2 apiData,
      FeaturesCoreProviders providers) {
    this.globalParameters = globalParameters;
    this.schemaValidator = Optional.ofNullable(schemaValidator);
    this.cql = Optional.ofNullable(cql);
    this.apiData = Optional.ofNullable(apiData);
    this.providers = Optional.ofNullable(providers);
    this.mapper = new ObjectMapper();
  }

  public List<String> visit(StoredQueryExpression storedQuery) {
    if (Objects.isNull(storedQuery)) {
      return List.of();
    }

    List<String> errors = new ArrayList<>();

    errors.addAll(visit((StoredQueryBase) storedQuery));

    if (schemaValidator.isPresent()
        && cql.isPresent()
        && storedQuery.getAllParameters().values().stream()
            .allMatch(schema -> schema.getDefault_().isPresent())) {
      try {
        new ParameterResolver(QueryParameterSet.of(), schemaValidator.get(), cql.get())
            .visit(storedQuery);
      } catch (Exception e) {
        errors.add("Stored query is invalid with the default parameter values: " + e.getMessage());
      }
    }

    return List.copyOf(errors);
  }

  public List<String> visit(StoredQueryBase storedQuery) {
    if (Objects.isNull(storedQuery)) {
      return List.of();
    }

    List<String> errors = new ArrayList<>();

    storedQuery.getCollections().forEach(v -> errors.addAll(v.accept(this)));
    storedQuery.getCrs().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getVerticalCrs().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getFilterCrs().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getFilterOperator().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getLimit().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getMaxAllowableOffset().ifPresent(v -> errors.addAll(v.accept(this)));

    storedQuery.getProfiles().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getProperties().ifPresent(v -> errors.addAll(v.accept(this)));
    storedQuery.getSortby().ifPresent(v -> errors.addAll(v.accept(this)));

    if (storedQuery.getFilter().isPresent() && cql.isPresent()) {
      try {
        // we don't have the filterCrs here, but it is not relevant for parameter extraction, so we
        // use CRS84
        Cql2Expression expression =
            cql.get()
                .read(
                    mapper.writeValueAsString(storedQuery.getFilter().get()),
                    Format.JSON,
                    OgcCrs.CRS84,
                    true);
        expression.accept(new CqlVisitorExtractParameters(globalParameters), true).values().stream()
            .map(this::checkSchema)
            .forEach(errors::addAll);

        // the global filter applies to every (sub-)query, so validate it against each target
        // collection's queryables (the top-level collections when there are no sub-queries)
        targetCollections(storedQuery)
            .forEach(collectionId -> errors.addAll(validateFilter(expression, collectionId)));
      } catch (JsonProcessingException e) {
        errors.add("CQL filter is invalid: " + e.getMessage());
      }
    }

    storedQuery
        .getQueries()
        .forEach(
            query -> {
              errors.addAll(query.accept(this));
            });

    return List.copyOf(errors);
  }

  public List<String> visit(SingleQueryWithParameters query) {
    List<String> errors = new ArrayList<>();
    query.getCollections().forEach(v -> errors.addAll(v.accept(this)));
    query.getProperties().ifPresent(v -> errors.addAll(v.accept(this)));
    query.getSortby().ifPresent(v -> errors.addAll(v.accept(this)));
    query
        .getFilter()
        .ifPresent(
            v -> {
              v.accept(new CqlVisitorExtractParameters(globalParameters), true).values().stream()
                  .map(this::checkSchema)
                  .forEach(errors::addAll);
              query.getCollections().stream()
                  .findFirst()
                  .flatMap(StringOrParameter::getValue)
                  .ifPresent(collectionId -> errors.addAll(validateFilter(v, collectionId)));
            });
    return List.copyOf(errors);
  }

  @Override
  public List<String> visit(ParameterValue param) {
    if (param.getSchema() instanceof JsonSchemaRef ref) {
      if (ref.getRef().startsWith("#/parameters/")) {
        String name = ref.getRef().substring("#/parameters/".length());
        if (!globalParameters.containsKey(name)) {
          return List.of("Parameter '" + name + "' is not defined in global parameters.");
        }
      } else {
        return List.of("Only local parameter references are supported, found: " + ref.getRef());
      }
    }
    return List.of();
  }

  public List<String> visit(StringOrParameter value) {
    return value
        .getParameter()
        .map(param -> checkParameter(param, JsonSchemaString.class))
        .orElse(List.of());
  }

  public List<String> visit(IntegerOrParameter value) {
    return value
        .getParameter()
        .map(param -> checkParameter(param, JsonSchemaInteger.class))
        .orElse(List.of());
  }

  public List<String> visit(DoubleOrParameter value) {
    return value
        .getParameter()
        .map(param -> checkParameter(param, JsonSchemaNumber.class))
        .orElse(List.of());
  }

  public List<String> visit(FilterOperatorOrParameter value) {
    return value
        .getParameter()
        .map(
            param -> {
              List<String> errors = checkParameter(param, JsonSchemaString.class);
              if (errors.isEmpty()) {
                // Further check that the parameter schema is an enum
                JsonSchema effectiveSchema = param.getSchema();
                if (effectiveSchema instanceof JsonSchemaRef ref) {
                  String name = ref.getRef().substring("#/parameters/".length());
                  effectiveSchema = globalParameters.get(name);
                }
                if (effectiveSchema instanceof JsonSchemaString string) {
                  if (string.getEnums().isEmpty()) {
                    errors =
                        List.of(
                            "The parameter schema for the filter operator must be a string limited to the values 'AND' and 'OR'. The parameter schema does not specify 'enum' values.");
                  } else if (string.getEnums().get().size() > 2) {
                    errors =
                        List.of(
                            String.format(
                                "The parameter schema for the filter operator must be a string limited to the values 'AND' and 'OR'. Found: %s",
                                String.join(", ", string.getEnums().get())));
                  } else if (string.getEnums().get().stream()
                      .anyMatch(item -> !"AND".equals(item) && !"OR".equals(item))) {
                    errors =
                        List.of(
                            String.format(
                                "The parameter schema for the filter operator must be a string limited to the values 'AND' and 'OR'. Found: %s",
                                String.join(", ", string.getEnums().get())));
                  }
                } else {
                  errors =
                      List.of(
                          String.format(
                              "The parameter schema for a filter operator must be a string limited to the values 'AND' and 'OR'. Found: %s",
                              effectiveSchema.getClass().getSimpleName()));
                }
              }
              return errors;
            })
        .orElse(List.of());
  }

  public List<String> visit(ParameterOrListOfStringOrParameter value) {
    return value
        .getParameter()
        .map(param -> checkParameter(param, JsonSchemaString.class))
        .or(
            () ->
                value
                    .getValue()
                    .map(
                        list -> {
                          List<String> errors = new ArrayList<>();
                          list.stream()
                              .map(StringOrParameter::getParameter)
                              .filter(Optional::isPresent)
                              .forEach(
                                  v ->
                                      errors.addAll(
                                          checkParameter(v.get(), JsonSchemaString.class)));
                          return List.copyOf(errors);
                        }))
        .orElse(List.of());
  }

  private List<String> checkParameter(ParameterValue param, Class<?> clazz) {
    return checkParameter(param.getSchema(), clazz);
  }

  private List<String> checkSchema(JsonSchema schema) {
    return checkParameter(schema, null);
  }

  /**
   * The collections a global (top-level) filter must be valid against: the first collection of each
   * sub-query when there are sub-queries, otherwise the top-level collections. Parameterized
   * collection references (no literal value) cannot be resolved at validation time and are skipped.
   */
  private List<String> targetCollections(StoredQueryBase storedQuery) {
    List<StringOrParameter> collections =
        storedQuery.getQueries().isEmpty()
            ? storedQuery.getCollections()
            : storedQuery.getQueries().stream()
                .map(SingleQueryWithParameters::getCollections)
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .collect(Collectors.toList());
    return collections.stream()
        .map(StringOrParameter::getValue)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * Validates a (sub-)query filter against the queryables of a collection at registration time,
   * mirroring the request-time check in {@code SearchQueriesHandlerImpl}: unknown or forbidden
   * properties and type mismatches are reported as errors so a broken stored query is surfaced at
   * startup rather than on first invocation. Unbound parameters in the filter are treated as
   * wildcards by the type checker. Coordinate range checks are left to request time, where the
   * filterCrs and parameter values are known.
   */
  private List<String> validateFilter(Cql2Expression filter, String collectionId) {
    if (apiData.isEmpty() || providers.isEmpty() || cql.isEmpty()) {
      return List.of();
    }
    OgcApiDataV2 data = apiData.get();
    Optional<FeatureTypeConfigurationOgcApi> collectionData = data.getCollectionData(collectionId);
    if (collectionData.isEmpty()) {
      return List.of();
    }
    Map<String, FeatureSchema> queryables =
        collectionData
            .get()
            .getExtension(QueryablesConfiguration.class)
            .map(qc -> qc.getQueryables(data, collectionData.get(), providers.get()))
            .orElse(Map.of());
    if (queryables.isEmpty()) {
      return List.of();
    }

    List<String> errors = new ArrayList<>();
    List<String> invalidProperties = cql.get().findInvalidProperties(filter, queryables.keySet());
    if (!invalidProperties.isEmpty()) {
      errors.add(
          String.format(
              "The filter of a query for collection '%s' uses unknown or forbidden properties: %s.",
              collectionId, String.join(", ", invalidProperties)));
    }

    Map<String, String> propertyTypes =
        queryables.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Entry::getKey, entry -> entry.getValue().getType().toString()));
    List<CustomFunction> customFunctions =
        providers
            .get()
            .getFeatureProvider(data, collectionData.get())
            .map(FeatureProvider::queries)
            .filter(OptionalVolatileCapability::isSupported)
            .map(OptionalVolatileCapability::get)
            .map(FeatureQueries::getCql2Functions)
            .orElse(List.of());
    try {
      cql.get().checkTypes(filter, propertyTypes, customFunctions);
    } catch (IllegalArgumentException | IllegalStateException e) {
      errors.add(
          String.format(
              "The filter of a query for collection '%s' is invalid: %s",
              collectionId, e.getMessage()));
    }
    return errors;
  }

  private List<String> checkParameter(JsonSchema schema, Class<?> clazz) {
    if (schema instanceof JsonSchemaRef ref) {
      if (ref.getRef().startsWith("#/parameters/")) {
        String name = ref.getRef().substring("#/parameters/".length());
        if (!globalParameters.containsKey(name)) {
          return List.of(String.format("Parameter '%s' is is not specified.", name));
        }
        if (Objects.nonNull(clazz)
            && !clazz.isAssignableFrom(globalParameters.get(name).getClass())) {
          return List.of(
              String.format(
                  "Parameter '%s' must be of type %s. Found: %s",
                  name,
                  clazz.getSimpleName(),
                  globalParameters.get(name).getClass().getSimpleName()));
        }
      } else {
        return List.of(
            String.format(
                "Only local parameter references are supported. Found: %s", ref.getRef()));
      }
    }
    return List.of();
  }
}
