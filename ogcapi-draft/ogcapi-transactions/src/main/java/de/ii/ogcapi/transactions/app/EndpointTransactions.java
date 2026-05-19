/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.GROUP_DATA_WRITE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import dagger.Lazy;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.transactions.domain.ActionResult;
import de.ii.ogcapi.transactions.domain.ActionStatus;
import de.ii.ogcapi.transactions.domain.ExecutionResult;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionExecutor;
import de.ii.ogcapi.transactions.domain.TransactionParser;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @title Transactions
 * @path transactions
 * @langEn Execute one or more insert, replace, update or delete actions over the feature
 *     collections of the API in a single request.
 * @langDe Führt eine oder mehrere Insert-, Replace-, Update- oder Delete-Aktionen über die
 *     Objektarten der API in einer einzigen Anfrage aus.
 */
@Singleton
@AutoBind
@Path("/transactions")
public class EndpointTransactions extends Endpoint implements ConformanceClass {

  private static final List<String> TAGS = ImmutableList.of("Mutate data");
  private static final MediaType APPLICATION_JSON = MediaType.APPLICATION_JSON_TYPE;
  private static final MediaType PROBLEM_JSON = new MediaType("application", "problem+json");

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final Lazy<Set<TransactionParser>> parsers;
  private final TransactionExecutor executor;
  private final FeaturesCoreProviders providers;

