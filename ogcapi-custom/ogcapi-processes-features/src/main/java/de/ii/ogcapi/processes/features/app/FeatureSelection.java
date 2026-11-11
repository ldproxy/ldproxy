/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeature;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.features.search.domain.ImmutableQueryExpression;
import de.ii.ogcapi.features.search.domain.ImmutableQueryInputQuery;
import de.ii.ogcapi.features.search.domain.ImmutableSingleQuery;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueryResolution;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.cql.domain.Bbox;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.SIntersects;
import de.ii.xtraplatform.cql.domain.SpatialLiteral;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

/**
 * The selection of the features of a process, the input {@code source}: the features of collections
 * with query parameters of {@code /collections/{collectionId}/items}, or the features of a stored
 * query. The features are read with the queries of the API in the internal {@link
 * FeatureRecordsFormat}, in the native coordinate reference system and limited to the properties
 * that the process needs.
 */
class FeatureSelection {

  static final String INPUT_SOURCE = "source";

  // the parameters that select a page, the properties, the coordinate reference system or the
  // format; the process decides these
  private static final Set<String> IGNORED_PARAMETERS =
      Set.of(
          "f",
          "limit",
          "offset",
          "properties",
          "exclude-properties",
          "crs",
          "sortby",
          "profile",
          "skipgeometry",
          "maxallowableoffset",
          "zoom-level");

