/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.transactions.domain.ImmutableNameValue;
import de.ii.ogcapi.transactions.domain.ImmutableTxDelete;
import de.ii.ogcapi.transactions.domain.ImmutableTxReplace;
import de.ii.ogcapi.transactions.domain.ImmutableTxUpdate;
import de.ii.ogcapi.transactions.domain.InsertItem;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionParser;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.transactions.domain.TxInsert;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.ogcapi.transactions.domain.TxUpdate.NameValue;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.CqlParseException;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Streaming parser for {@code application/ogc-tx+json} / {@code application/json} transaction
 * bodies as defined by OGC API Features Part 11 §10.2.
 *
 * <p>Memory bound: at most one transaction action header is held at a time, and within an insert
 * action at most one feature payload is buffered. Streaming requires the following field-order
 * constraints inside a JSON action object:
 *
 * <ul>
 *   <li>{@code action} and {@code collection} MUST appear before {@code items} (insert).
 *   <li>Any {@code id}/{@code title}/{@code description} appearing after {@code items} is silently
 *       skipped, as the action header has already been emitted to the executor.
 * </ul>
 */
@Singleton
@AutoBind
public class JsonTransactionParser implements TransactionParser {

  static final String MEDIA_TYPE_OGC_TX_JSON = "application/ogc-tx+json";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DEFAULT_FILTER_LANG_JSON = "cql2-json";
  private static final String FILTER_LANG_CQL2_TEXT = "cql2-text";
  private static final String ENVELOPE_SCHEMA_RESOURCE =
      "/de/ii/ogcapi/transactions/transaction-envelope.json";

  private final Cql cql;
  private final SchemaValidator schemaValidator;
  private final String envelopeSchema;

  @Inject
  public JsonTransactionParser(Cql cql, SchemaValidator schemaValidator) {
    this.cql = cql;
    this.schemaValidator = schemaValidator;
    this.envelopeSchema = loadEnvelopeSchema();
  }

