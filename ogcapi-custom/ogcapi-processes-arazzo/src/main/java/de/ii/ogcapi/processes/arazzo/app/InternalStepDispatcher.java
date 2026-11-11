/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterProfile;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeature;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.features.search.domain.ImmutableQueryExpression;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueryResolution;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestAuthorizer;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.ProcessData;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.web.domain.URICustomizer;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs the requests of steps in-process with the query handlers of the API, like the tools of the
 * MCP endpoint: the features of a collection, a feature, a stored query, an ad-hoc query and the
 * execution of a process. The content of a response is JSON (GeoJSON for features). The request
 * needs the permissions of the operation, as a request to the API without credentials.
 */
@Singleton
@AutoBind
public class InternalStepDispatcher implements StepDispatcher {

  private static final ObjectMapper MAPPER = Jobs.DEFAULT_MAPPER;
  private static final String GET = "GET";
  private static final String POST = "POST";
  private static final String COLLECTIONS = "collections";
  private static final String SEARCH = "search";
  private static final String PROCESSES = "processes";
  private static final ApiMediaType GEOJSON =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "geo+json"))
          .label("GeoJSON")
          .parameter("json")
          .build();
  private static final ApiMediaType JSON =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.APPLICATION_JSON_TYPE)
          .label("JSON")
          .parameter("json")
          .build();
  private static final Set<String> JSON_FORMATS = Set.of("json", "geojson");

  private final AppContext appContext;
  private final EntityRegistry entityRegistry;
  private final ExtensionRegistry extensionRegistry;
  private final FeaturesCoreProviders providers;
  private final FeaturesQuery featuresQuery;
  private final FeaturesCoreQueriesHandler featuresHandler;
  private final SearchQueriesHandler searchHandler;
  private final StoredQueryResolution storedQueryResolution;
  private final ProcessRepository processRepository;
  private final ProcessesExecutor processesExecutor;
  private final ApiRequestAuthorizer authorizer;
  private final WorkflowSlots workflowSlots;

  @Inject
  public InternalStepDispatcher(
      AppContext appContext,
      EntityRegistry entityRegistry,
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesQuery featuresQuery,
      FeaturesCoreQueriesHandler featuresHandler,
      SearchQueriesHandler searchHandler,
      StoredQueryResolution storedQueryResolution,
      ProcessRepository processRepository,
      ProcessesExecutor processesExecutor,
      ApiRequestAuthorizer authorizer,
      WorkflowSlots workflowSlots) {
    this.appContext = appContext;
    this.entityRegistry = entityRegistry;
    this.extensionRegistry = extensionRegistry;
    this.providers = providers;
    this.featuresQuery = featuresQuery;
    this.featuresHandler = featuresHandler;
    this.searchHandler = searchHandler;
    this.storedQueryResolution = storedQueryResolution;
    this.processRepository = processRepository;
    this.processesExecutor = processesExecutor;
    this.authorizer = authorizer;
    this.workflowSlots = workflowSlots;
  }

  @Override
  public Optional<String> apiOfSource(String url, String apiId) {
    Optional<OgcApi> current = entityRegistry.getEntity(OgcApi.class, apiId);
    if (current.isEmpty()) {
      return Optional.empty();
    }
    URI source;
    try {
      // the document of an API is /{apiId}/api, the format is not relevant
      source = new URI(url.replaceAll("[?#].*$", ""));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    URI resolved = current.get().getUri().resolve(source);
    String target = resolved.toString().replaceAll("/$", "");
    return entityRegistry.getEntitiesForType(OgcApi.class).stream()
        .filter(api -> target.equals(api.getUri().toString().replaceAll("/$", "") + "/api"))
        .map(OgcApi::getId)
        .findFirst();
  }

  @Override
  public Optional<Operation> operation(String apiId, String operationId) {
    return operations(apiId).filter(op -> op.operationId().equals(operationId)).findFirst();
  }

  @Override
  public Optional<Operation> operation(String apiId, String method, String path) {
    return operations(apiId)
        .filter(op -> op.method().equalsIgnoreCase(method) && op.path().equals(path))
        .findFirst();
  }

  private Stream<Operation> operations(String apiId) {
    return entityRegistry.getEntity(OgcApi.class, apiId).stream()
        .flatMap(api -> apiOperations(api.getData()))
        .map(
            entry ->
                new Operation(
                    apiId,
                    entry.operation().getOperationId(),
                    entry.method(),
                    entry.path(),
                    isSupported(entry.method(), segments(entry.path()))));
  }

  private record ApiOperationEntry(String method, String path, ApiOperation operation) {}

  private Stream<ApiOperationEntry> apiOperations(OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
        .filter(endpoint -> endpoint.isEnabledForApi(apiData))
        .flatMap(endpoint -> endpoint.getDefinition(apiData).getResources().values().stream())
        .flatMap(
            resource ->
                resource.getOperations().entrySet().stream()
                    .map(
                        entry ->
                            new ApiOperationEntry(
                                entry.getKey().toUpperCase(Locale.ROOT),
                                resource.getPath(),
                                entry.getValue())));
  }

  // the operations that have a handler here
  static boolean isSupported(String method, List<String> segments) {
    int size = segments.size();
    String first = size > 0 ? segments.get(0) : "";
    if (GET.equals(method)) {
      return COLLECTIONS.equals(first)
              && (size == 3 || size == 4)
              && "items".equals(segments.get(2))
          || SEARCH.equals(first) && size == 2;
    }
    if (POST.equals(method)) {
      return SEARCH.equals(first) && (size == 1 || size == 2)
          || PROCESSES.equals(first) && size == 3 && "execution".equals(segments.get(2));
    }
    return false;
  }

  private static List<String> segments(String path) {
    return Stream.of(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
  }

  @Override
  public StepResponse dispatch(StepRequest request, int maximumResponseSize) {
    try {
      OgcApi api =
          entityRegistry
              .getEntity(OgcApi.class, request.apiId())
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          String.format("The API '%s' does not exist.", request.apiId())));
      ApiOperation operation =
          apiOperations(api.getData())
              .filter(entry -> entry.operation().getOperationId().equals(request.operationId()))
              .map(ApiOperationEntry::operation)
              .findFirst()
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          String.format(
                              "The operation '%s' does not exist.", request.operationId())));
      checkFormat(request);
      List<String> segments = segments(request.path());
      Response response = route(api, operation, request, segments);
      return toStepResponse(response, maximumResponseSize);
    } catch (WebApplicationException e) {
      return error(e.getResponse().getStatus(), e.getMessage());
    } catch (IllegalArgumentException e) {
      return error(400, e.getMessage());
    }
  }

  private Response route(
      OgcApi api, ApiOperation operation, StepRequest request, List<String> segments) {
    String method = request.method();
    if (!isSupported(method, segments)) {
      throw new IllegalArgumentException(
          String.format(
              "The operation '%s' (%s %s) cannot be a step of a workflow.",
              request.operationId(), method, request.path()));
    }
    String first = segments.get(0);
    if (COLLECTIONS.equals(first)) {
      return segments.size() == 3
          ? items(api, operation, request, segments.get(1))
          : feature(api, operation, request, segments.get(1), segments.get(3));
    }
    if (SEARCH.equals(first)) {
      return segments.size() == 1
          ? adHocQuery(api, operation, request)
          : storedQuery(api, operation, request, segments.get(1));
    }
    return execute(api, operation, request, segments.get(1));
  }

  // --- features

  private Response items(
      OgcApi api, ApiOperation operation, StepRequest request, String collectionId) {
    OgcApiDataV2 apiData = api.getData();
    FeatureTypeConfigurationOgcApi collectionData = collection(apiData, collectionId);
    FeaturesCoreConfiguration coreConfiguration = featuresCore(collectionData);
    ApiRequestContext requestContext =
        authorize(
            operation,
            context(api, request, GEOJSON)
                .queryParameterSet(
                    parameters(operation, request).evaluate(api, Optional.of(collectionData)))
                .build(),
            Optional.empty());

    FeatureQuery query =
        featuresQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            coreConfiguration.getDefaultPageSize(),
            requestContext.getQueryParameterSet(),
            false);

    return featuresHandler.handle(
        FeaturesCoreQueriesHandler.Query.FEATURES,
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(collectionId)
            .query(query)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData, collectionData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(coreConfiguration.getDefaultPageSize()))
            .profiles(requestedProfiles(requestContext))
            .defaultProfilesResource(defaultProfiles(coreConfiguration))
            .sendResponseAsStream(false)
            .build(),
        requestContext);
  }

  private Response feature(
      OgcApi api,
      ApiOperation operation,
      StepRequest request,
      String collectionId,
      String featureId) {
    OgcApiDataV2 apiData = api.getData();
    FeatureTypeConfigurationOgcApi collectionData = collection(apiData, collectionId);
    FeaturesCoreConfiguration coreConfiguration = featuresCore(collectionData);
    ApiRequestContext requestContext =
        authorize(
            operation,
            context(api, request, GEOJSON)
                .queryParameterSet(
                    parameters(operation, request).evaluate(api, Optional.of(collectionData)))
                .build(),
            Optional.empty());

    FeatureQuery query =
        featuresQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            requestContext.getQueryParameterSet(),
            featureId,
            Optional.empty(),
            SchemaBase.Scope.RETURNABLE,
            false);

    return featuresHandler.handle(
        FeaturesCoreQueriesHandler.Query.FEATURE,
        new ImmutableQueryInputFeature.Builder()
            .collectionId(collectionId)
            .featureId(featureId)
            .query(query)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData, collectionData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .profiles(requestedProfiles(requestContext))
            .defaultProfilesResource(defaultProfiles(coreConfiguration))
            .build(),
        requestContext);
  }

  // as in a request of the API: the profiles of the parameter profile, else the defaults
  @SuppressWarnings("unchecked")
  private static List<Profile> requestedProfiles(ApiRequestContext requestContext) {
    return (List<Profile>)
        Objects.requireNonNullElse(
            requestContext
                .getQueryParameterSet()
                .getTypedValues()
                .get(QueryParameterProfile.PROFILE),
            List.of());
  }

  private List<Profile> defaultProfiles(FeaturesCoreConfiguration coreConfiguration) {
    return extensionRegistry.getExtensionsForType(Profile.class).stream()
        .filter(
            profile ->
                profile
                    .getId()
                    .equals(coreConfiguration.getDefaultProfiles().get(profile.getProfileSet())))
        .toList();
  }

  private static FeatureTypeConfigurationOgcApi collection(
      OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollectionData(collectionId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    String.format("The collection '%s' does not exist.", collectionId)));
  }

  private static FeaturesCoreConfiguration featuresCore(
      FeatureTypeConfigurationOgcApi collectionData) {
    return collectionData
        .getExtension(FeaturesCoreConfiguration.class)
        .filter(ExtensionConfiguration::isEnabled)
        .orElseThrow(
            () ->
                new NotFoundException(
                    String.format(
                        "Features are not supported in collection '%s'.", collectionData.getId())));
  }

  // --- search

  private Response storedQuery(
      OgcApi api, ApiOperation operation, StepRequest request, String queryId) {
    // the parameters of a POST request are the members of the content
    Map<String, String> values =
        POST.equals(request.method()) ? bodyParameters(request) : withoutFormat(request.query());
    StoredQueryResolution.Resolved resolved = storedQueryResolution.resolve(api, queryId, values);
    ApiRequestContext requestContext =
        authorize(
            operation,
            context(api, request, GEOJSON)
                .queryParameterSet(resolved.getQueryParameterSet())
                .build(),
            request.body().map(InternalStepDispatcher::toBytes));
    return searchHandler.handle(
        SearchQueriesHandler.Query.QUERY,
        queryInput(api.getData(), resolved.getQuery(), true),
        requestContext);
  }

  private Response adHocQuery(OgcApi api, ApiOperation operation, StepRequest request) {
    byte[] body =
        request
            .body()
            .map(InternalStepDispatcher::toBytes)
            .orElseThrow(() -> new IllegalArgumentException("An ad-hoc query needs a content."));
    QueryExpression query;
    try {
      query = QueryExpression.of(new ByteArrayInputStream(body));
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "The content of the query expression is invalid: " + e.getMessage(), e);
    }
    // as for a request of the API: no paging, there is no resource to page through
    if (query.getSupportPaging().orElse(false)) {
      throw new IllegalArgumentException("Paging is not supported for ad-hoc queries.");
    }
    query = new ImmutableQueryExpression.Builder().from(query).supportPaging(false).build();
    ApiRequestContext requestContext =
        authorize(
            operation,
            context(api, request, GEOJSON)
                .queryParameterSet(parameters(operation, request).evaluate(api, Optional.empty()))
                .build(),
            Optional.of(body));
    return searchHandler.handle(
        SearchQueriesHandler.Query.QUERY, queryInput(api.getData(), query, false), requestContext);
  }

  private SearchQueriesHandler.QueryInputQuery queryInput(
      OgcApiDataV2 apiData, QueryExpression query, boolean isStoredQuery) {
    FeaturesCoreConfiguration coreConfiguration =
        apiData
            .getExtension(FeaturesCoreConfiguration.class)
            .orElseThrow(() -> new NotFoundException("The API has no features."));
    return new ImmutableQueryInputQuery.Builder()
        .query(query)
        .featureProvider(providers.getFeatureProviderOrThrow(apiData))
        .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
        .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
        .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
        .maximumPageSize(Optional.ofNullable(coreConfiguration.getMaximumPageSize()))
        .allLinksAreLocal(
            apiData
                .getExtension(SearchConfiguration.class)
                .map(SearchConfiguration::getAllLinksAreLocal)
                .orElse(false))
        .isStoredQuery(isStoredQuery)
        .build();
  }

  // --- processes

  private Response execute(
      OgcApi api, ApiOperation operation, StepRequest request, String processId) {
    OgcApiDataV2 apiData = api.getData();
    ProcessesCoreConfiguration configuration =
        apiData
            .getExtension(ProcessesCoreConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled)
            .orElseThrow(() -> new NotFoundException("The API has no processes."));
    ProcessData process =
        processRepository
            .get(apiData.getId(), processId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format("The process '%s' does not exist.", processId)));
    Object body = request.body().orElse(Map.of());
    OgcExecute executeRequest;
    try {
      executeRequest = MAPPER.convertValue(body, OgcExecute.class);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("The execute request is invalid: " + e.getMessage(), e);
    }
    authorize(
        operation,
        context(api, request, JSON)
            .queryParameterSet(QueryParameterSet.of(List.of(), Map.of()))
            .build(),
        Optional.of(toBytes(body)));
    // the job of the process needs a job slot next to the one of the workflow
    Optional<String> noSlot = workflowSlots.executeProcess(processId);
    if (noSlot.isPresent()) {
      throw new WebApplicationException(noSlot.get(), Response.Status.SERVICE_UNAVAILABLE);
    }
    Map<String, Object> outputs =
        processesExecutor.executeSync(
            apiData.getId(), process, executeRequest, configuration.getSyncTimeoutMillis());
    return Response.ok(toBytes(outputs), MediaType.APPLICATION_JSON_TYPE).build();
  }

  // --- requests

  private ImmutableStaticRequestContext.Builder context(
      OgcApi api, StepRequest request, ApiMediaType mediaType) {
    URICustomizer uri = new URICustomizer(api.getUri());
    uri.appendPathSegments(segments(request.path()).toArray(String[]::new));
    withoutFormat(request.query()).forEach(uri::setParameter);
    return new ImmutableStaticRequestContext.Builder()
        .webContext(appContext)
        .api(api)
        .requestUri(URI.create(uri.toString()))
        .method(request.method())
        .mediaType(mediaType)
        .alternateMediaTypes(Set.of())
        .language(Optional.empty());
  }

  // a step has the permissions of a request without credentials; a policy may change the request
  private ApiRequestContext authorize(
      ApiOperation operation, ApiRequestContext requestContext, Optional<byte[]> body) {
    return authorizer.checkAuthorization(requestContext, operation, Optional.empty(), body);
  }

  private static QueryParameterSet parameters(ApiOperation operation, StepRequest request) {
    Map<String, String> values = withoutFormat(request.query());
    List<OgcApiQueryParameter> definitions = operation.getQueryParameters();
    List<String> unknown =
        values.keySet().stream()
            .filter(name -> definitions.stream().noneMatch(p -> p.getName().equals(name)))
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The operation '%s' has no parameters %s.", operation.getOperationId(), unknown));
    }
    return QueryParameterSet.of(definitions, values);
  }

  // the responses of steps are JSON
  private static void checkFormat(StepRequest request) {
    String format = request.query().get("f");
    if (Objects.nonNull(format) && !JSON_FORMATS.contains(format)) {
      throw new IllegalArgumentException(
          String.format(
              "The format '%s' is not supported in a step, the responses of steps are JSON.",
              format));
    }
  }

  private static Map<String, String> withoutFormat(Map<String, String> query) {
    Map<String, String> values = new LinkedHashMap<>(query);
    values.remove("f");
    return values;
  }

  private static Map<String, String> bodyParameters(StepRequest request) {
    Object body = request.body().orElse(Map.of());
    if (!(body instanceof Map<?, ?> members)) {
      throw new IllegalArgumentException("The content must be an object with parameter values.");
    }
    Map<String, String> values = new LinkedHashMap<>();
    members.forEach((name, value) -> values.put(String.valueOf(name), parameterValue(value)));
    return values;
  }

  private static String parameterValue(Object value) {
    if (value instanceof List<?> list) {
      return list.stream()
          .map(InternalStepDispatcher::parameterValue)
          .collect(Collectors.joining(","));
    }
    if (value instanceof Map<?, ?>) {
      return new String(toBytes(value), StandardCharsets.UTF_8);
    }
    return RuntimeExpressions.stringify(value);
  }

  private static byte[] toBytes(Object value) {
    try {
      return MAPPER.writeValueAsBytes(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("The content is not JSON: " + e.getMessage(), e);
    }
  }

  // --- responses

  private static StepResponse toStepResponse(Response response, int maximumResponseSize) {
    try (response) {
      Map<String, String> headers = new LinkedHashMap<>();
      response
          .getStringHeaders()
          .forEach(
              (name, values) -> {
                if (!values.isEmpty()) {
                  headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                }
              });
      byte[] content = content(response.getEntity(), maximumResponseSize);
      Optional<Object> body = Optional.empty();
      if (content.length > 0) {
        try {
          body = Optional.of(MAPPER.readValue(content, Object.class));
        } catch (IOException e) {
          body = Optional.of(new String(content, StandardCharsets.UTF_8));
        }
      }
      return new StepResponse(response.getStatus(), headers, body);
    }
  }

  private static byte[] content(Object entity, int maximumResponseSize) {
    if (entity instanceof byte[] bytes) {
      if (bytes.length > maximumResponseSize) {
        throw tooLarge(maximumResponseSize);
      }
      return bytes;
    }
    if (entity instanceof StreamingOutput streamingOutput) {
      try (BoundedOutputStream output = new BoundedOutputStream(maximumResponseSize)) {
        streamingOutput.write(output);
        return output.toByteArray();
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
    if (entity instanceof String string) {
      return string.getBytes(StandardCharsets.UTF_8);
    }
    return Objects.isNull(entity) ? new byte[0] : toBytes(entity);
  }

  private static WebApplicationException tooLarge(int maximumResponseSize) {
    return new WebApplicationException(
        String.format(
            "The response of the step is larger than %d bytes, the maximum of a step.",
            maximumResponseSize),
        413);
  }

  private static StepResponse error(int status, String detail) {
    Map<String, Object> problem = new LinkedHashMap<>();
    problem.put("status", status);
    if (Objects.nonNull(detail)) {
      problem.put("detail", detail);
    }
    return new StepResponse(
        status, Map.of("content-type", "application/problem+json"), Optional.of(problem));
  }

  /** An output stream that fails when more than a maximum of bytes is written. */
  private static final class BoundedOutputStream extends OutputStream {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final int maximum;

    BoundedOutputStream(int maximum) {
      this.maximum = maximum;
    }

    @Override
    public void write(int b) {
      check(1);
      bytes.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      check(len);
      bytes.write(b, off, len);
    }

    private void check(int length) {
      if (bytes.size() + length > maximum) {
        throw tooLarge(maximum);
      }
    }

    byte[] toByteArray() {
      return bytes.toByteArray();
    }
  }
}