  private static final ApiMediaType GEOJSON =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "geo+json"))
          .label("GeoJSON")
          .parameter("json")
          .build();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final FeaturesCoreProviders providers;
  private final FeaturesQuery featuresQuery;
  private final FeaturesCoreQueriesHandler featuresHandler;
  private final SearchQueriesHandler searchHandler;
  private final StoredQueryResolution storedQueryResolution;
  private final CrsTransformerFactory crsTransformerFactory;

  FeatureSelection(
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesQuery featuresQuery,
      FeaturesCoreQueriesHandler featuresHandler,
      SearchQueriesHandler searchHandler,
      StoredQueryResolution storedQueryResolution,
      CrsTransformerFactory crsTransformerFactory) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.providers = providers;
    this.featuresQuery = featuresQuery;
    this.featuresHandler = featuresHandler;
    this.searchHandler = searchHandler;
    this.storedQueryResolution = storedQueryResolution;
    this.crsTransformerFactory = crsTransformerFactory;
  }

  /** A checked selection, ready to run. */
  record Selection(
      OgcApi api,
      List<String> collections,
      Map<String, QueryParameterSet> itemsParameters,
      Optional<StoredQueryResolution.Resolved> storedQuery) {

    boolean isMultiCollection() {
      return collections.size() > 1;
    }
  }

  /**
   * Parses and checks the input {@code source}.
   *
   * @throws IllegalArgumentException if the input is invalid
   */
  Selection select(OgcApi api, Object input) {
    if (!(input instanceof Map<?, ?> source)) {
      throw new IllegalArgumentException("The input 'source' must be an object.");
    }
    if (source.get("href") instanceof String href) {
      return fromHref(api, href);
    }

    Map<String, String> parameters = parameters(source.get("parameters"));
    Object collection = source.get("collection");
    Object query = source.get("query");
    if (Objects.nonNull(collection) == Objects.nonNull(query)) {
      throw new IllegalArgumentException(
          "The input 'source' must have exactly one of the members 'collection', 'query' and 'href'.");
    }
    if (query instanceof String queryId) {
      return storedQuery(api, queryId, parameters);
    }
    return items(api, collections(collection), parameters);
  }

  /**
   * The names of the properties that can be grouped or aggregated in the collections of the
   * selection; geometries are left out, their values are the virtual properties.
   */
  Set<String> properties(Selection selection) {
    Set<String> properties = new LinkedHashSet<>();
    for (String collectionId : selection.collections()) {
      queryables(selection.api(), collectionId)
          .ifPresent(
              schema ->
                  schema.getAllNestedProperties().stream()
                      .filter(property -> !property.isSpatial())
                      .forEach(property -> properties.add(property.getFullPathAsString())));
    }
    return properties;
  }

  /**
   * The native coordinate reference system of the features, which must be projected for distances
   * and areas.
   *
   * @throws IllegalArgumentException if it is not projected
   */
  CrsTransformer toNative(OgcApi api) {
    EpsgCrs nativeCrs = nativeCrs(api);
    Optional<CrsTransformer> transformer =
        OgcCrs.CRS84.equals(nativeCrs)
            ? Optional.empty()
            : crsTransformerFactory.getTransformer(OgcCrs.CRS84, nativeCrs, true);
    if (transformer.isEmpty() || transformer.get().getTargetUnitEquivalentInMeters() != 1.0) {
      throw new IllegalArgumentException(
          String.format(
              "Distances and areas need a projected coordinate reference system with meters, the features are in %s.",
              nativeCrs.toUriString()));
    }
    return transformer.get();
  }

  /**
   * Reads the selected features and passes them to the consumer.
   *
   * @param properties the property paths that the process needs
   * @param geometry whether the process needs the primary geometry
   * @param area the area that the features must intersect, in the native coordinate reference
   *     system; used as a pre-filter of the features of collections, the process checks the
   *     intersection itself
   * @param maximum the maximum number of features
   * @return the number of features read, at most {@code maximum + 1}
   * @throws IllegalArgumentException if a query of the selection fails with a client error
   */
  long read(
      Selection selection,
      List<String> properties,
      boolean geometry,
      Optional<Geometry> area,
      int maximum,
      Consumer<FeatureRecord> consumer)
      throws IOException {
    long[] count = {0};
    Consumer<FeatureRecord> limited =
        record -> {
          count[0]++;
          if (count[0] <= maximum) {
            consumer.accept(record);
          }
        };

    if (selection.storedQuery().isPresent()) {
      readStoredQuery(selection, properties, geometry, maximum, limited);
    } else {
      // a selection with too many features fails before the features are read
      long matched = 0;
      for (String collectionId : selection.collections()) {
        long collectionCount = countItems(selection, collectionId, area);
        if (collectionCount < 0) {
          matched = -1;
          break;
        }
        matched += collectionCount;
      }
      if (matched > maximum) {
        return matched;
      }
      for (String collectionId : selection.collections()) {
        if (count[0] > maximum) {
          break;
        }
        readItems(selection, collectionId, properties, geometry, area, maximum, limited);
      }
    }

    return count[0];
  }

  /**
   * A feature as GeoJSON, as returned by {@code /collections/{collectionId}/items/{featureId}}.
   *
   * @throws IllegalArgumentException if the query of the feature fails with a client error
   */
  @SuppressWarnings("unchecked")
  Map<String, Object> feature(OgcApi api, String collectionId, String featureId)
      throws IOException {
    OgcApiDataV2 apiData = api.getData();
    FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();
    FeaturesCoreConfiguration coreConfiguration =
        collectionData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();
    QueryParameterSet parameterSet =
        QueryParameterSet.of(
                queryParameters(
                    api, String.format("/collections/%s/items/%s", collectionId, featureId)),
                Map.of())
            .evaluate(api, Optional.of(collectionData));

    FeatureQuery query =
        featuresQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            parameterSet,
            featureId,
            Optional.empty(),
            SchemaBase.Scope.RETURNABLE,
            false);

    URICustomizer requestUri = new URICustomizer(api.getUri());
    requestUri.appendPathSegments("collections", collectionId, "items", featureId);

    FeaturesCoreQueriesHandler.QueryInputFeature queryInput =
        new ImmutableQueryInputFeature.Builder()
            .collectionId(collectionId)
            .featureId(featureId)
            .query(query)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData, collectionData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .shouldAuditLog(false)
            .build();

    ApiRequestContext requestContext =
        new ImmutableStaticRequestContext.Builder()
            .webContext(appContext)
            .api(api)
            .requestUri(URI.create(requestUri.toString()))
            .mediaType(GEOJSON)
            .alternateMediaTypes(Set.of())
            .language(Optional.empty())
            .queryParameterSet(parameterSet)
            .build();

    try (Response response =
        featuresHandler.handle(
            FeaturesCoreQueriesHandler.Query.FEATURE, queryInput, requestContext)) {
      if (response.getStatus() >= 400) {
        throw new WebApplicationException(
            String.format("The query of the feature failed with status %d.", response.getStatus()),
            response.getStatus());
      }
      Object entity = response.getEntity();
      if (entity instanceof StreamingOutput streamingOutput) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        streamingOutput.write(bytes);
        entity = bytes.toByteArray();
      }
      return MAPPER.readValue((byte[]) entity, Map.class);
    }
  }

  private Selection items(OgcApi api, List<String> collections, Map<String, String> parameters) {
    OgcApiDataV2 apiData = api.getData();
    Map<String, QueryParameterSet> parameterSets = new LinkedHashMap<>();
    for (String collectionId : collections) {
      FeatureTypeConfigurationOgcApi collectionData =
          apiData
              .getCollectionData(collectionId)
              .filter(
                  collection ->
                      collection.getExtension(FeaturesCoreConfiguration.class).isPresent())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          String.format(
                              "The collection '%s' does not exist in API '%s'.",
                              collectionId, api.getId())));
      String path = String.format("/collections/%s/items", collectionId);
      List<OgcApiQueryParameter> definitions = queryParameters(api, path);
      List<String> errors = validate(apiData, Optional.of(collectionId), definitions, parameters);
      if (!errors.isEmpty()) {
        throw new IllegalArgumentException(String.join(" ", errors));
      }
      parameterSets.put(
          collectionId,
          QueryParameterSet.of(definitions, parameters).evaluate(api, Optional.of(collectionData)));
    }
    return new Selection(api, List.copyOf(collections), parameterSets, Optional.empty());
  }

  private Selection storedQuery(OgcApi api, String queryId, Map<String, String> parameters) {
    StoredQueryResolution.Resolved resolved =
        storedQueryResolution.resolve(api, queryId, parameters);
    List<String> collections =
        resolved.getQuery().getQueries().stream()
            .filter(query -> !query.getResultSetOnly())
            .flatMap(query -> query.getCollections().stream())
            .distinct()
            .toList();
    if (collections.isEmpty()) {
      collections = resolved.getQuery().getCollections();
    }
    return new Selection(api, collections, Map.of(), Optional.of(resolved));
  }

  // an /items or /search URL of the same API
  private Selection fromHref(OgcApi api, String href) {
    URI uri;
    try {
      uri = URI.create(href);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(String.format("The URL '%s' is invalid.", href), e);
    }
    URI apiUri = api.getUri();
    String apiPath = apiUri.getPath().endsWith("/") ? apiUri.getPath() : apiUri.getPath() + "/";
    boolean sameApi =
        Objects.equals(uri.getScheme(), apiUri.getScheme())
            && Objects.equals(uri.getHost(), apiUri.getHost())
            && uri.getPort() == apiUri.getPort()
            && Objects.nonNull(uri.getPath())
            && uri.getPath().startsWith(apiPath);
    if (!sameApi) {
      throw new IllegalArgumentException(
          String.format("The URL '%s' is not a URL of API '%s'.", href, api.getId()));
    }

    String[] segments = uri.getPath().substring(apiPath.length()).split("/");
    Map<String, String> parameters = queryString(uri.getRawQuery());
    if (segments.length == 3 && "collections".equals(segments[0]) && "items".equals(segments[2])) {
      return items(api, List.of(segments[1]), parameters);
    }
    if (segments.length == 2 && "search".equals(segments[0])) {
      return storedQuery(api, segments[1], parameters);
    }
    throw new IllegalArgumentException(
        String.format(
            "The URL '%s' is neither a request of the features of a collection nor of a stored query.",
            href));
  }

  private void readItems(
      Selection selection,
      String collectionId,
      List<String> properties,
      boolean geometry,
      Optional<Geometry> area,
      int maximum,
      Consumer<FeatureRecord> consumer)
      throws IOException {
    FeatureQuery query =
        itemsQuery(selection, collectionId, area)
            .fields(
                fields(
                    selection.api(),
                    collectionId,
                    properties,
                    geometry ? geometryPath(selection.api(), collectionId) : Optional.empty()))
            .limit(maximum + 1)
            .build();
    try (Response response = handleItems(selection, collectionId, query)) {
      readResponse(response, consumer, count -> {});
    }
  }

  // the number of matching features, -1 if unknown
  private long countItems(Selection selection, String collectionId, Optional<Geometry> area)
      throws IOException {
    FeatureQuery query =
        itemsQuery(selection, collectionId, area)
            .fields(fields(selection.api(), collectionId, List.of(), Optional.empty()))
            .hitsOnly(true)
            .build();
    long[] matched = {-1};
    try (Response response = handleItems(selection, collectionId, query)) {
      readResponse(response, record -> {}, count -> matched[0] = count);
    }
    return matched[0];
  }

  private Optional<String> geometryPath(OgcApi api, String collectionId) {
    return queryables(api, collectionId)
        .flatMap(FeatureSchema::getFilterGeometry)
        .map(FeatureSchema::getFullPathAsString);
  }

  // the query of the request, in the native coordinate reference system and with the area as a
  // pre-filter
  private ImmutableFeatureQuery.Builder itemsQuery(
      Selection selection, String collectionId, Optional<Geometry> area) {
    OgcApi api = selection.api();
    OgcApiDataV2 apiData = api.getData();
    FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();
    FeaturesCoreConfiguration coreConfiguration =
        collectionData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();
    QueryParameterSet parameterSet = selection.itemsParameters().get(collectionId);
    Optional<String> geometryPath = geometryPath(api, collectionId);
    EpsgCrs nativeCrs = nativeCrs(api);

    FeatureQuery query =
        featuresQuery.requestToFeatureQuery(
            apiData,
            collectionData,
            coreConfiguration.getDefaultEpsgCrs(),
            coreConfiguration.getCoordinatePrecision(),
            coreConfiguration.getDefaultPageSize(),
            parameterSet,
            true);

    ImmutableFeatureQuery.Builder builder =
        ImmutableFeatureQuery.builder().from(query).crs(nativeCrs).offset(0);
    if (area.isPresent() && geometryPath.isPresent()) {
      Envelope envelope = area.get().getEnvelopeInternal();
      builder.addFilters(
          SIntersects.of(
              Property.of(geometryPath.get()),
              SpatialLiteral.of(
                  Bbox.of(
                      envelope.getMinX(),
                      envelope.getMinY(),
                      envelope.getMaxX(),
                      envelope.getMaxY(),
                      nativeCrs))));
    }
    return builder;
  }

  private Response handleItems(Selection selection, String collectionId, FeatureQuery query) {
    OgcApi api = selection.api();
    OgcApiDataV2 apiData = api.getData();
    FeatureTypeConfigurationOgcApi collectionData =
        apiData.getCollectionData(collectionId).orElseThrow();
    FeaturesCoreConfiguration coreConfiguration =
        collectionData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();

    URICustomizer requestUri = new URICustomizer(api.getUri());
    requestUri.appendPathSegments("collections", collectionId, "items");

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(collectionId)
            .query(query)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData, collectionData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .defaultPageSize(Optional.of(coreConfiguration.getDefaultPageSize()))
            .sendResponseAsStream(true)
            .includeBodyLinks(false)
            .shouldAuditLog(false)
            .build();

    return featuresHandler.handle(
        FeaturesCoreQueriesHandler.Query.FEATURES,
        queryInput,
        requestContext(api, requestUri, selection.itemsParameters().get(collectionId)));
  }

  private void readStoredQuery(
      Selection selection,
      List<String> properties,
      boolean geometry,
      int maximum,
      Consumer<FeatureRecord> consumer)
      throws IOException {
    OgcApi api = selection.api();
    OgcApiDataV2 apiData = api.getData();
    FeaturesCoreConfiguration coreConfiguration =
        apiData.getExtension(FeaturesCoreConfiguration.class).orElseThrow();
    StoredQueryResolution.Resolved resolved = selection.storedQuery().orElseThrow();
    QueryExpression resolvedQuery = resolved.getQuery();

    Set<String> fields = new LinkedHashSet<>();
    for (String collectionId : selection.collections()) {
      Optional<String> geometryPath =
          queryables(api, collectionId)
              .flatMap(FeatureSchema::getFilterGeometry)
              .map(FeatureSchema::getFullPathAsString);
      fields.addAll(
          fields(api, collectionId, properties, geometry ? geometryPath : Optional.empty()));
    }

    // the properties of the process replace those of the stored query and of its queries
    QueryExpression query =
        new ImmutableQueryExpression.Builder()
            .from(resolvedQuery)
            .properties(List.copyOf(fields))
            .excludeProperties(List.of())
            .crs(nativeCrs(api).toUriString())
            .limit(maximum + 1)
            .offset(0)
            .queries(
                resolvedQuery.getQueries().stream()
                    .map(
                        single ->
                            new ImmutableSingleQuery.Builder()
                                .from(single)
                                .properties(List.of())
                                .excludeProperties(List.of())
                                .build())
                    .toList())
            .build();

    URICustomizer requestUri = new URICustomizer(api.getUri());
    requestUri.appendPathSegments("search", resolvedQuery.getId());

    SearchQueriesHandler.QueryInputQuery queryInput =
        new ImmutableQueryInputQuery.Builder()
            .query(query)
            .featureProvider(providers.getFeatureProviderOrThrow(apiData))
            .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
            .minimumPageSize(Optional.ofNullable(coreConfiguration.getMinimumPageSize()))
            .defaultPageSize(Optional.ofNullable(coreConfiguration.getDefaultPageSize()))
            .maximumPageSize(Optional.of(maximum + 1))
            .allLinksAreLocal(
                apiData
                    .getExtension(SearchConfiguration.class)
                    .map(SearchConfiguration::getAllLinksAreLocal)
                    .orElse(false))
            .isStoredQuery(true)
            .includeBodyLinks(false)
            .build();

    try (Response response =
        searchHandler.handle(
            SearchQueriesHandler.Query.QUERY,
            queryInput,
            requestContext(api, requestUri, resolved.getQueryParameterSet()))) {
      readResponse(response, consumer, count -> {});
    }
  }

  private ApiRequestContext requestContext(
      OgcApi api, URICustomizer requestUri, QueryParameterSet parameterSet) {
    return new ImmutableStaticRequestContext.Builder()
        .webContext(appContext)
        .api(api)
        .requestUri(URI.create(requestUri.toString()))
        .mediaType(FeatureRecordsFormat.MEDIA_TYPE)
        .alternateMediaTypes(Set.of())
        .language(Optional.empty())
        .queryParameterSet(parameterSet)
        .build();
  }

  private static void readResponse(
      Response response, Consumer<FeatureRecord> consumer, LongConsumer numberMatched)
      throws IOException {
    if (response.getStatus() >= 400) {
      throw new WebApplicationException(
          String.format("The query of the features failed with status %d.", response.getStatus()),
          response.getStatus());
    }
    try (FeatureRecordReader reader = new FeatureRecordReader(consumer, numberMatched)) {
      Object entity = response.getEntity();
      if (entity instanceof StreamingOutput streamingOutput) {
        streamingOutput.write(reader);
      } else if (entity instanceof byte[] bytes) {
        reader.write(bytes);
      }
    }
  }

  // the id is always read, the provider needs at least one property
  private List<String> fields(
      OgcApi api, String collectionId, List<String> properties, Optional<String> geometryPath) {
    List<String> fields = new ArrayList<>();
    providers
        .getFeatureSchema(
            api.getData(), api.getData().getCollectionData(collectionId).orElseThrow())
        .flatMap(FeatureSchema::getIdProperty)
        .map(FeatureSchema::getFullPathAsString)
        .ifPresent(fields::add);
    properties.stream().filter(p -> !Aggregation.isVirtual(p)).forEach(fields::add);
    geometryPath.ifPresent(fields::add);
    return fields.stream().distinct().toList();
  }

  private Optional<FeatureSchema> queryables(OgcApi api, String collectionId) {
    return api.getData()
        .getCollectionData(collectionId)
        .flatMap(collectionData -> providers.getQueryablesSchema(api.getData(), collectionData));
  }

  private EpsgCrs nativeCrs(OgcApi api) {
    FeatureProvider provider = providers.getFeatureProviderOrThrow(api.getData());
    return provider.crs().isAvailable() ? provider.crs().get().getNativeCrs() : OgcCrs.CRS84;
  }

  private List<OgcApiQueryParameter> queryParameters(OgcApi api, String path) {
    return extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
        .filter(endpoint -> endpoint.isEnabledForApi(api.getData()))
        .map(endpoint -> endpoint.getDefinition(api.getData()))
        .filter(Objects::nonNull)
        .map(definition -> definition.getOperation(path, "GET"))
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("The features at '%s' cannot be queried.", path)))
        .getQueryParameters();
  }

  // the checks of the request dispatcher for the parameters of a GET request
  private static List<String> validate(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      List<OgcApiQueryParameter> definitions,
      Map<String, String> values) {
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
                  .filter(name -> !IGNORED_PARAMETERS.contains(name.toLowerCase(Locale.ROOT)))
                  .collect(Collectors.joining(", "))));
    }

    values.forEach(
        (name, value) ->
            publicParameters.stream()
                .filter(parameter -> parameter.getName().equalsIgnoreCase(name))
                .forEach(
                    parameter ->
                        parameter
                            .validate(apiData, collectionId, List.of(value))
                            .ifPresent(errors::add)));

    return errors;
  }

  private static List<String> collections(Object collection) {
    if (collection instanceof String collectionId) {
      return List.of(collectionId);
    }
    if (collection instanceof List<?> list
        && !list.isEmpty()
        && list.stream().allMatch(String.class::isInstance)) {
      return list.stream().map(String.class::cast).distinct().toList();
    }
    throw new IllegalArgumentException(
        "The member 'collection' of the input 'source' must be a collection id or a list of ids.");
  }

  // the values as in a query string, lists are comma-separated; the ignored parameters are dropped
  static Map<String, String> parameters(Object input) {
    Map<String, String> parameters = new LinkedHashMap<>();
    if (Objects.isNull(input)) {
      return parameters;
    }
    if (!(input instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(
          "The member 'parameters' of the input 'source' must be an object.");
    }
    map.forEach(
        (name, value) -> {
          if (Objects.isNull(value) || isIgnored(String.valueOf(name))) {
            return;
          }
          if (value instanceof List<?> list) {
            parameters.put(
                String.valueOf(name),
                list.stream().map(String::valueOf).collect(Collectors.joining(",")));
          } else if (value instanceof Map<?, ?>) {
            throw new IllegalArgumentException(
                String.format("The value of the parameter '%s' must not be an object.", name));
          } else {
            parameters.put(String.valueOf(name), String.valueOf(value));
          }
        });
    return parameters;
  }

  private static Map<String, String> queryString(String rawQuery) {
    Map<String, String> parameters = new LinkedHashMap<>();
    if (Objects.isNull(rawQuery) || rawQuery.isEmpty()) {
      return parameters;
    }
    for (String pair : rawQuery.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int separator = pair.indexOf('=');
      String name =
          URLDecoder.decode(
              separator < 0 ? pair : pair.substring(0, separator), StandardCharsets.UTF_8);
      String value =
          separator < 0
              ? ""
              : URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
      if (!isIgnored(name)) {
        parameters.put(name, value);
      }
    }
    return parameters;
  }

  private static boolean isIgnored(String name) {
    return IGNORED_PARAMETERS.contains(name.toLowerCase(Locale.ROOT));
  }
}