  private static String loadEnvelopeSchema() {
    try (InputStream in =
        JsonTransactionParser.class.getResourceAsStream(ENVELOPE_SCHEMA_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(
            "Bundled envelope schema resource not found: " + ENVELOPE_SCHEMA_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not load bundled envelope schema", e);
    }
  }

  @Override
  public boolean canParse(MediaType mediaType) {
    if (mediaType == null) {
      return false;
    }
    return MEDIA_TYPE_OGC_TX_JSON.equalsIgnoreCase(
            mediaType.getType() + "/" + mediaType.getSubtype())
        || MediaType.APPLICATION_JSON_TYPE.isCompatible(mediaType);
  }

  @Override
  public void validateEnvelope(byte[] body, MediaType mediaType) {
    String json = new String(body, StandardCharsets.UTF_8);
    Optional<String> error;
    try {
      error = schemaValidator.validate(envelopeSchema, json);
    } catch (IOException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
    if (error.isPresent()) {
      throw new IllegalArgumentException(error.get());
    }
  }

  @Override
  public Transaction parse(InputStream body, MediaType mediaType) {
    JsonParser parser;
    try {
      parser = MAPPER.getFactory().createParser(body);
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not open transaction body as JSON", e);
    }
    try {
      TxSemantic semantic = readEnvelope(parser);
      return new JsonTransactionImpl(parser, body, semantic, cql);
    } catch (IOException e) {
      closeQuietly(parser);
      closeQuietly(body);
      throw new IllegalArgumentException("Could not parse transaction body as JSON", e);
    } catch (RuntimeException e) {
      closeQuietly(parser);
      closeQuietly(body);
      throw e;
    }
  }

  /**
   * Reads the outer object up to and including the {@code transaction} field's {@code [}, returning
   * the parsed {@code semantic}. After this call the parser is positioned ready to read either an
   * action {@code {} or the {@code ]} terminator.
   */
  private TxSemantic readEnvelope(JsonParser parser) throws IOException {
    if (parser.nextToken() != JsonToken.START_OBJECT) {
      throw new IllegalArgumentException("Transaction body must be a JSON object");
    }
    TxSemantic semantic = TxSemantic.ATOMIC;
    while (parser.nextToken() == JsonToken.FIELD_NAME) {
      String field = parser.currentName();
      JsonToken value = parser.nextToken();
      if ("semantic".equals(field)) {
        if (value != JsonToken.VALUE_STRING && value != JsonToken.VALUE_NULL) {
          throw new IllegalArgumentException("'semantic' must be a string");
        }
        if (value == JsonToken.VALUE_STRING) {
          semantic = TxSemantic.fromJsonValue(parser.getValueAsString());
        }
      } else if ("transaction".equals(field)) {
        if (value != JsonToken.START_ARRAY) {
          throw new IllegalArgumentException("'transaction' must be a JSON array of actions");
        }
        return semantic;
      } else {
        parser.skipChildren();
      }
    }
    throw new IllegalArgumentException(
        "Transaction body must contain a 'transaction' array of actions");
  }

  private static void closeQuietly(AutoCloseable c) {
    if (c == null) return;
    try {
      c.close();
    } catch (Exception ignore) {
      // best-effort
    }
  }

  /* ---------- Streaming Transaction implementation ---------- */

  private static final class JsonTransactionImpl implements Transaction {

    private final JsonParser parser;
    private final InputStream body;
    private final TxSemantic semantic;
    private final JsonActionIterator iterator;
    private boolean closed;

    JsonTransactionImpl(JsonParser parser, InputStream body, TxSemantic semantic, Cql cql) {
      this.parser = parser;
      this.body = body;
      this.semantic = semantic;
      this.iterator = new JsonActionIterator(parser, cql);
    }

    @Override
    public TxSemantic getSemantic() {
      return semantic;
    }

    @Override
    public Iterator<TxAction> actions() {
      return iterator;
    }

    @Override
    public void close() {
      if (closed) return;
      closed = true;
      closeQuietly(parser);
      closeQuietly(body);
    }
  }

  /**
   * Walks the {@code transaction[]} array, yielding one action at a time. If the previous action
   * was a streaming insert, this iterator ensures its body has been fully drained before reading
   * the next action.
   */
  private static final class JsonActionIterator implements Iterator<TxAction> {

    private final JsonParser parser;
    private final Cql cql;
    private TxAction pending;
    private StreamingInsert previous;
    private boolean exhausted;
    private int index;

    JsonActionIterator(JsonParser parser, Cql cql) {
      this.parser = parser;
      this.cql = cql;
    }

    @Override
    public boolean hasNext() {
      if (pending != null) return true;
      if (exhausted) return false;
      try {
        if (previous != null) {
          previous.drainRemainder();
          previous = null;
        }
        JsonToken t = parser.nextToken();
        if (t == JsonToken.END_ARRAY) {
          exhausted = true;
          return false;
        }
        if (t != JsonToken.START_OBJECT) {
          throw new IllegalArgumentException(
              "transaction[" + index + "] must be a JSON object, got " + t);
        }
        pending = parseAction();
        index++;
        return true;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public TxAction next() {
      if (!hasNext()) throw new NoSuchElementException();
      TxAction r = pending;
      pending = null;
      if (r instanceof StreamingInsert) {
        previous = (StreamingInsert) r;
      }
      return r;
    }

    /**
     * Parses one action object. Parser is at START_OBJECT on entry; on return it is at END_OBJECT
     * (eager actions) or at the start of {@code items}'s value array (streaming insert; the
     * returned {@link StreamingInsert} will drain to END_OBJECT lazily).
     */
    private TxAction parseAction() throws IOException {
      String action = null;
      String collectionId = null;
      Optional<String> actionId = Optional.empty();
      Optional<String> title = Optional.empty();
      Optional<String> description = Optional.empty();
      JsonNode filterNode = null;
      String filterLang = null;
      Optional<EpsgCrs> filterCrs = Optional.empty();
      JsonNode properties = null;
      int actionIndex = index;

      while (parser.nextToken() == JsonToken.FIELD_NAME) {
        String field = parser.currentName();
        parser.nextToken();
        switch (field) {
          case "action":
            action = parser.getValueAsString();
            break;
          case "collection":
            collectionId = parser.getValueAsString();
            break;
          case "id":
            actionId = stringOpt(parser);
            break;
          case "title":
            title = stringOpt(parser);
            break;
          case "description":
            description = stringOpt(parser);
            break;
          case "filter":
            filterNode = MAPPER.readTree(parser);
            break;
          case "filter-lang":
            filterLang = parser.getValueAsString();
            break;
          case "filter-crs":
            filterCrs = parseFilterCrs(parser.getValueAsString());
            break;
          case "properties":
            properties = MAPPER.readTree(parser);
            break;
          case "items":
            return startStreamingInsert(
                actionIndex, action, collectionId, actionId, title, description);
          default:
            parser.skipChildren();
            break;
        }
      }
      // parser now at END_OBJECT
      if (action == null) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "] is missing required 'action' property");
      }
      if (collectionId == null) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "] is missing required 'collection' property");
      }
      TxActionType type = TxActionType.fromJsonValue(action);
      switch (type) {
        case INSERT:
          throw new IllegalArgumentException(
              "transaction[" + actionIndex + "] (insert) requires a non-empty 'items' array");
        case REPLACE:
          return buildReplace(
              actionIndex,
              collectionId,
              actionId,
              title,
              description,
              properties,
              filterNode,
              filterLang,
              filterCrs);
        case UPDATE:
          return buildUpdate(
              actionIndex,
              collectionId,
              actionId,
              title,
              description,
              properties,
              filterNode,
              filterLang,
              filterCrs);
        case DELETE:
          return buildDelete(
              actionIndex,
              collectionId,
              actionId,
              title,
              description,
              filterNode,
              filterLang,
              filterCrs);
        default:
          throw new IllegalArgumentException("Unsupported action type: " + action);
      }
    }

    private TxAction startStreamingInsert(
        int actionIndex,
        String action,
        String collectionId,
        Optional<String> actionId,
        Optional<String> title,
        Optional<String> description)
        throws IOException {
      if (action == null || !TxActionType.INSERT.toJsonValue().equalsIgnoreCase(action)) {
        throw new IllegalArgumentException(
            "transaction["
                + actionIndex
                + "]: 'items' encountered but 'action' is not 'insert' or was not declared before"
                + " 'items' (the streaming parser requires 'action' and 'collection' to appear"
                + " before 'items')");
      }
      if (collectionId == null) {
        throw new IllegalArgumentException(
            "transaction["
                + actionIndex
                + "]: 'collection' must appear before 'items' in a streaming insert action");
      }
      if (parser.currentToken() != JsonToken.START_ARRAY) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "].items must be an array");
      }
      return new StreamingInsert(parser, collectionId, actionId, title, description, actionIndex);
    }

