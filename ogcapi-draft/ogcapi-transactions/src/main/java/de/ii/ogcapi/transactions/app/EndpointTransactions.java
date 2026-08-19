/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.GROUP_DATA_WRITE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CountingInputStream;
import dagger.Lazy;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.crs.domain.HeaderContentCrs;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.HeaderPrefer.Handling;
import de.ii.ogcapi.foundation.domain.HeaderPrefer.Return;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.transactions.domain.TransactionJob;
import de.ii.ogcapi.transactions.domain.TransactionParser;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import de.ii.xtralink.jobs.Identifiers;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import io.dropwizard.auth.Auth;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class EndpointTransactions extends Endpoint
    implements TransactionInputs, ConformanceClass, ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointTransactions.class);

  private static final List<String> TAGS = ImmutableList.of("Mutate data");

  private static final String ENVELOPE_SCHEMA_RESOURCE =
      "/de/ii/ogcapi/transactions/transaction-envelope.json";
  private static volatile JsonEnvelopeSchema cachedJsonEnvelope;

  static final class JsonEnvelopeSchema {
    final Schema<?> top;
    final Map<String, Schema<?>> defs;

    JsonEnvelopeSchema(Schema<?> top, Map<String, Schema<?>> defs) {
      this.top = top;
      this.defs = defs;
    }
  }

  private final Lazy<Set<TransactionParser>> parsers;
  private final CommandHandlerTransactions commandHandler;
  private final CrsSupport crsSupport;
  private final FeaturesCoreProviders providers;
  private final Jobs jobs;
  private final ResourceStore documentStore;

  @Inject
  public EndpointTransactions(
      ExtensionRegistry extensionRegistry,
      Lazy<Set<TransactionParser>> parsers,
      CommandHandlerTransactions commandHandler,
      CrsSupport crsSupport,
      FeaturesCoreProviders providers,
      Jobs jobs,
      ResourceStore resourceStore) {
    super(extensionRegistry);
    this.parsers = parsers;
    this.commandHandler = commandHandler;
    this.crsSupport = crsSupport;
    this.providers = providers;
    this.jobs = jobs;
    this.documentStore = resourceStore.with(Jobs.RESOURCE_TYPE, TransactionJob.KIND);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TransactionsConfiguration.class;
  }

  // TODO: add format for TransactionResponse
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
            .orElse(Boolean.FALSE);
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
    ImmutableApiMediaTypeContent.Builder builder =
        new ImmutableApiMediaTypeContent.Builder()
            .ogcApiMediaType(ogcApiMediaType)
            .schemaRef("#/components/schemas/" + schemaName);
    if (type.getSubtype().endsWith("xml")) {
      builder.schema(new StringSchema().example("<wfs:Transaction>...</wfs:Transaction>"));
    } else {
      JsonEnvelopeSchema envelope = jsonEnvelopeSchema();
      builder.schema(envelope.top).referencedSchemas(envelope.defs);
    }
    return builder.build();
  }

  static JsonEnvelopeSchema jsonEnvelopeSchema() {
    JsonEnvelopeSchema cached = cachedJsonEnvelope;
    if (cached == null) {
      cached = loadJsonEnvelopeSchema();
      cachedJsonEnvelope = cached;
    }
    return cached;
  }

  private static JsonEnvelopeSchema loadJsonEnvelopeSchema() {
    try (InputStream in =
        EndpointTransactions.class.getResourceAsStream(ENVELOPE_SCHEMA_RESOURCE)) {
      if (in == null) {
        LOGGER.warn(
            "Bundled envelope schema resource not found ({}); falling back to an empty object schema in the OpenAPI document",
            ENVELOPE_SCHEMA_RESOURCE);
        return new JsonEnvelopeSchema(new ObjectSchema(), Map.of());
      }
      ObjectMapper jsonMapper = Json.mapper();
      JsonNode root = jsonMapper.readTree(in);
      if (!(root instanceof ObjectNode rootObj)) {
        LOGGER.warn(
            "Envelope schema {} is not a JSON object; falling back to an empty object schema",
            ENVELOPE_SCHEMA_RESOURCE);
        return new JsonEnvelopeSchema(new ObjectSchema(), Map.of());
      }
      rootObj.remove("$schema");
      JsonNode defsNode = rootObj.remove("$defs");
      rewriteDefRefs(rootObj);
      normaliseForOpenApi30(rootObj);
      Map<String, Schema<?>> defs = new LinkedHashMap<>();
      if (defsNode instanceof ObjectNode defsObj) {
        Iterator<Map.Entry<String, JsonNode>> it = defsObj.fields();
        while (it.hasNext()) {
          Map.Entry<String, JsonNode> entry = it.next();
          JsonNode defNode = entry.getValue();
          rewriteDefRefs(defNode);
          normaliseForOpenApi30(defNode);
          Schema<?> defSchema = jsonMapper.treeToValue(defNode, Schema.class);
          defs.put(entry.getKey(), defSchema);
        }
      }
      Schema<?> topSchema = jsonMapper.treeToValue(rootObj, Schema.class);
      return new JsonEnvelopeSchema(topSchema, Map.copyOf(defs));
    } catch (IOException e) {
      LOGGER.warn(
          "Could not load bundled envelope schema; falling back to an empty object schema: {}",
          e.getMessage());
      return new JsonEnvelopeSchema(new ObjectSchema(), Map.of());
    }
  }

  /**
   * Rewrites JSON Schema 2020-12 idioms the envelope uses into shapes the OpenAPI 3.0 {@link
   * Schema} deserializer accepts:
   *
   * <ul>
   *   <li>{@code "type": ["X", "null"]} → {@code "type": "X", "nullable": true}
   *   <li>{@code "type": ["X", "Y", ...]} (no {@code null}) → drop {@code type} and add an {@code
   *       oneOf} of single-type schemas
   *   <li>{@code "const": X} → {@code "enum": [X]}
   * </ul>
   *
   * Other 3.1-only idioms ({@code prefixItems}, etc.) are left alone — the deserializer tolerates
   * them as schema extensions or extras.
   */
  private static void normaliseForOpenApi30(JsonNode node) {
    if (node instanceof ObjectNode obj) {
      JsonNode typeNode = obj.get("type");
      if (typeNode instanceof ArrayNode typeArr) {
        List<String> types = new java.util.ArrayList<>();
        boolean nullable = false;
        typeArr.forEach(
            t -> {
              if (t.isTextual()) {
                if ("null".equals(t.asText())) {
                  // sentinel; handled outside
                } else {
                  types.add(t.asText());
                }
              }
            });
        nullable =
            java.util.stream.StreamSupport.stream(typeArr.spliterator(), false)
                .anyMatch(t -> t.isTextual() && "null".equals(t.asText()));
        obj.remove("type");
        if (types.size() == 1) {
          obj.put("type", types.get(0));
        } else if (types.size() > 1) {
          ArrayNode oneOf = obj.withArray("oneOf");
          for (String t : types) {
            ObjectNode branch = oneOf.addObject();
            branch.put("type", t);
          }
        }
        if (nullable) {
          obj.put("nullable", true);
        }
      }
      JsonNode constNode = obj.remove("const");
      if (constNode != null && !obj.has("enum")) {
        ArrayNode enumArr = obj.withArray("enum");
        enumArr.add(constNode);
      }
      obj.fields().forEachRemaining(e -> normaliseForOpenApi30(e.getValue()));
    } else if (node instanceof ArrayNode arr) {
      arr.elements().forEachRemaining(EndpointTransactions::normaliseForOpenApi30);
    }
  }

  private static void rewriteDefRefs(JsonNode node) {
    if (node instanceof ObjectNode obj) {
      JsonNode ref = obj.get("$ref");
      if (ref != null && ref.isTextual()) {
        String value = ref.asText();
        String prefix = "#/$defs/";
        if (value.startsWith(prefix)) {
          obj.put("$ref", "#/components/schemas/" + value.substring(prefix.length()));
        }
      }
      obj.fields().forEachRemaining(e -> rewriteDefRefs(e.getValue()));
    } else if (node instanceof ArrayNode arr) {
      arr.elements().forEachRemaining(EndpointTransactions::rewriteDefRefs);
    }
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();
    builder.add("http://www.opengis.net/spec/ogcapi-features-11/0.0/conf/transactions");
    builder.add("http://www.opengis.net/spec/ogcapi-features-11/0.0/conf/json-transactions");
    builder.add("http://www.opengis.net/spec/ogcapi-features-11/0.0/conf/features");

    Optional<TransactionsConfiguration> cfg = apiData.getExtension(TransactionsConfiguration.class);
    if (cfg.map(c -> !Objects.equals(c.getAtomic(), false)).orElse(true)) {
      builder.add("http://www.opengis.net/spec/ogcapi-features-11/0.0/conf/atomic-transactions");
    }
    if (cfg.map(c -> !Objects.equals(c.getBatch(), false)).orElse(true)) {
      builder.add("http://www.opengis.net/spec/ogcapi-features-11/0.0/conf/batch-transactions");
    }
    if (cfg.map(c -> Objects.equals(c.getAsync(), true)).orElse(false)) {
      builder.add("http://www.opengis.net/spec/ogcapi-features-11/0.0/conf/async-transactions");
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
    HttpMethods method = HttpMethods.POST;
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path, method);
    List<ApiHeader> headers = getHeaders(extensionRegistry, apiData, path, method);
    String operationSummary = "execute a transaction";
    Optional<String> operationDescription =
        Optional.of(
            "Executes one or more insert, replace, update or delete actions over the feature "
                + "collections of the API. The request body is a transaction document; supported "
                + "media types: `application/ogc-tx+json` (an OGC JSON Transaction object)"
                + (apiData
                        .getExtension(TransactionsConfiguration.class)
                        .map(TransactionsConfiguration::getWfsTransaction)
                        .orElse(false)
                    ? " and `application/xml` (a `wfs:Transaction` element)."
                    : "."));
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
    Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);
    // TODO: Content is {}
    Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
    ApiOperation.of(
            requestContent,
            responseContent,
            queryParameters,
            headers,
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("executeTransaction"),
            GROUP_DATA_WRITE,
            TAGS,
            TransactionsBuildingBlock.MATURITY,
            TransactionsBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));
    definitionBuilder.putResources(path, resourceBuilder.build());

    return definitionBuilder.build();
  }

  // The transaction body is streamed end-to-end (WfsTransactionParser / JsonTransactionParser are
  // streaming StAX/Jackson parsers and the endpoint receives the raw InputStream below). The
  // default dispatcher path eagerly buffers the entire entity into a single byte[] for policy
  // authorization inspection, which (a) caps any request body at ~2 GiB because of Java's array
  // length limit (OOM "Required array size too large" in InputStream.readAllBytes) and (b) defeats
  // the streaming parser by holding the whole payload in heap before the endpoint runs. Opt out so
  // bulk inserts / deletes can flow through without buffering. Policy attribute resolvers will not
  // see the body for transactions; they don't today either, since no resolver introspects WFS-T or
  // OGC-Tx payloads.
  @Override
  public boolean skipBodyParsing() {
    return true;
  }

  @POST
  @Consumes({"application/ogc-tx+json", "application/xml"})
  @Produces({"application/json", "application/problem+json"})
  public Response postTransaction(
      @Auth Optional<User> optionalUser,
      @HeaderParam("Content-Crs") String contentCrsHeader,
      @HeaderParam("OGC-Mutation-Datetime") String mutationDatetimeHeader,
      @HeaderParam("Prefer") List<String> prefer,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpServletRequest request,
      InputStream requestBody) {

    boolean async = HeaderPrefer.containsToken(prefer, "respond-async");
    if (async
        && !api.getData()
            .getExtension(TransactionsConfiguration.class)
            .map(TransactionsConfiguration::getAsync)
            .map(Boolean.TRUE::equals)
            .orElse(false)) {
      return Response.status(Response.Status.NOT_IMPLEMENTED)
          .type(MediaType.TEXT_PLAIN_TYPE)
          .entity("Asynchronous transactions are not supported by this API.")
          .build();
    }

    // parsed without fallback first, so responses can echo exactly the preferences the client sent
    HeaderPrefer.Handling sentHandling =
        HeaderPrefer.parseParameterised(
            prefer, "handling", HeaderPrefer.Handling::fromHeader, null);
    HeaderPrefer.Return sentReturn =
        HeaderPrefer.parseParameterised(prefer, "return", HeaderPrefer.Return::fromHeader, null);

    HeaderPrefer.Handling handling =
        sentHandling != null ? sentHandling : HeaderPrefer.Handling.LENIENT;
    HeaderPrefer.Return ret = sentReturn != null ? sentReturn : HeaderPrefer.Return.REPRESENTATION;

    if (async) {
      // validates the headers (content type, parser, CRS, mutation datetime) before anything is
      // spooled; the returned input is not used, the body stream is not consumed
      createQueryInput(
          api,
          request.getContentType(),
          contentCrsHeader,
          mutationDatetimeHeader,
          handling,
          ret,
          requestBody);

      return submitAsync(
          requestContext,
          request.getContentType(),
          contentCrsHeader,
          mutationDatetimeHeader,
          handling,
          ret,
          sentHandling,
          sentReturn,
          HeaderPrefer.parseWait(prefer).filter(seconds -> seconds > 0),
          requestBody);
    }

    CommandHandlerTransactions.QueryInputTransaction queryInput =
        ImmutableQueryInputTransaction.builder()
            .from(
                createQueryInput(
                    api,
                    request.getContentType(),
                    contentCrsHeader,
                    mutationDatetimeHeader,
                    handling,
                    ret,
                    requestBody))
            .requestedHandling(Optional.ofNullable(sentHandling))
            .requestedReturn(Optional.ofNullable(sentReturn))
            .build();

    return commandHandler.processTransaction(queryInput, requestContext);
  }

  /**
   * Spools the request body to the resources store, pushes a job that executes the transaction in
   * the background, and returns 202 with the job id. Depending on the {@code return} preference the
   * result document lands in the job outputs (as a map) or in the resources store (full
   * representations can be large).
   */
  private Response submitAsync(
      ApiRequestContext requestContext,
      String contentTypeHeader,
      @Nullable String contentCrsHeader,
      @Nullable String mutationDatetimeHeader,
      HeaderPrefer.Handling handling,
      HeaderPrefer.Return ret,
      @Nullable HeaderPrefer.Handling sentHandling,
      @Nullable HeaderPrefer.Return sentReturn,
      Optional<Integer> sentWait,
      InputStream requestBody) {
    OgcApiDataV2 apiData = requestContext.getApi().getData();
    java.nio.file.Path documentPath =
        java.nio.file.Path.of(apiData.getId(), "document_" + UUID.randomUUID());
    long totalBytes;
    try {
      CountingInputStream countingBody = new CountingInputStream(requestBody);
      documentStore.put(documentPath, countingBody);
      totalBytes = countingBody.getCount();
    } catch (IOException e) {
      throw new IllegalStateException("Could not store the transaction document", e);
    }

    JobConfiguration jobConfiguration =
        TransactionJob.of(
            apiData.getId(),
            documentPath,
            contentTypeHeader,
            contentCrsHeader,
            mutationDatetimeHeader,
            handling,
            ret,
            ret == HeaderPrefer.Return.REPRESENTATION);

    Job job;
    try {
      job = jobs.push(jobConfiguration);
    } catch (RuntimeException e) {
      // do not leave an orphaned document behind when the job never made it into the queue
      try {
        documentStore.delete(documentPath);
      } catch (IOException cleanupError) {
        LOGGER.warn("Could not delete the orphaned transaction document {}", documentPath);
      }
      throw e;
    }

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Accepted asynchronous transaction as job {} ({} bytes)", job.id(), totalBytes);
    }

    String sentPreferences =
        (sentReturn != null ? ", return=" + sentReturn.headerValue() : "")
            + (sentHandling != null ? ", handling=" + sentHandling.headerValue() : "");

    // honor the wait preference: defer the response for up to min(wait, maxWait) seconds and
    // answer synchronously when the job finishes in time
    if (sentWait.isPresent()) {
      int maxWait =
          Optional.ofNullable(
                  apiData
                      .getExtension(TransactionsConfiguration.class)
                      .map(TransactionsConfiguration::getMaxWait)
                      .orElse(null))
              .orElse(60);
      int waitSeconds = Math.min(sentWait.get(), maxWait);
      boolean waitCapped = waitSeconds < sentWait.get();
      try {
        Job finished = jobs.waitFor(job.id()).get(waitSeconds, TimeUnit.SECONDS);
        Optional<Response> syncResponse =
            syncResponse(finished, sentHandling, sentReturn, sentWait.get());
        if (syncResponse.isPresent()) {
          return syncResponse.get();
        }
      } catch (TimeoutException e) {
        // the job keeps running, fall through to 202
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } catch (ExecutionException e) {
        LOGGER.warn("Waiting for transaction job {} failed", job.id(), e);
      }
      if (waitCapped) {
        // the client's wait was not fully honored, do not claim it
        sentWait = Optional.empty();
      }
    }

    String preferenceApplied =
        "respond-async" + sentPreferences + sentWait.map(seconds -> ", wait=" + seconds).orElse("");

    // TODO: the jobs API is not yet available; the Location target ({apiUri}/jobs/{jobId})
    // returns 404 until it lands
    URI location =
        URI.create(
            requestContext.getApiUriCustomizer().appendPathSegments("jobs", job.id()).toString());

    return Response.accepted()
        .location(location)
        .header("Preference-Applied", preferenceApplied)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(Map.of("jobId", job.id()))
        .build();
  }

  /**
   * The regular synchronous response, reconstructed from a finished job: the result document from
   * the resources store (deleted once read) or the job outputs, 204 when there is neither. Empty
   * when the job failed — the client then gets the 202 and can inspect the job.
   */
  private Optional<Response> syncResponse(
      Job finished,
      @Nullable HeaderPrefer.Handling sentHandling,
      @Nullable HeaderPrefer.Return sentReturn,
      int sentWait) {
    if (finished.status() != Identifiers.Status.SUCCESSFUL) {
      return Optional.empty();
    }

    try {
      Object resultPath = finished.outputs().get("resultPath");
      if (resultPath != null) {
        java.nio.file.Path path = java.nio.file.Path.of(resultPath.toString());
        Optional<InputStream> result = documentStore.content(path);
        if (result.isEmpty()) {
          return Optional.empty();
        }
        String document;
        try (InputStream in = result.get()) {
          document = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // the response is delivered synchronously, the stored copy is no longer needed
        documentStore.delete(path);
        return Optional.of(
            Response.ok(document)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .header(
                    "Preference-Applied",
                    waitPreferenceApplied(sentHandling, sentReturn, sentWait, true))
                .build());
      }

      if (!finished.outputs().isEmpty()) {
        // a document was produced although return=none was requested (failures/warnings override
        // it), so a requested return=none was not applied
        boolean returnApplied = sentReturn != HeaderPrefer.Return.NONE;
        return Optional.of(
            Response.ok(Jobs.DEFAULT_MAPPER.writeValueAsString(finished.outputs()))
                .type(MediaType.APPLICATION_JSON_TYPE)
                .header(
                    "Preference-Applied",
                    waitPreferenceApplied(sentHandling, sentReturn, sentWait, returnApplied))
                .build());
      }

      // Prefer: return=none on a successful transaction has no response document
      return Optional.of(
          Response.noContent()
              .header(
                  "Preference-Applied",
                  waitPreferenceApplied(sentHandling, sentReturn, sentWait, true))
              .build());
    } catch (IOException e) {
      LOGGER.warn("Could not read the result of transaction job {}", finished.id(), e);
      return Optional.empty();
    }
  }

  /** Unlike the 202 variant there is no leading token; wait is always present and last. */
  private static String waitPreferenceApplied(
      @Nullable HeaderPrefer.Handling sentHandling,
      @Nullable HeaderPrefer.Return sentReturn,
      int sentWait,
      boolean returnApplied) {
    return (sentReturn != null && returnApplied ? "return=" + sentReturn.headerValue() + ", " : "")
        + (sentHandling != null ? "handling=" + sentHandling.headerValue() + ", " : "")
        + "wait="
        + sentWait;
  }

  public CommandHandlerTransactions.QueryInputTransaction createQueryInput(
      OgcApi api,
      String contentTypeHeader,
      @Nullable String contentCrsHeader,
      @Nullable String mutationDatetimeHeader,
      Handling handling,
      Return ret,
      InputStream transactionDocument) {

    OgcApiDataV2 apiData = api.getData();
    TransactionsConfiguration config =
        apiData
            .getExtension(TransactionsConfiguration.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Transactions building block is not configured for this API"));

    MediaType contentType = parseContentType(contentTypeHeader);
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
      throw new BadRequestException("wfs:Transaction payloads are not enabled for this API");
    }

    EpsgCrs requestCrs = HeaderContentCrs.parse(contentCrsHeader, apiData, crsSupport);

    Optional<Instant> mutationDatetime = parseMutationDatetime(mutationDatetimeHeader);

    return ImmutableQueryInputTransaction.builder()
        .parser(parser)
        .requestBody(transactionDocument)
        .contentType(contentType)
        .config(config)
        .requestCrs(requestCrs)
        .mutationDatetime(mutationDatetime)
        .handling(handling)
        .returnPreference(ret)
        .build();
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

  // The canonical RFC 3339 parser lives on HeaderOgcMutationDatetime in the versioned-features
  // module; mirrored here so this endpoint stays in the transactions module (which does not, and
  // should not, depend on versioned-features).
  private static Optional<Instant> parseMutationDatetime(String header) {
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(header.trim()));
    } catch (DateTimeParseException e) {
      throw new BadRequestException(
          "Invalid OGC-Mutation-Datetime header (expected an RFC 3339 date-time in UTC): "
              + header);
    }
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(commandHandler, providers.getFeatureProviderOrThrow(apiData));
  }
}
