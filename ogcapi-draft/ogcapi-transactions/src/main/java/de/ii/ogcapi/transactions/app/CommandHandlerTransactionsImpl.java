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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Singleton
@AutoBind
public class CommandHandlerTransactionsImpl implements CommandHandlerTransactions {

  private static final MediaType APPLICATION_JSON = MediaType.APPLICATION_JSON_TYPE;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private final TransactionExecutor executor;

  @Inject
  public CommandHandlerTransactionsImpl(TransactionExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Response processTransaction(
      QueryInputTransaction queryInput, ApiRequestContext requestContext) {
    boolean validate = queryInput.getHandling() == HeaderPreferTransaction.PreferHandling.STRICT;
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
                validate);
        transaction = null;
      } catch (IllegalArgumentException e) {
        throw new BadRequestException("Could not parse transaction body: " + e.getMessage());
      }

      return buildResponse(result, queryInput.getReturnPreference(), requestContext);
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
      HeaderPreferTransaction.PreferReturn ret,
      ApiRequestContext requestContext) {
    boolean atomic = result.getSemantic() == TxSemantic.ATOMIC;
    boolean failed = !result.isSuccess();
    int status = (atomic && failed) ? 422 : 200;

    if (ret == HeaderPreferTransaction.PreferReturn.NONE && !failed) {
      return Response.noContent().header("Preference-Applied", "return=none").build();
    }

    ObjectNode body = renderBody(result, ret, requestContext);
    return Response.status(status)
        .type(APPLICATION_JSON)
        .header("Preference-Applied", "return=" + ret.headerValue())
        .entity(toJson(body))
        .build();
  }

  private static ObjectNode renderBody(
      ExecutionResult result,
      HeaderPreferTransaction.PreferReturn ret,
      ApiRequestContext requestContext) {
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

    boolean wantDetails = ret != HeaderPreferTransaction.PreferReturn.MINIMAL;

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

    if (ret == HeaderPreferTransaction.PreferReturn.MINIMAL) {
      body.remove("insertResults");
      body.remove("replaceResults");
      body.remove("updateResults");
      body.remove("deleteResults");
    }

    if (!exceptions.isEmpty()) {
      body.set("exceptions", exceptions);
    }
    return body;
  }

  private static void addExceptionsFor(ActionResult r, ArrayNode exceptions) {
    if (!r.getFailedFeatureErrors().isEmpty()) {
      int size = r.getFailedFeatureErrors().size();
      for (int i = 0; i < size; i++) {
        Optional<String> fid =
            i < r.getFailedFeatureIds().size()
                ? Optional.of(r.getFailedFeatureIds().get(i))
                : Optional.empty();
        Optional<Integer> idx =
            i < r.getFailedFeatureIndexes().size()
                ? Optional.of(r.getFailedFeatureIndexes().get(i))
                : Optional.empty();
        exceptions.add(renderItemException(r, fid, idx, r.getFailedFeatureErrors().get(i)));
      }
      return;
    }
    if (r.getStatus() == ActionStatus.FAILED) {
      exceptions.add(renderException(r));
    }
  }

  private static ObjectNode renderItemException(
      ActionResult r, Optional<String> featureId, Optional<Integer> featureIndex, String message) {
    String action = r.getType().toString().toLowerCase(java.util.Locale.ROOT);
    ObjectNode ex = MAPPER.createObjectNode();
    ex.put("type", "about:blank");
    ex.put("title", "Action " + action + " on '" + r.getCollectionId() + "' rejected a feature");
    ex.put("status", 422);
    ex.put("detail", message);
    r.getActionId().ifPresent(id -> ex.put("actionId", id));
    ex.put("collectionId", r.getCollectionId());
    ex.put("action", action);
    featureId.ifPresent(
        id -> {
          ArrayNode ids = ex.putArray("featureIds");
          ids.add(id);
        });
    featureIndex.ifPresent(
        idx -> {
          ArrayNode indexes = ex.putArray("featureIndexes");
          indexes.add(idx);
        });
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
    if (!r.getFailedFeatureIndexes().isEmpty()) {
      ArrayNode idx = ex.putArray("featureIndexes");
      r.getFailedFeatureIndexes().forEach(idx::add);
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