    private TxAction buildReplace(
        int actionIndex,
        String collectionId,
        Optional<String> actionId,
        Optional<String> title,
        Optional<String> description,
        JsonNode properties,
        JsonNode filterNode,
        String filterLang,
        Optional<EpsgCrs> filterCrs) {
      if (properties == null || !properties.isObject()) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "] (replace) requires a 'properties' object");
      }
      JsonNode feature = properties.get("feature");
      if (feature == null || !feature.isObject()) {
        throw new IllegalArgumentException(
            "transaction["
                + actionIndex
                + "] (replace) requires 'properties.feature' to be a GeoJSON Feature");
      }
      return new ImmutableTxReplace.Builder()
          .collectionId(collectionId)
          .actionId(actionId)
          .title(title)
          .description(description)
          .feature(toBytes(feature, "transaction[" + actionIndex + "].properties.feature"))
          .mediaType(MediaType.valueOf("application/geo+json"))
          .filter(parseFilter(filterNode, filterLang, filterCrs, actionIndex))
          .filterCrs(filterCrs)
          .build();
    }

    private TxAction buildUpdate(
        int actionIndex,
        String collectionId,
        Optional<String> actionId,
        Optional<String> title,
        Optional<String> description,
        JsonNode properties,
        JsonNode filterNode,
        String filterLang,
        Optional<EpsgCrs> filterCrs) {
      if (properties == null || !properties.isObject()) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "] (update) requires a 'properties' object");
      }
      List<NameValue> add = readNameValues(properties.get("add"), actionIndex, "add");
      List<NameValue> modify = readNameValues(properties.get("modify"), actionIndex, "modify");
      List<String> deletes = readStringArray(properties.get("delete"), actionIndex, "delete");
      if (add.isEmpty() && modify.isEmpty() && deletes.isEmpty()) {
        throw new IllegalArgumentException(
            "transaction["
                + actionIndex
                + "] (update) must specify at least one of 'add', 'modify' or 'delete'");
      }
      return new ImmutableTxUpdate.Builder()
          .collectionId(collectionId)
          .actionId(actionId)
          .title(title)
          .description(description)
          .add(add)
          .modify(modify)
          .deleteProperties(deletes)
          .filter(parseFilter(filterNode, filterLang, filterCrs, actionIndex))
          .filterCrs(filterCrs)
          .build();
    }

    private TxAction buildDelete(
        int actionIndex,
        String collectionId,
        Optional<String> actionId,
        Optional<String> title,
        Optional<String> description,
        JsonNode filterNode,
        String filterLang,
        Optional<EpsgCrs> filterCrs) {
      return new ImmutableTxDelete.Builder()
          .collectionId(collectionId)
          .actionId(actionId)
          .title(title)
          .description(description)
          .filter(parseFilter(filterNode, filterLang, filterCrs, actionIndex))
          .filterCrs(filterCrs)
          .build();
    }

    private Optional<Cql2Expression> parseFilter(
        JsonNode filterNode, String filterLang, Optional<EpsgCrs> filterCrs, int actionIndex) {
      if (filterNode == null || filterNode.isNull()) {
        return Optional.empty();
      }
      String langDefault =
          filterNode.isTextual() ? FILTER_LANG_CQL2_TEXT : DEFAULT_FILTER_LANG_JSON;
      String effectiveLang = filterLang == null ? langDefault : filterLang;
      Cql.Format format =
          FILTER_LANG_CQL2_TEXT.equalsIgnoreCase(effectiveLang) ? Cql.Format.TEXT : Cql.Format.JSON;
      EpsgCrs crs = filterCrs.orElse(OgcCrs.CRS84);

      String filterText;
      try {
        filterText =
            filterNode.isTextual() ? filterNode.asText() : MAPPER.writeValueAsString(filterNode);
      } catch (IOException e) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "].filter could not be serialised", e);
      }
      try {
        return Optional.of(cql.read(filterText, format, crs));
      } catch (CqlParseException e) {
        throw new IllegalArgumentException(
            "transaction["
                + actionIndex
                + "].filter is not a valid CQL2 expression: "
                + e.getMessage(),
            e);
      }
    }

    private static Optional<EpsgCrs> parseFilterCrs(String value) {
      if (value == null) {
        return Optional.empty();
      }
      try {
        return Optional.of(EpsgCrs.fromString(value));
      } catch (Throwable e) {
        throw new IllegalArgumentException("Invalid 'filter-crs': " + e.getMessage(), e);
      }
    }

    private static List<NameValue> readNameValues(JsonNode node, int actionIndex, String field) {
      if (node == null || node.isNull()) {
        return ImmutableList.of();
      }
      if (!node.isArray()) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "].properties." + field + " must be an array");
      }
      List<NameValue> values = new ArrayList<>(node.size());
      int j = 0;
      for (JsonNode entry : node) {
        if (!entry.isObject() || entry.get("name") == null || !entry.get("name").isTextual()) {
          throw new IllegalArgumentException(
              "transaction["
                  + actionIndex
                  + "].properties."
                  + field
                  + "["
                  + j
                  + "] must be an object with a textual 'name'");
        }
        JsonNode value = entry.get("value");
        values.add(
            new ImmutableNameValue.Builder()
                .name(entry.get("name").asText())
                .value(value == null ? MAPPER.nullNode() : value)
                .build());
        j++;
      }
      return ImmutableList.copyOf(values);
    }

    private static List<String> readStringArray(JsonNode node, int actionIndex, String field) {
      if (node == null || node.isNull()) {
        return ImmutableList.of();
      }
      if (!node.isArray()) {
        throw new IllegalArgumentException(
            "transaction[" + actionIndex + "].properties." + field + " must be an array");
      }
      ImmutableList.Builder<String> out = ImmutableList.builder();
      int j = 0;
      for (JsonNode v : node) {
        if (!v.isTextual()) {
          throw new IllegalArgumentException(
              "transaction["
                  + actionIndex
                  + "].properties."
                  + field
                  + "["
                  + j
                  + "] must be a string");
        }
        out.add(v.asText());
        j++;
      }
      return out.build();
    }

    private static Optional<String> stringOpt(JsonParser parser) throws IOException {
      JsonToken t = parser.currentToken();
      if (t == JsonToken.VALUE_NULL) return Optional.empty();
      return Optional.ofNullable(parser.getValueAsString());
    }

    private static byte[] toBytes(JsonNode node, String pointer) {
      try {
        return MAPPER.writeValueAsBytes(node);
      } catch (IOException e) {
        throw new IllegalArgumentException("Could not serialise " + pointer, e);
      }
    }
  }

  /**
   * Streaming insert backed by a {@link JsonParser}. {@link #items()} returns a single-use iterator
   * that pulls one feature object at a time. After exhaustion (either explicit drain or via {@link
   * #drainRemainder()} called by the parent iterator), the parser is positioned at the action's
   * END_OBJECT.
   */
  private static final class StreamingInsert implements TxInsert {

    private final JsonParser parser;
    private final String collectionId;
    private final Optional<String> actionId;
    private final Optional<String> title;
    private final Optional<String> description;
    private final int actionIndex;
    private boolean itemsConsumed;
    private boolean arrayDrained;
    private boolean objectDrained;

    StreamingInsert(
        JsonParser parser,
        String collectionId,
        Optional<String> actionId,
        Optional<String> title,
        Optional<String> description,
        int actionIndex) {
      this.parser = parser;
      this.collectionId = collectionId;
      this.actionId = actionId;
      this.title = title;
      this.description = description;
      this.actionIndex = actionIndex;
    }

    @Override
    public String getCollectionId() {
      return collectionId;
    }

    @Override
    public Optional<String> getActionId() {
      return actionId;
    }

    @Override
    public Optional<String> getTitle() {
      return title;
    }

    @Override
    public Optional<String> getDescription() {
      return description;
    }

    @Override
    public MediaType getMediaType() {
      return MediaType.valueOf("application/geo+json");
    }

    @Override
    public Iterator<InsertItem> items() {
      if (itemsConsumed) {
        throw new IllegalStateException(
            "items() has already been called on transaction[" + actionIndex + "]");
      }
      itemsConsumed = true;
      return new Iterator<>() {
        private InsertItem next;
        private int featureIndex = 0;

        @Override
        public boolean hasNext() {
          if (next != null) return true;
          if (arrayDrained) return false;
          try {
            JsonToken t = parser.nextToken();
            if (t == JsonToken.END_ARRAY) {
              arrayDrained = true;
              return false;
            }
            if (t != JsonToken.START_OBJECT) {
              throw new IllegalArgumentException(
                  "transaction[" + actionIndex + "].items must contain JSON Feature objects");
            }
            JsonNode feature = MAPPER.readTree(parser);
            featureIndex++;
            JsonNode idNode = feature.get("id");
            Optional<String> featureId =
                idNode == null || idNode.isNull() ? Optional.empty() : Optional.of(idNode.asText());
            next =
                new InsertItem(
                    featureId,
                    featureIndex,
                    new ByteArrayInputStream(MAPPER.writeValueAsBytes(feature)));
            return true;
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }

        @Override
        public InsertItem next() {
          if (!hasNext()) throw new NoSuchElementException();
          InsertItem r = next;
          next = null;
          return r;
        }
      };
    }

    /**
     * Consumes any tokens remaining in this action's JSON object so the parent action iterator can
     * read the next action cleanly. Safe to call even if items() was never invoked or was only
     * partially consumed.
     */
    void drainRemainder() throws IOException {
      if (!arrayDrained) {
        // skip the unread tail of the items array
        int depth = 1;
        while (depth > 0) {
          JsonToken t = parser.nextToken();
          if (t == null) return;
          if (t == JsonToken.START_ARRAY || t == JsonToken.START_OBJECT) depth++;
          else if (t == JsonToken.END_ARRAY || t == JsonToken.END_OBJECT) depth--;
        }
        arrayDrained = true;
      }
      if (objectDrained) return;
      // drain any remaining FIELD_NAME / value pairs until END_OBJECT
      while (true) {
        JsonToken t = parser.nextToken();
        if (t == null || t == JsonToken.END_OBJECT) {
          objectDrained = true;
          return;
        }
        if (t == JsonToken.FIELD_NAME) {
          parser.nextToken();
          parser.skipChildren();
        }
      }
    }
  }
}