  @Inject
  public EndpointTransactions(
      ExtensionRegistry extensionRegistry,
      Lazy<Set<TransactionParser>> parsers,
      TransactionExecutor executor,
      FeaturesCoreProviders providers) {
    super(extensionRegistry);
    this.parsers = parsers;
    this.executor = executor;
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TransactionsConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = ImmutableList.of();
    }
    return formats;
  }

  /**
   * Transaction bodies don't fit the {@link FormatExtension} model (they are parsed by {@link
   * TransactionParser} implementations, not feature format extensions), so the default {@link
   * Endpoint#getRequestContent(OgcApiDataV2)} returns an empty map and the framework refuses to
   * register the POST handler. We override here to declare the supported request media types
   * directly, which makes the resource available at runtime and surfaces them in the generated
   * OpenAPI document.
   */
  @Override
  public Map<MediaType, ApiMediaTypeContent> getRequestContent(OgcApiDataV2 apiData) {
    Map<MediaType, ApiMediaTypeContent> content = new LinkedHashMap<>();
    MediaType ogcTxJson = new MediaType("application", "ogc-tx+json");
    content.put(ogcTxJson, transactionContent(ogcTxJson, "ogc-tx-json", "JSON", "ogc-tx-json"));

    boolean wfsEnabled =
        apiData
            .getExtension(TransactionsConfiguration.class)
            .map(TransactionsConfiguration::getWfsTransaction)
            .orElse(Boolean.FALSE)
            .booleanValue();
    if (wfsEnabled) {
      MediaType xml = MediaType.APPLICATION_XML_TYPE;
      content.put(xml, transactionContent(xml, "xml", "XML", "wfs-transaction-xml"));
    }
    return content;
  }

  private static ApiMediaTypeContent transactionContent(
      MediaType type, String parameter, String label, String schemaName) {
    ApiMediaType ogcApiMediaType =
        new ImmutableApiMediaType.Builder().type(type).label(label).parameter(parameter).build();
    return new ImmutableApiMediaTypeContent.Builder()
        .ogcApiMediaType(ogcApiMediaType)
        .schema(
            type.getSubtype().endsWith("xml")
                ? new StringSchema().example("<wfs:Transaction>...</wfs:Transaction>")
                : new ObjectSchema())
        .schemaRef("#/components/schemas/" + schemaName)
        .build();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<String>();
    builder.add("http://www.opengis.net/spec/ogcapi-features-11/1.0/conf/transactions");
    builder.add("http://www.opengis.net/spec/ogcapi-features-11/1.0/conf/json-transactions");
    builder.add("http://www.opengis.net/spec/ogcapi-features-11/1.0/conf/features");

    Optional<TransactionsConfiguration> cfg = apiData.getExtension(TransactionsConfiguration.class);
    if (cfg.map(c -> !Objects.equals(c.getAtomic(), false)).orElse(true)) {
      builder.add("http://www.opengis.net/spec/ogcapi-features-11/1.0/conf/atomic-transactions");
    }
    if (cfg.map(c -> !Objects.equals(c.getBatch(), false)).orElse(true)) {
      builder.add("http://www.opengis.net/spec/ogcapi-features-11/1.0/conf/batch-transactions");
    }
    return builder.build();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("transactions")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_TRANSACTIONS);

    String path = "/transactions";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path, HttpMethods.POST);
    String operationSummary = "execute a transaction";
    Optional<String> operationDescription =
        Optional.of(
            "Executes one or more insert, replace, update or delete actions over the feature "
                + "collections of the API. The request body is a transaction document; supported "
                + "media types: `application/ogc-tx+json` (an OGC JSON Transaction object)"
                + (apiData
                        .getExtension(TransactionsConfiguration.class)
                        .map(cfg -> cfg.getWfsTransaction())
                        .orElse(false)
                    ? " and `application/xml` (a `wfs:Transaction` element)."
                    : "."));
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
    Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);
    ApiOperation.of(
            path,
            HttpMethods.POST,
            requestContent,
            queryParameters,
            ImmutableList.of(),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("executeTransaction"),
            GROUP_DATA_WRITE,
            TAGS,
            TransactionsBuildingBlock.MATURITY,
            TransactionsBuildingBlock.SPEC,
            false)
        .ifPresent(operation -> resourceBuilder.putOperations(HttpMethods.POST.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  @POST
  @Consumes({
    "application/ogc-tx+json",
    "application/json",
    "application/xml",
    "application/gml+xml"
  })
  @Produces({"application/json", "application/problem+json"})
  public Response postTransaction(
      @Auth Optional<User> optionalUser,
      @HeaderParam("Content-Crs") String contentCrsHeader,
      @HeaderParam("Prefer") List<String> prefer,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      InputStream requestBody) {

    if (containsPreferToken(prefer, "respond-async")) {
      return Response.status(Response.Status.NOT_IMPLEMENTED)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("Asynchronous transactions are not supported by this API.")
          .build();
    }

    OgcApiDataV2 apiData = api.getData();
    TransactionsConfiguration config =
        apiData
            .getExtension(TransactionsConfiguration.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Transactions building block is not configured for this API"));

    MediaType contentType = parseContentType(request.getContentType());
    TransactionParser parser =
        parsers.get().stream()
            .filter(p -> p.canParse(contentType))
            .findFirst()
            .orElseThrow(
                () ->
                    new BadRequestException(
                        "Unsupported transaction request media type: " + contentType));

    if (parser instanceof WfsTransactionParser
        && !Boolean.TRUE.equals(config.getWfsTransaction())) {
      throw new BadRequestException(
          "wfs:Transaction payloads are not enabled for this API "
              + "(set 'wfsTransaction: true' on the TRANSACTIONS building block)");
    }

    EpsgCrs requestCrs = resolveRequestCrs(contentCrsHeader, apiData);

    Transaction transaction;
    try {
      transaction = parser.parse(requestBody, contentType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Could not parse transaction body: " + e.getMessage());
    }

    enforceSemanticPolicy(transaction, config);
    enforceActionLimit(transaction, config);

    ExecutionResult result = executor.execute(transaction, api, requestContext, requestCrs);

    PreferReturn ret = parseReturn(prefer, PreferReturn.REPRESENTATION);
    return buildResponse(result, ret, requestContext);
  }

  // --- helpers --------------------------------------------------------------

  private static MediaType parseContentType(String header) {
    if (header == null || header.isBlank()) {
      throw new BadRequestException("Missing Content-Type header");
    }
    try {
      return MediaType.valueOf(header);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid Content-Type header: " + header);
    }
  }

  private EpsgCrs resolveRequestCrs(String header, OgcApiDataV2 apiData) {
    if (header != null && !header.isBlank()) {
      String value = header.trim();
      if (value.startsWith("<") && value.endsWith(">")) {
        value = value.substring(1, value.length() - 1);
      }
      try {
        return EpsgCrs.fromString(value);
      } catch (RuntimeException e) {
        throw new BadRequestException("Invalid Content-Crs header: " + header);
      }
    }
    return providers
        .getFeatureProvider(apiData)
        .flatMap(
            fp ->
                apiData.getCollections().values().stream()
                    .findFirst()
                    .flatMap(cd -> cd.getExtension(FeaturesCoreConfiguration.class))
                    .map(FeaturesCoreConfiguration::getDefaultEpsgCrs))
        .orElse(de.ii.xtraplatform.crs.domain.OgcCrs.CRS84);
  }

  private static void enforceSemanticPolicy(Transaction tx, TransactionsConfiguration config) {
    if (tx.getSemantic() == TxSemantic.ATOMIC && Objects.equals(config.getAtomic(), false)) {
      throw new BadRequestException("Atomic transactions are not enabled for this API");
    }
    if (tx.getSemantic() == TxSemantic.BATCH && Objects.equals(config.getBatch(), false)) {
      throw new BadRequestException("Batch transactions are not enabled for this API");
    }
  }

  private static void enforceActionLimit(Transaction tx, TransactionsConfiguration config) {
    Integer max = config.getMaxActionsPerRequest();
    if (max == null || max <= 0) {
      return;
    }
    // counting actions consumes the iterator; since the parser is lazy we instead defer to
    // executor-side enforcement only when the limit is wired into the action iterator wrapper.
    // For now this is a no-op so we don't break streaming; a follow-up will add a counting
    // wrapper around Transaction#actions that aborts past the limit.
  }

  private Response buildResponse(
      ExecutionResult result, PreferReturn ret, ApiRequestContext requestContext) {
    boolean atomic = result.getSemantic() == TxSemantic.ATOMIC;

    if (atomic && !result.isSuccess()) {
      ActionResult failed =
          result.getActionResults().stream()
              .filter(r -> r.getStatus() == ActionStatus.FAILED)
              .findFirst()
              .orElse(null);
      ObjectNode problem = MAPPER.createObjectNode();
      problem.put("type", "about:blank");
      problem.put("title", "Transaction failed");
      problem.put("status", 422);
      if (failed != null) {
        problem.put(
            "detail",
            "Action "
                + failed.getActionId().orElse(failed.getType() + "@" + failed.getCollectionId())
                + " failed: "
                + failed.getError().orElse("unknown error"));
        failed.getActionId().ifPresent(id -> problem.put("actionId", id));
        problem.put("collectionId", failed.getCollectionId());
        problem.put("action", failed.getType().toString().toLowerCase(java.util.Locale.ROOT));
      } else {
        problem.put("detail", "Transaction could not be committed");
      }
      return Response.status(422).type(PROBLEM_JSON).entity(toJson(problem)).build();
    }

    if (ret == PreferReturn.NONE) {
      return Response.noContent().header("Preference-Applied", "return=none").build();
    }

    ObjectNode body = renderBody(result, ret, requestContext);
    Response.ResponseBuilder rb =
        Response.ok(toJson(body), APPLICATION_JSON)
            .header("Preference-Applied", "return=" + ret.headerValue());
    return rb.build();
  }

  private static ObjectNode renderBody(
      ExecutionResult result, PreferReturn ret, ApiRequestContext requestContext) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("semantic", result.getSemantic().toString().toLowerCase(java.util.Locale.ROOT));

    ObjectNode summary = body.putObject("summary");
    summary.put("totalInserted", result.getInsertedCount());
    summary.put("totalReplaced", result.getReplacedCount());
    summary.put("totalUpdated", result.getUpdatedCount());
    summary.put("totalDeleted", result.getDeletedCount());

    if (ret == PreferReturn.MINIMAL) {
      return body;
    }

    ArrayNode insertResults = body.putArray("insertResults");
    ArrayNode replaceResults = body.putArray("replaceResults");
    ArrayNode updateResults = body.putArray("updateResults");
    ArrayNode deleteResults = body.putArray("deleteResults");
    ArrayNode exceptions = MAPPER.createArrayNode();

    for (ActionResult r : result.getActionResults()) {
      if (r.getStatus() == ActionStatus.FAILED) {
        ObjectNode ex = exceptions.addObject();
        r.getActionId().ifPresent(id -> ex.put("actionId", id));
        ex.put("collectionId", r.getCollectionId());
        ex.put("action", r.getType().toString().toLowerCase(java.util.Locale.ROOT));
        ex.put("message", r.getError().orElse("unknown error"));
        continue;
      }
      if (r.getStatus() != ActionStatus.SUCCESS) {
        continue;
      }
      ArrayNode bucket =
          bucketFor(r.getType(), insertResults, replaceResults, updateResults, deleteResults);
      for (String id : r.getFeatureIds()) {
        bucket.add(featureUri(requestContext, r.getCollectionId(), id));
      }
    }

    if (!exceptions.isEmpty()) {
      body.set("exceptions", exceptions);
    }
    return body;
  }

  private static ArrayNode bucketFor(
      TxActionType type,
      ArrayNode insertResults,
      ArrayNode replaceResults,
      ArrayNode updateResults,
      ArrayNode deleteResults) {
    switch (type) {
      case INSERT:
        return insertResults;
      case REPLACE:
        return replaceResults;
      case UPDATE:
        return updateResults;
      case DELETE:
        return deleteResults;
      default:
        throw new IllegalStateException("Unknown action type: " + type);
    }
  }

  private static String featureUri(
      ApiRequestContext requestContext, String collectionId, String featureId) {
    try {
      return requestContext
          .getApiUriCustomizer()
          .copy()
          .ensureLastPathSegments("collections", collectionId, "items", featureId)
          .build()
          .toString();
    } catch (Exception e) {
      return "/collections/" + collectionId + "/items/" + featureId;
    }
  }

  private static String toJson(Object node) {
    try {
      return MAPPER.writeValueAsString(node);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialise transaction response", e);
    }
  }

  // --- Prefer header parsing ------------------------------------------------

  private enum PreferReturn {
    NONE("none"),
    MINIMAL("minimal"),
    REPRESENTATION("representation");

    private final String header;

    PreferReturn(String header) {
      this.header = header;
    }

    String headerValue() {
      return header;
    }

    static Optional<PreferReturn> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(java.util.Locale.ROOT);
      for (PreferReturn r : values()) {
        if (r.header.equals(v)) return Optional.of(r);
      }
      return Optional.empty();
    }
  }

  private static PreferReturn parseReturn(List<String> preferHeaders, PreferReturn fallback) {
    if (preferHeaders == null) return fallback;
    for (String header : preferHeaders) {
      for (String token : header.split(",")) {
        String t = token.trim();
        if (t.regionMatches(true, 0, "return", 0, "return".length())) {
          int eq = t.indexOf('=');
          if (eq > 0) {
            Optional<PreferReturn> r = PreferReturn.fromHeader(t.substring(eq + 1).trim());
            if (r.isPresent()) return r.get();
          }
        }
      }
    }
    return fallback;
  }

  private static boolean containsPreferToken(List<String> preferHeaders, String token) {
    if (preferHeaders == null) return false;
    for (String header : preferHeaders) {
      for (String t : header.split(",")) {
        if (token.equalsIgnoreCase(t.trim())) return true;
      }
    }
    return false;
  }
}
