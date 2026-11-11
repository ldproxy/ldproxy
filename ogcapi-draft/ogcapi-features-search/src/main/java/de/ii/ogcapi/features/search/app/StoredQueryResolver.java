/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryExpression;
import de.ii.ogcapi.features.search.domain.ParameterResolver;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.QueryExpressionQueryParameter;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.features.search.domain.StoredQueryResolution;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves a stored query with its parameter values to an executable query expression. The endpoint
 * {@code /search/{queryId}} gets the parameters from the request; a search job pushed without a
 * request gets them as job inputs and resolves them with the same parameter definitions and checks
 * as a GET request.
 */
final class StoredQueryResolver {

  static final String PARAMETER_F = "f";

  private final ExtensionRegistry extensionRegistry;
  private final StoredQueryRepository repository;
  private final SchemaValidator schemaValidator;
  private final Cql cql;

  StoredQueryResolver(
      ExtensionRegistry extensionRegistry,
      StoredQueryRepository repository,
      SchemaValidator schemaValidator,
      Cql cql) {
    this.extensionRegistry = extensionRegistry;
    this.repository = repository;
    this.schemaValidator = schemaValidator;
    this.cql = cql;
  }

  /** A stored query resolved for a job: the query, the parameters, the format and the URI. */
  static final class Resolved {
    private final QueryExpression query;
    private final QueryParameterSet queryParameterSet;
    private final ApiMediaType mediaType;
    private final URI requestUri;

    Resolved(
        QueryExpression query,
        QueryParameterSet queryParameterSet,
        ApiMediaType mediaType,
        URI requestUri) {
      this.query = query;
      this.queryParameterSet = queryParameterSet;
      this.mediaType = mediaType;
      this.requestUri = requestUri;
    }

    QueryExpression getQuery() {
      return query;
    }

    QueryParameterSet getQueryParameterSet() {
      return queryParameterSet;
    }

    ApiMediaType getMediaType() {
      return mediaType;
    }

    URI getRequestUri() {
      return requestUri;
    }
  }

  /** Applies the evaluated parameters to the stored query and resolves its parameter references. */
  static QueryExpression resolve(
      StoredQueryExpression storedQuery,
      QueryParameterSet queryParameterSet,
      SchemaValidator schemaValidator,
      Cql cql) {
    ImmutableStoredQueryExpression.Builder builder =
        new ImmutableStoredQueryExpression.Builder().from(storedQuery);
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof QueryExpressionQueryParameter) {
        ((QueryExpressionQueryParameter) parameter).applyTo(builder, queryParameterSet);
      }
    }

    return new ParameterResolver(queryParameterSet, schemaValidator, cql).visit(builder.build());
  }

  /**
   * Resolves the stored query {@code queryId} with the given parameter values, which include {@code
   * f} for the format.
   *
   * @throws IllegalArgumentException with all problems found, if the query does not exist, a
   *     parameter is unknown or a value is invalid
   */
  Resolved resolveForJob(OgcApi api, String queryId, Map<String, String> parameters) {
    OgcApiDataV2 apiData = api.getData();
    EndpointExtension endpoint = getEndpoint();
    List<OgcApiQueryParameter> definitions =
        getOperation(api, endpoint, queryId).getQueryParameters();
    List<String> errors = validate(apiData, definitions, parameters);

    Optional<ApiMediaType> mediaType = Optional.empty();
    String format = parameters.get(PARAMETER_F);
    if (Objects.isNull(format)) {
      errors.add(String.format("The parameter '%s' is required.", PARAMETER_F));
    } else {
      mediaType =
          endpoint.getMediaTypes(apiData, "/" + queryId, "GET").stream()
              .filter(candidate -> format.equals(candidate.parameter()))
              .findFirst();
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join(" ", errors));
    }

    if (mediaType.isEmpty()) {
      // the parameter is validated against the formats, this is unexpected
      throw new IllegalArgumentException(
          String.format("The format '%s' is not supported for stored queries.", format));
    }

    QueryParameterSet queryParameterSet =
        QueryParameterSet.of(definitions, parameters).evaluate(api, Optional.empty());
    QueryExpression query =
        resolve(repository.get(apiData, queryId), queryParameterSet, schemaValidator, cql);

    URICustomizer requestUri = new URICustomizer(api.getUri());
    requestUri.appendPathSegments("search", queryId);
    parameters.forEach(requestUri::addParameter);

    return new Resolved(
        query, queryParameterSet, mediaType.get(), URI.create(requestUri.toString()));
  }

  /**
   * Resolves the stored query {@code queryId} with the given parameter values, without a format.
   *
   * @throws IllegalArgumentException with all problems found, if the query does not exist, a
   *     parameter is unknown or a value is invalid
   */
  StoredQueryResolution.Resolved resolveParameters(
      OgcApi api, String queryId, Map<String, String> parameters) {
    OgcApiDataV2 apiData = api.getData();
    List<OgcApiQueryParameter> definitions =
        getOperation(api, getEndpoint(), queryId).getQueryParameters();
    List<String> errors = validate(apiData, definitions, parameters);

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join(" ", errors));
    }

    QueryParameterSet queryParameterSet =
        QueryParameterSet.of(definitions, parameters).evaluate(api, Optional.empty());
    QueryExpression query =
        resolve(repository.get(apiData, queryId), queryParameterSet, schemaValidator, cql);

    return new StoredQueryResolution.Resolved(query, queryParameterSet);
  }

  private EndpointExtension getEndpoint() {
    return extensionRegistry.getExtensionsForType(EndpointStoredQuery.class).stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Stored queries are not available."));
  }

  private ApiOperation getOperation(OgcApi api, EndpointExtension endpoint, String queryId) {
    if (!repository.exists(api.getData(), queryId)) {
      throw new IllegalArgumentException(
          String.format("The stored query '%s' does not exist in API '%s'.", queryId, api.getId()));
    }

    return Optional.ofNullable(endpoint.getDefinition(api.getData()))
        .flatMap(definition -> definition.getOperation("/search/" + queryId, "GET"))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "The stored query '%s' cannot be executed in API '%s'.",
                        queryId, api.getId())));
  }

  // the checks of the request dispatcher for the parameters of a GET request
  private static List<String> validate(
      OgcApiDataV2 apiData, List<OgcApiQueryParameter> definitions, Map<String, String> values) {
    List<String> errors = new ArrayList<>();
    List<OgcApiQueryParameter> publicParameters =
        definitions.stream().filter(parameter -> !parameter.isInternal()).toList();

    List<String> unknown =
        values.keySet().stream()
            .filter(
                name ->
                    publicParameters.stream()
                        .noneMatch(parameter -> parameter.getName().equalsIgnoreCase(name)))
            .sorted()
            .toList();
    if (!unknown.isEmpty()) {
      errors.add(
          String.format(
              "The following parameters are rejected: %s. Valid parameters are: %s.",
              String.join(", ", unknown),
              publicParameters.stream()
                  .map(OgcApiQueryParameter::getName)
                  .collect(Collectors.joining(", "))));
    }

    values.forEach(
        (name, value) ->
            publicParameters.stream()
                .filter(parameter -> parameter.getName().equalsIgnoreCase(name))
                .forEach(
                    parameter ->
                        parameter
                            .validate(apiData, Optional.empty(), List.of(value))
                            .ifPresent(errors::add)));

    return errors;
  }
}
