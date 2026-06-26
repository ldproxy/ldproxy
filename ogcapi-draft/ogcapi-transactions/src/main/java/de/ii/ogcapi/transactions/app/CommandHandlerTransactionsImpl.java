/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.transactions.app.CommandHandlerTransactions.QueryInputTransaction;
import de.ii.ogcapi.transactions.domain.ActionResult;
import de.ii.ogcapi.transactions.domain.ActionStatus;
import de.ii.ogcapi.transactions.domain.ExecutionResult;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionExecutor;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

@Singleton
@AutoBind
public class CommandHandlerTransactionsImpl extends AbstractVolatileComposed
    implements CommandHandlerTransactions {

  private static final MediaType APPLICATION_JSON = MediaType.APPLICATION_JSON_TYPE;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final TransactionExecutor executor;

  @Inject
  public CommandHandlerTransactionsImpl(
      TransactionExecutor executor, VolatileRegistry volatileRegistry) {
    super(CommandHandlerTransactions.class.getSimpleName(), volatileRegistry, true);
    this.executor = executor;

    onVolatileStart();

    addSubcomponent(executor);

    onVolatileStarted();
  }

  @Override
  public Response processTransaction(
      QueryInputTransaction queryInput, ApiRequestContext requestContext) {
    Transaction transaction = null;

    try {
      transaction =
          queryInput.getParser().parse(queryInput.getRequestBody(), queryInput.getContentType());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Could not parse transaction body: " + e.getMessage());
    }

    try {
      enforceSemanticPolicy(transaction, queryInput.getConfig());
      transaction = enforceActionLimit(transaction, queryInput.getConfig());

      ExecutionResult result;
      try {
        result =
            executor.execute(
                transaction,
                requestContext.getApi(),
                requestContext,
                queryInput.getRequestCrs(),
                queryInput.getHandling(),
                queryInput.getMutationDatetime());
        transaction = null;
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Could not parse transaction body: " + e.getMessage());
      }

      return buildResponse(
          result, queryInput.getReturnPreference(), queryInput.getHandling(), requestContext);
    } finally {
      if (transaction != null) {
        transaction.close();
      }
    }
  }

  private static void enforceSemanticPolicy(Transaction tx, TransactionsConfiguration config) {
    if (tx.getSemantic() == TxSemantic.ATOMIC && Objects.equals(config.getAtomic(), false)) {
      throw new BadRequestException("Atomic transactions are not enabled for this API");
    }
    if (tx.getSemantic() == TxSemantic.BATCH && Objects.equals(config.getBatch(), false)) {
      throw new BadRequestException("Batch transactions are not enabled for this API");
    }
  }

  private static Transaction enforceActionLimit(
      Transaction transaction, TransactionsConfiguration config) {
    Integer max = config.getMaxActionsPerRequest();
    if (max == null || max <= 0) {
      return transaction;
    }
    return new ActionLimitedTransaction(transaction, max);
  }

  private static final class ActionLimitedTransaction implements Transaction {

    private final Transaction delegate;
    private final int maxActions;
    private Iterator<TxAction> actions;

    private ActionLimitedTransaction(Transaction delegate, int maxActions) {
      this.delegate = delegate;
      this.maxActions = maxActions;
    }

    @Override
    public TxSemantic getSemantic() {
      return delegate.getSemantic();
    }

    @Override
    public Iterator<TxAction> actions() {
      if (actions == null) {
        actions = new ActionLimitedIterator(delegate.actions(), maxActions);
      }
      return actions;
    }

    @Override
    public void close() {
      delegate.close();
    }
  }

  private static final class ActionLimitedIterator implements Iterator<TxAction> {

    private final Iterator<TxAction> delegate;
    private final int maxActions;
    private int count;

    private ActionLimitedIterator(Iterator<TxAction> delegate, int maxActions) {
      this.delegate = delegate;
      this.maxActions = maxActions;
    }

    @Override
    public boolean hasNext() {
      if (count < maxActions) {
        return delegate.hasNext();
      }
      if (delegate.hasNext()) {
        throw new BadRequestException(
            "Transaction request contains more than "
                + maxActions
                + " action"
                + (maxActions == 1 ? "" : "s"));
      }
      return false;
    }

    @Override
    public TxAction next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      count++;
      return delegate.next();
    }
  }

  private Response buildResponse(
      ExecutionResult result,
      HeaderPrefer.Return ret,
      HeaderPrefer.Handling handling,
      ApiRequestContext requestContext) {
    boolean atomic = result.getSemantic() == TxSemantic.ATOMIC;
    boolean failed = !result.isSuccess();
    int status = (atomic && failed) ? 422 : 200;
    String preferenceApplied = preferenceApplied(ret, handling);

    if (ret == HeaderPrefer.Return.NONE && !failed && result.getWarnings().isEmpty()) {
      return Response.noContent().header("Preference-Applied", preferenceApplied).build();
    }

    ObjectNode body = renderBody(result, ret, requestContext);
    return Response.status(status)
        .type(APPLICATION_JSON)
        .header("Preference-Applied", preferenceApplied)
        .entity(toJson(body))
        .build();
  }

  private static String preferenceApplied(HeaderPrefer.Return ret, HeaderPrefer.Handling handling) {
    String value = "return=" + ret.headerValue();
    if (handling == HeaderPrefer.Handling.STRICT) {
      value += ", handling=strict";
    }
    return value;
  }

  private static ObjectNode renderBody(
      ExecutionResult result, HeaderPrefer.Return ret, ApiRequestContext requestContext) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("semantic", result.getSemantic().toString().toLowerCase(java.util.Locale.ROOT));

    ObjectNode summary = body.putObject("summary");
    summary.put("totalInserted", result.getInsertedCount());
    summary.put("totalReplaced", result.getReplacedCount());
    summary.put("totalUpdated", result.getUpdatedCount());
    summary.put("totalDeleted", result.getDeletedCount());

    ArrayNode insertResults = body.putArray("insertResults");
    ArrayNode replaceResults = body.putArray("replaceResults");
    ArrayNode updateResults = body.putArray("updateResults");
    ArrayNode deleteResults = body.putArray("deleteResults");
    ArrayNode exceptions = MAPPER.createArrayNode();

    boolean wantDetails = ret != HeaderPrefer.Return.MINIMAL;

    for (ActionResult r : result.getActionResults()) {
      addExceptionsFor(r, exceptions);
      if (r.getStatus() != ActionStatus.SUCCESS || !wantDetails) {
        continue;
      }
      ArrayNode bucket =
          bucketFor(r.getType(), insertResults, replaceResults, updateResults, deleteResults);
      for (String id : r.getFeatureIds()) {
        bucket.add(featureUri(requestContext, r.getCollectionId(), id));
      }
    }

    if (ret == HeaderPrefer.Return.MINIMAL) {
      body.remove("insertResults");
      body.remove("replaceResults");
      body.remove("updateResults");
      body.remove("deleteResults");
    }

    if (!exceptions.isEmpty()) {
      body.set("exceptions", exceptions);
    }
    if (!result.getWarnings().isEmpty()) {
      ArrayNode warnings = body.putArray("warnings");
      result.getWarnings().forEach(warnings::add);
    }
    return body;
  }

  private static void addExceptionsFor(ActionResult r, ArrayNode exceptions) {
    if (!r.getFailedFeatureErrors().isEmpty()) {
      // Per-item errors are populated by the validate-and-skip path (Prefer: handling=strict).
      // The ids list contains items that had a source-side id; the payloads list contains the
      // raw feature bytes of items without an id. Errors are parallel to the concatenation of
      // ids ++ payloads.
      int idCount = r.getFailedFeatureIds().size();
      int total = r.getFailedFeatureErrors().size();
      for (int i = 0; i < total; i++) {
        String error = r.getFailedFeatureErrors().get(i);
        if (i < idCount) {
          exceptions.add(renderItemException(r, r.getFailedFeatureIds().get(i), null, error));
        } else {
          int pIdx = i - idCount;
          String payload =
              pIdx < r.getFailedFeaturePayloads().size()
                  ? r.getFailedFeaturePayloads().get(pIdx)
                  : null;
          exceptions.add(renderItemException(r, null, payload, error));
        }
      }
      return;
    }
    if (r.getStatus() == ActionStatus.FAILED) {
      exceptions.add(renderException(r));
    }
  }

  private static ObjectNode renderItemException(
      ActionResult r, String featureId, String featurePayload, String message) {
    String action = r.getType().toString().toLowerCase(java.util.Locale.ROOT);
    ObjectNode ex = MAPPER.createObjectNode();
    ex.put("type", "about:blank");
    ex.put("title", "Action " + action + " on '" + r.getCollectionId() + "' rejected a feature");
    ex.put("status", 422);
    ex.put("detail", message);
    r.getActionId().ifPresent(id -> ex.put("actionId", id));
    ex.put("collectionId", r.getCollectionId());
    ex.put("action", action);
    if (featureId != null) {
      ArrayNode ids = ex.putArray("featureIds");
      ids.add(featureId);
    } else if (featurePayload != null) {
      ArrayNode payloads = ex.putArray("featurePayloads");
      payloads.add(featurePayload);
    }
    return ex;
  }

  private static ObjectNode renderException(ActionResult r) {
    String action = r.getType().toString().toLowerCase(java.util.Locale.ROOT);
    ObjectNode ex = MAPPER.createObjectNode();
    ex.put("type", "about:blank");
    ex.put("title", "Action " + action + " on '" + r.getCollectionId() + "' failed");
    ex.put("status", 422);
    ex.put("detail", r.getError().orElse("unknown error"));
    r.getActionId().ifPresent(id -> ex.put("actionId", id));
    ex.put("collectionId", r.getCollectionId());
    ex.put("action", action);
    if (!r.getFailedFeatureIds().isEmpty()) {
      ArrayNode ids = ex.putArray("featureIds");
      r.getFailedFeatureIds().forEach(ids::add);
    }
    if (!r.getFailedFeaturePayloads().isEmpty()) {
      ArrayNode payloads = ex.putArray("featurePayloads");
      r.getFailedFeaturePayloads().forEach(payloads::add);
    }
    return ex;
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
}
