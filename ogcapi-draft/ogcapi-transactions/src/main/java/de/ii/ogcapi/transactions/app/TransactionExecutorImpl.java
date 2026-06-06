/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.features.core.domain.DecoderContext;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.ImmutableDecoderContext;
import de.ii.ogcapi.features.core.domain.ImmutableValidatorContext;
import de.ii.ogcapi.features.core.domain.ValidatorContext;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.transactions.domain.ActionResult;
import de.ii.ogcapi.transactions.domain.ActionStatus;
import de.ii.ogcapi.transactions.domain.ExecutionResult;
import de.ii.ogcapi.transactions.domain.ImmutableActionResult;
import de.ii.ogcapi.transactions.domain.ImmutableExecutionResult;
import de.ii.ogcapi.transactions.domain.InsertItem;
import de.ii.ogcapi.transactions.domain.MutationStrategy;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionExecutor;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.transactions.domain.TxDelete;
import de.ii.ogcapi.transactions.domain.TxInsert;
import de.ii.ogcapi.transactions.domain.TxReplace;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.ogcapi.transactions.domain.TxUpdate;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureChange;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenSource;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import de.ii.xtraplatform.features.domain.FeatureTransactions.MutationResult;
import de.ii.xtraplatform.features.domain.FeatureTransactions.Session;
import de.ii.xtraplatform.features.domain.ImmutableFeatureChange;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.geometries.domain.Axes;
import de.ii.xtraplatform.streams.domain.Reactive.Source;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threeten.extra.Interval;

@Singleton
@AutoBind
public class TransactionExecutorImpl implements TransactionExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionExecutorImpl.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();
  // Per-action wfs:Insert / JSON streaming-insert batch size. The executor accumulates this many
  // decoded per-feature FeatureTokenSources and hands them to Session.createFeatures(...) in one
  // call so the SQL session can fold consecutive same-shape main INSERTs into one multi-row
  // INSERT and flatten all children into one JDBC batch. Bounded to preserve streaming behaviour
  // for very large inserts.
  private static final int INSERT_BATCH_SIZE = 100;

  private final FeaturesCoreProviders providers;
  private final ExtensionRegistry extensionRegistry;
  private final CrsInfo crsInfo;
  private final FeaturesCoreQueriesHandler queriesHandler;

  @Inject
  public TransactionExecutorImpl(
      FeaturesCoreProviders providers,
      ExtensionRegistry extensionRegistry,
      CrsInfo crsInfo,
      FeaturesCoreQueriesHandler queriesHandler) {
    this.providers = providers;
    this.extensionRegistry = extensionRegistry;
    this.crsInfo = crsInfo;
    this.queriesHandler = queriesHandler;
  }

  @Override
  public ExecutionResult execute(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext requestContext,
      EpsgCrs requestCrs,
      boolean validate,
      Optional<Instant> ogcMutationDatetime) {
    try (transaction) {
      return transaction.getSemantic() == TxSemantic.ATOMIC
          ? executeAtomic(
              transaction, api, requestContext, requestCrs, validate, ogcMutationDatetime)
          : executeBatch(
              transaction, api, requestContext, requestCrs, validate, ogcMutationDatetime);
    }
  }

  /**
   * Capture the per-scope server clock reading for mutation timestamps. Package-private so tests
   * can swap it via an anonymous subclass; otherwise just {@link Instant#now()}.
   */
  Instant nowInstant() {
    return Instant.now();
  }

  // --- atomic ---------------------------------------------------------------

  private ExecutionResult executeAtomic(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      boolean validate,
      Optional<Instant> ogcMutationDatetime) {
    // Single timestamp for the whole atomic transaction — every action shares it so versioned
    // strategies can reject same-feature chains under the no-backdating rule (plan §1.5).
    Instant atomicScopeTimestamp = nowInstant();
    Map<String, Session> sessionsByProvider = new LinkedHashMap<>();
    List<ActionResult> results = new ArrayList<>();
    // Ids touched by earlier successful actions in this atomic transaction, keyed by canonical
    // collection id. Consulted by runUpdate to reject same-transaction chaining (the GET inside
    // runUpdate runs on the provider's query connection at READ COMMITTED and cannot see the
    // Session's still-uncommitted writes).
    Map<String, Set<String>> touchedIdsByCollection = new LinkedHashMap<>();
    // MutationStrategy resolved once per (transaction, collectionId); the lookup walks every
    // registered strategy and is cheap but not free.
    Map<String, MutationStrategy> strategyByCollection = new LinkedHashMap<>();
    Throwable firstError = null;

    Iterator<TxAction> actions = transaction.actions();
    while (actions.hasNext()) {
      TxAction action = actions.next();
      if (firstError != null) {
        // a previous action already failed — atomic semantics: skip remaining actions but
        // still drain insert items so the parser stays in a consistent state
        drainQuietly(action);
        results.add(skipped(action));
        continue;
      }
      try {
        FeatureProvider provider = resolveProvider(api, action.getCollectionId());
        Session session =
            sessionsByProvider.computeIfAbsent(
                provider.getId(), id -> openSession(provider, action.getCollectionId()));
        results.add(
            runAction(
                action,
                provider,
                session,
                api,
                ctx,
                requestCrs,
                touchedIdsByCollection,
                strategyByCollection,
                atomicScopeTimestamp,
                ogcMutationDatetime,
                validate,
                false,
                transaction.isWfs()));
        ActionResult lastResult = last(results);
        if (lastResult.getStatus() == ActionStatus.FAILED) {
          firstError =
              new RuntimeException(
                  lastResult.getError().orElse("action " + actionLabel(action) + " failed"));
        } else if (lastResult.getStatus() == ActionStatus.SUCCESS) {
          touchedIdsByCollection
              .computeIfAbsent(lastResult.getCollectionId(), k -> new HashSet<>())
              .addAll(lastResult.getFeatureIds());
        }
      } catch (RuntimeException e) {
        results.add(failed(action, e));
        firstError = e;
      }
    }

    boolean commitSucceeded = false;
    if (firstError == null) {
      try {
        sessionsByProvider.values().forEach(Session::commit);
        commitSucceeded = true;
      } catch (RuntimeException e) {
        // commit failed — flip every recorded SUCCESS to FAILED with the commit error,
        // rollback whatever can still be rolled back
        LOGGER.warn("Atomic transaction commit failed", e);
        rollbackQuietly(sessionsByProvider.values());
        results = flipSuccessesToFailed(results, e);
      }
    } else {
      rollbackQuietly(sessionsByProvider.values());
    }
    closeQuietly(sessionsByProvider.values());

    if (commitSucceeded) {
      emitChanges(api, results);
    }

    return new ImmutableExecutionResult.Builder()
        .semantic(TxSemantic.ATOMIC)
        .actionResults(results)
        .build();
  }

  // --- batch ----------------------------------------------------------------

  private ExecutionResult executeBatch(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      boolean validate,
      Optional<Instant> ogcMutationDatetime) {
    List<ActionResult> results = new ArrayList<>();
    // Reused across actions in a batch even though each action commits independently — keeps the
    // MutationStrategy lookup to one walk per (transaction, collectionId).
    Map<String, MutationStrategy> strategyByCollection = new LinkedHashMap<>();

    Iterator<TxAction> actions = transaction.actions();
    while (actions.hasNext()) {
      TxAction action = actions.next();
      Session session = null;
      try {
        FeatureProvider provider = resolveProvider(api, action.getCollectionId());
        session = openSession(provider, action.getCollectionId());
        // Batch semantics commit between actions, so each action sees a fresh committed
        // snapshot — no need to track touched ids across actions.
        // Per-action timestamp under batch semantics — each action's mutation is independent.
        Instant batchScopeTimestamp = nowInstant();
        ActionResult r =
            runAction(
                action,
                provider,
                session,
                api,
                ctx,
                requestCrs,
                Map.of(),
                strategyByCollection,
                batchScopeTimestamp,
                ogcMutationDatetime,
                validate,
                true,
                transaction.isWfs());
        if (r.getStatus() == ActionStatus.SUCCESS) {
          session.commit();
        } else {
          session.rollback();
        }
        results.add(r);
      } catch (RuntimeException e) {
        if (session != null) {
          try {
            session.rollback();
          } catch (RuntimeException ignored) {
            // already failing, swallow
          }
        }
        results.add(failed(action, e));
      } finally {
        if (session != null) {
          try {
            session.close();
          } catch (RuntimeException ignored) {
            // best-effort
          }
        }
      }
    }

    emitChanges(api, results);

    return new ImmutableExecutionResult.Builder()
        .semantic(TxSemantic.BATCH)
        .actionResults(results)
        .build();
  }

  // --- action dispatch ------------------------------------------------------

  private ActionResult runAction(
      TxAction action,
      FeatureProvider provider,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      Map<String, Set<String>> touchedIdsByCollection,
      Map<String, MutationStrategy> strategyByCollection,
      Instant scopeTimestamp,
      Optional<Instant> ogcMutationDatetime,
      boolean validate,
      boolean skipInvalid,
      boolean fromWfs) {
    MutationStrategy strategy =
        strategyByCollection.computeIfAbsent(
            action.getCollectionId(), id -> pickStrategy(api.getData(), id));
    Instant mutationTimestamp =
        strategy.resolveMutationTimestamp(
            api.getData(), action, scopeTimestamp, ogcMutationDatetime);
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Action {} on collection '{}': dispatching via {} with mutationTimestamp={}",
          action.getType(),
          action.getCollectionId(),
          strategy.getClass().getSimpleName(),
          mutationTimestamp);
    }
    try {
      return switch (action.getType()) {
        case INSERT ->
            runInsert((TxInsert) action, session, api, ctx, requestCrs, validate, skipInvalid);
        case REPLACE -> runReplace((TxReplace) action, session, api, ctx, requestCrs, validate);
        case UPDATE ->
            runUpdate(
                (TxUpdate) action,
                provider,
                session,
                api,
                ctx,
                requestCrs,
                touchedIdsByCollection,
                fromWfs);
        case DELETE -> runDelete((TxDelete) action, session, api);
        default -> throw new IllegalArgumentException("Unknown action type: " + action.getType());
      };
    } catch (RuntimeException e) {
      return failed(action, e);
    }
  }

  private ActionResult runInsert(
      TxInsert action,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      boolean validate,
      boolean skipInvalid) {
    Axes axes = crsInfo.is3d(requestCrs) ? Axes.XYZ : Axes.XY;
    OgcApiDataV2 apiData = api.getData();
    String featureType = resolveFeatureType(apiData, action.getCollectionId());
    List<String> ids = new ArrayList<>();
    List<String> skippedIds = new ArrayList<>();
    List<String> skippedPayloads = new ArrayList<>();
    List<String> skippedErrors = new ArrayList<>();

    FeatureFormatExtension format = validate ? resolveFormat(action.getMediaType()) : null;
    ValidatorContext vctx =
        validate
            ? buildValidatorContext(apiData, action.getCollectionId(), action.getMediaType(), ctx)
            : null;

    List<FeatureTokenSource> batch = new ArrayList<>(INSERT_BATCH_SIZE);
    List<InsertItem> batchItems = new ArrayList<>(INSERT_BATCH_SIZE);
    ExtentAccumulator extents = new ExtentAccumulator();
    Iterator<InsertItem> items = action.items();
    while (items.hasNext()) {
      InsertItem item = items.next();
      try (InputStream payload = item.payload()) {
        byte[] bytes = validate ? payload.readAllBytes() : null;
        if (validate) {
          try {
            format.validate(new String(bytes, StandardCharsets.UTF_8), vctx);
          } catch (IllegalArgumentException ve) {
            if (!skipInvalid) {
              throw ve;
            }
            // Record the item: id when known, else the payload as a content-based locator.
            if (item.featureId().isPresent()) {
              skippedIds.add(item.featureId().get());
            } else {
              skippedPayloads.add(payloadString(item));
            }
            skippedErrors.add(
                ve.getMessage() != null ? ve.getMessage() : ve.getClass().getSimpleName());
            continue;
          }
        }
        InputStream decodeStream = validate ? new ByteArrayInputStream(bytes) : payload;
        FeatureTokenSource source =
            decodeFeature(
                action.getMediaType(),
                decodeStream,
                apiData,
                action.getCollectionId(),
                requestCrs,
                axes);
        batch.add(source);
        batchItems.add(item);
      } catch (java.io.IOException e) {
        return failedInsert(action, apiData, batchItems, e);
      }
      if (batch.size() >= INSERT_BATCH_SIZE) {
        ActionResult failed =
            flushInsertBatch(
                action, apiData, session, featureType, batch, batchItems, requestCrs, ids, extents);
        if (failed != null) return failed;
      }
    }
    if (!batch.isEmpty()) {
      ActionResult failed =
          flushInsertBatch(
              action, apiData, session, featureType, batch, batchItems, requestCrs, ids, extents);
      if (failed != null) return failed;
    }

    // When validate-and-skip is on and every item was invalid, nothing was written — report FAILED
    // so the batch executor rolls back the (empty) session and the response reflects the failure.
    if (ids.isEmpty() && (!skippedIds.isEmpty() || !skippedPayloads.isEmpty())) {
      return new ImmutableActionResult.Builder()
          .type(TxActionType.INSERT)
          .collectionId(canonicalCollectionId(apiData, action.getCollectionId()))
          .actionId(action.getActionId())
          .status(ActionStatus.FAILED)
          .error("All items failed schema validation under Prefer: handling=strict")
          .failedFeatureIds(skippedIds)
          .failedFeaturePayloads(skippedPayloads)
          .failedFeatureErrors(skippedErrors)
          .build();
    }

    return new ImmutableActionResult.Builder()
        .type(TxActionType.INSERT)
        .collectionId(canonicalCollectionId(apiData, action.getCollectionId()))
        .actionId(action.getActionId())
        .status(ActionStatus.SUCCESS)
        .featureIds(ids)
        .failedFeatureIds(skippedIds)
        .failedFeaturePayloads(skippedPayloads)
        .failedFeatureErrors(skippedErrors)
        .newBoundingBox(extents.bbox())
        .newInterval(extents.intervalMillis())
        .build();
  }

  private static String payloadString(InsertItem item) {
    byte[] bytes = item.payloadBytes();
    if (bytes == null) {
      return "";
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /**
   * Commit one batch. On failure, build a FAILED ActionResult carrying the candidate feature ids
   * (and 1-based positions) of every item that was in the failing batch — the broken one is among
   * them but cannot be pinpointed further from a batch-level error.
   */
  private ActionResult flushInsertBatch(
      TxInsert action,
      OgcApiDataV2 apiData,
      Session session,
      String featureType,
      List<FeatureTokenSource> batch,
      List<InsertItem> batchItems,
      EpsgCrs crs,
      List<String> ids,
      ExtentAccumulator extents) {
    try {
      MutationResult mr = session.createFeatures(featureType, batch, crs);
      rejectIfError(mr);
      ids.addAll(mr.getIds());
      extents.merge(mr.getSpatialExtent(), mr.getTemporalExtent());
      batch.clear();
      batchItems.clear();
      return null;
    } catch (RuntimeException e) {
      return failedInsert(action, apiData, batchItems, e);
    }
  }

  private static ActionResult failedInsert(
      TxInsert action, OgcApiDataV2 apiData, List<InsertItem> batchItems, Throwable error) {
    List<String> candidateIds = new ArrayList<>(batchItems.size());
    List<String> candidatePayloads = new ArrayList<>(batchItems.size());
    for (InsertItem it : batchItems) {
      if (it.featureId().isPresent()) {
        candidateIds.add(it.featureId().get());
      } else {
        candidatePayloads.add(payloadString(it));
      }
    }
    return failed(action, error, candidateIds, candidatePayloads);
  }

  private ActionResult runReplace(
      TxReplace action,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      boolean validate) {
    Axes axes = crsInfo.is3d(requestCrs) ? Axes.XYZ : Axes.XY;
    OgcApiDataV2 apiData = api.getData();
    String featureType = resolveFeatureType(apiData, action.getCollectionId());

    List<String> targetIds =
        resolveTargetIds(
            action.getTargetIds(),
            action.getFilter(),
            apiData,
            canonicalCollectionId(apiData, action.getCollectionId()),
            "Replace action");
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Replace action filter matched no feature ids for collection '"
              + action.getCollectionId()
              + "'.");
    }
    if (targetIds.size() > 1) {
      throw new IllegalArgumentException(
          "Replace action must target exactly one feature id; got "
              + targetIds.size()
              + " for collection '"
              + action.getCollectionId()
              + "'.");
    }
    String id = targetIds.get(0);

    MutationResult mr;
    try {
      if (validate) {
        FeatureFormatExtension format = resolveFormat(action.getMediaType());
        ValidatorContext vctx =
            buildValidatorContext(apiData, action.getCollectionId(), action.getMediaType(), ctx);
        format.validate(new String(action.getFeature(), StandardCharsets.UTF_8), vctx);
      }

      FeatureTokenSource source =
          decodeFeature(
              action.getMediaType(),
              new ByteArrayInputStream(action.getFeature()),
              apiData,
              action.getCollectionId(),
              requestCrs,
              axes);
      mr = session.updateFeature(featureType, id, source, requestCrs, false);
      rejectIfError(mr);
    } catch (RuntimeException e) {
      return failed(action, e, List.of(id));
    }

    return new ImmutableActionResult.Builder()
        .type(TxActionType.REPLACE)
        .collectionId(canonicalCollectionId(apiData, action.getCollectionId()))
        .actionId(action.getActionId())
        .status(ActionStatus.SUCCESS)
        .featureIds(mr.getIds().isEmpty() ? List.of(id) : mr.getIds())
        .newBoundingBox(mr.getSpatialExtent())
        .newInterval(toIntervalMillis(mr.getTemporalExtent()))
        .build();
  }

  private ActionResult runUpdate(
      TxUpdate action,
      FeatureProvider provider,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      Map<String, Set<String>> touchedIdsByCollection,
      boolean fromWfs) {
    EpsgCrs crs = requestCrs;
    OgcApiDataV2 apiData = api.getData();
    String featureType = resolveFeatureType(apiData, action.getCollectionId());
    String canonicalCollectionId = canonicalCollectionId(apiData, action.getCollectionId());

    List<String> targetIds =
        resolveTargetIds(
            action.getTargetIds(),
            action.getFilter(),
            apiData,
            canonicalCollectionId,
            "Update action");
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Update action filter matched no feature ids for collection '"
              + action.getCollectionId()
              + "'.");
    }

    // Phase C: every Update is a native SQL UPDATE issued on the session's own connection, so
    // prior writes in the same atomic transaction are visible to it. The v1 touched-id reject
    // and the GET-merge-write fall-back have been removed; chaining Insert/Replace/Update with
    // Update on the same id now works natively.

    List<FeatureTransactions.PropertyUpdate> updates;
    try {
      updates = buildPropertyUpdates(action, apiData, canonicalCollectionId, fromWfs, crs);
    } catch (RuntimeException e) {
      // Action-level failure (bad payload, non-whitelisted property, unknown path, etc.) —
      // attribute it to every target id so the log line and the result both name the
      // features the client tried to update.
      return failed(action, e, targetIds);
    }

    List<String> updatedIds = new ArrayList<>();
    for (String id : targetIds) {
      try {
        MutationResult mr = session.patchFeature(featureType, id, updates, crs);
        rejectIfError(mr);
        if (mr.getIds().isEmpty()) {
          updatedIds.add(id);
        } else {
          updatedIds.addAll(mr.getIds());
        }
      } catch (RuntimeException e) {
        return failed(action, e, List.of(id));
      }
    }

    return new ImmutableActionResult.Builder()
        .type(TxActionType.UPDATE)
        .collectionId(canonicalCollectionId)
        .actionId(action.getActionId())
        .status(ActionStatus.SUCCESS)
        .featureIds(updatedIds)
        .build();
  }

  // Resolve every input path (alias or schema id, per the input format's useAlias) against the
  // collection's FeatureSchema and turn it into a PropertyUpdate carrying the canonical
  // schema-id path. Validates each canonical path against the TRANSACTIONS building block's
  // updatableProperties whitelist; anything not declared updatable is rejected with a 4xx.
  private List<FeatureTransactions.PropertyUpdate> buildPropertyUpdates(
      TxUpdate action,
      OgcApiDataV2 apiData,
      String canonicalCollectionId,
      boolean fromWfs,
      EpsgCrs crs) {
    FeatureSchema rootSchema = resolveFeatureSchema(apiData, canonicalCollectionId);
    boolean inputUseAlias =
        fromWfs
            ? gmlUseAlias(apiData, canonicalCollectionId)
            : geoJsonUseAlias(apiData, canonicalCollectionId);
    Set<List<String>> whitelist =
        new java.util.HashSet<>(updatablePaths(apiData, canonicalCollectionId));

    // Group NameValue entries by canonical path: multiple <wfs:Property> with the same
    // ValueReference (the standard WFS-T form for multi-valued properties) collapse into one
    // PropertyUpdate whose value is the JSON array of the individual values. Single-value
    // properties get a scalar value; explicit deletes carry empty Optional<JsonNode>.
    // `fromWfs` toggles the XPath object-type-step convention on the input path.
    boolean inputHasObjectTypeSteps = fromWfs;
    java.util.LinkedHashMap<List<String>, List<JsonNode>> byPath = new java.util.LinkedHashMap<>();
    for (TxUpdate.NameValue nv : action.getAdd()) {
      addValueByPath(
          byPath,
          rootSchema,
          nv.getPath(),
          inputUseAlias,
          inputHasObjectTypeSteps,
          whitelist,
          nameValueAsJson(nv, rootSchema, inputUseAlias, inputHasObjectTypeSteps, crs),
          canonicalCollectionId);
    }
    for (TxUpdate.NameValue nv : action.getModify()) {
      addValueByPath(
          byPath,
          rootSchema,
          nv.getPath(),
          inputUseAlias,
          inputHasObjectTypeSteps,
          whitelist,
          nameValueAsJson(nv, rootSchema, inputUseAlias, inputHasObjectTypeSteps, crs),
          canonicalCollectionId);
    }
    java.util.Set<List<String>> clearedPaths = new java.util.LinkedHashSet<>();
    for (List<String> path : action.getDeleteProperties()) {
      List<String> canonical =
          resolveAndCheck(
              rootSchema,
              path,
              inputUseAlias,
              inputHasObjectTypeSteps,
              whitelist,
              canonicalCollectionId);
      clearedPaths.add(canonical);
      byPath.remove(canonical);
    }

    List<FeatureTransactions.PropertyUpdate> result =
        new ArrayList<>(byPath.size() + clearedPaths.size());
    for (Map.Entry<List<String>, List<JsonNode>> entry : byPath.entrySet()) {
      result.add(buildPropertyUpdate(entry.getKey(), entry.getValue()));
    }
    for (List<String> path : clearedPaths) {
      result.add(
          de.ii.xtraplatform.features.domain.ImmutablePropertyUpdate.builder()
              .path(path)
              .value(Optional.empty())
              .build());
    }
    return result;
  }

  private static void addValueByPath(
      java.util.LinkedHashMap<List<String>, List<JsonNode>> byPath,
      FeatureSchema root,
      List<String> inputPath,
      boolean inputUseAlias,
      boolean inputHasObjectTypeSteps,
      Set<List<String>> whitelist,
      JsonNode value,
      String canonicalCollectionId) {
    List<String> canonical =
        resolveAndCheck(
            root,
            inputPath,
            inputUseAlias,
            inputHasObjectTypeSteps,
            whitelist,
            canonicalCollectionId);
    byPath.computeIfAbsent(canonical, k -> new ArrayList<>()).add(value);
  }

  // Resolve the NameValue's payload to a JsonNode. For text/json values the JsonNode is used
  // as-is. For WFS XML subtrees (set via getValueXml()), the resolved target schema property
  // drives format-specific conversion: GML→GeoJSON for GEOMETRY; XML walk for OBJECT_ARRAY.
  private static JsonNode nameValueAsJson(
      TxUpdate.NameValue nv,
      FeatureSchema rootSchema,
      boolean inputUseAlias,
      boolean inputHasObjectTypeSteps,
      EpsgCrs crs) {
    if (nv.getValueXml().isEmpty()) {
      return nv.getValue();
    }
    List<FeatureSchema> resolved =
        UpdatePathResolver.resolve(
            rootSchema, nv.getPath(), inputUseAlias, inputHasObjectTypeSteps);
    if (resolved.isEmpty()) {
      throw new IllegalArgumentException(
          "Could not resolve property path for <wfs:Value> XML content.");
    }
    FeatureSchema targetSchema = resolved.get(resolved.size() - 1);
    return WfsValueXmlConverter.convert(nv.getValueXml().get(), targetSchema, inputUseAlias, crs);
  }

  private static FeatureTransactions.PropertyUpdate buildPropertyUpdate(
      List<String> canonicalPath, List<JsonNode> values) {
    JsonNode value;
    if (values.size() == 1) {
      value = values.get(0);
    } else {
      com.fasterxml.jackson.databind.node.ArrayNode array = MAPPER.createArrayNode();
      for (JsonNode v : values) {
        array.add(v);
      }
      value = array;
    }
    return de.ii.xtraplatform.features.domain.ImmutablePropertyUpdate.builder()
        .path(canonicalPath)
        .value(Optional.of(value))
        .build();
  }

  private static List<String> resolveAndCheck(
      FeatureSchema root,
      List<String> inputPath,
      boolean inputUseAlias,
      boolean inputHasObjectTypeSteps,
      Set<List<String>> whitelist,
      String canonicalCollectionId) {
    List<FeatureSchema> resolved =
        UpdatePathResolver.resolve(root, inputPath, inputUseAlias, inputHasObjectTypeSteps);
    List<String> canonicalPath = UpdatePathResolver.toOutputPath(resolved, false);
    if (!whitelist.contains(canonicalPath)) {
      throw new IllegalArgumentException(
          "Property '"
              + String.join(".", canonicalPath)
              + "' is not declared updatable on collection '"
              + canonicalCollectionId
              + "'. Configure the TRANSACTIONS building block's `updatableProperties` to opt in.");
    }
    return canonicalPath;
  }

  private static List<List<String>> updatablePaths(
      OgcApiDataV2 apiData, String canonicalCollectionId) {
    de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return collectionCfg
        .getExtension(de.ii.ogcapi.transactions.domain.TransactionsConfiguration.class)
        .map(de.ii.ogcapi.transactions.domain.TransactionsConfiguration::getUpdatablePropertyPaths)
        .orElse(List.of());
  }

  private FeatureSchema resolveFeatureSchema(OgcApiDataV2 apiData, String canonicalCollectionId) {
    de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return providers
        .getFeatureSchema(apiData, collectionCfg)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No feature schema for collection '" + canonicalCollectionId + "'"));
  }

  private static boolean gmlUseAlias(OgcApiDataV2 apiData, String canonicalCollectionId) {
    de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return collectionCfg
        .getExtension(de.ii.ogcapi.features.gml.domain.GmlConfiguration.class)
        .map(de.ii.ogcapi.foundation.domain.AliasConfiguration::isUseAlias)
        .orElse(false);
  }

  private static boolean geoJsonUseAlias(OgcApiDataV2 apiData, String canonicalCollectionId) {
    de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return collectionCfg
        .getExtension(de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration.class)
        .map(de.ii.ogcapi.foundation.domain.AliasConfiguration::isUseAlias)
        .orElse(false);
  }

  private ActionResult runDelete(TxDelete action, Session session, OgcApi api) {
    OgcApiDataV2 apiData = api.getData();
    String canonicalCollectionId = canonicalCollectionId(apiData, action.getCollectionId());
    List<String> targetIds =
        resolveTargetIds(
            action.getTargetIds(),
            action.getFilter(),
            apiData,
            canonicalCollectionId,
            "Delete action");
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Delete action filter matched no feature ids for collection '"
              + action.getCollectionId()
              + "'.");
    }

    String featureType = resolveFeatureType(api.getData(), action.getCollectionId());
    List<String> deleted = new ArrayList<>();
    for (String id : targetIds) {
      try {
        MutationResult mr = session.deleteFeature(featureType, id);
        rejectIfError(mr);
        // SqlMutationSession.deleteFeature only populates getIds() when the SQL DELETE actually
        // matched a row. Treat an empty result as a no-op so totalDeleted / deleteResults reflect
        // only features that were really removed, not every rid the caller named in the filter.
        if (!mr.getIds().isEmpty()) {
          deleted.add(id);
        }
      } catch (RuntimeException e) {
        return failed(action, e, List.of(id));
      }
    }

    return new ImmutableActionResult.Builder()
        .type(TxActionType.DELETE)
        .collectionId(canonicalCollectionId(api.getData(), action.getCollectionId()))
        .actionId(action.getActionId())
        .status(ActionStatus.SUCCESS)
        .featureIds(deleted)
        .build();
  }

  // --- change emission ------------------------------------------------------

  /**
   * Aggregate successful action results by (collection, mapped {@link FeatureChange.Action}) and
   * dispatch one {@link FeatureChange} per group to the affected provider. Mirrors the post-write
   * hook in {@code CommandHandlerCrudImpl} so collection metadata (item count, spatial extent,
   * temporal extent, lastModified) stays in sync after a transaction. Mapping: INSERT→CREATE,
   * REPLACE/UPDATE→UPDATE, DELETE→DELETE. Aggregating across actions yields the "effective" delta
   * per collection — both atomic transactions and batch transactions with multiple successful
   * same-collection actions emit a single event per (collection, action). Failures are swallowed:
   * change emission is best-effort and must never break the response.
   *
   * <p>Package-private to enable direct exercise from {@code TransactionExecutorChangesSpec}.
   */
  void emitChanges(OgcApi api, List<ActionResult> results) {
    try {
      Map<ChangeKey, ChangeAggregate> aggregates = new LinkedHashMap<>();
      for (ActionResult r : results) {
        if (r.getStatus() != ActionStatus.SUCCESS) continue;
        if (r.getFeatureIds().isEmpty()) continue;
        FeatureChange.Action mapped = mapAction(r.getType());
        ChangeKey key = new ChangeKey(r.getCollectionId(), mapped);
        aggregates
            .computeIfAbsent(key, k -> new ChangeAggregate())
            .add(r.getFeatureIds(), r.getNewBoundingBox(), r.getNewInterval());
      }
      for (Map.Entry<ChangeKey, ChangeAggregate> e : aggregates.entrySet()) {
        ChangeKey key = e.getKey();
        ChangeAggregate agg = e.getValue();
        de.ii.xtraplatform.features.domain.FeatureChanges sink;
        try {
          sink = resolveChanges(api, key.collectionId);
        } catch (RuntimeException re) {
          LOGGER.warn(
              "Could not resolve feature provider for collection '{}' while emitting change: {}",
              key.collectionId,
              re.getMessage());
          continue;
        }
        FeatureChange change =
            ImmutableFeatureChange.builder()
                .action(key.action)
                .featureType(key.collectionId)
                .featureIds(List.copyOf(agg.ids))
                .newBoundingBox(agg.bbox)
                .newInterval(agg.interval)
                .build();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("Feature Change: {}", change);
        }
        try {
          sink.handle(change);
        } catch (RuntimeException re) {
          LOGGER.warn(
              "Dispatching feature change for collection '{}' failed: {}",
              key.collectionId,
              re.getMessage());
        }
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Emitting transaction feature changes failed", e);
    }
  }

  private static FeatureChange.Action mapAction(TxActionType type) {
    return switch (type) {
      case INSERT -> FeatureChange.Action.CREATE;
      case REPLACE, UPDATE -> FeatureChange.Action.UPDATE;
      case DELETE -> FeatureChange.Action.DELETE;
    };
  }

  private static Optional<Interval> toIntervalMillis(Optional<Tuple<Long, Long>> extent) {
    if (extent.isEmpty()) return Optional.empty();
    Long begin = extent.get().first();
    Long end = extent.get().second();
    Instant beginInstant = Objects.nonNull(begin) ? Instant.ofEpochMilli(begin) : Instant.MIN;
    Instant endInstant = Objects.nonNull(end) ? Instant.ofEpochMilli(end) : Instant.MAX;
    return Optional.of(Interval.of(beginInstant, endInstant));
  }

  // Mutable accumulator that folds successive MutationResult extents from a streaming insert
  // batch or per-id update loop into a single union bbox / interval. Kept package-private only
  // to allow direct field access from runInsert / runUpdate above.
  private static final class ExtentAccumulator {
    private BoundingBox bbox;
    private Long beginMillis;
    private Long endMillis;

    void merge(Optional<BoundingBox> nextBbox, Optional<Tuple<Long, Long>> nextInterval) {
      nextBbox.ifPresent(b -> bbox = (bbox == null) ? b : BoundingBox.merge(bbox, b));
      nextInterval.ifPresent(
          iv -> {
            Long b = iv.first();
            Long e = iv.second();
            if (b != null) beginMillis = (beginMillis == null) ? b : Math.min(beginMillis, b);
            if (e != null) endMillis = (endMillis == null) ? e : Math.max(endMillis, e);
          });
    }

    Optional<BoundingBox> bbox() {
      return Optional.ofNullable(bbox);
    }

    Optional<Interval> intervalMillis() {
      if (beginMillis == null && endMillis == null) return Optional.empty();
      Instant begin = beginMillis != null ? Instant.ofEpochMilli(beginMillis) : Instant.MIN;
      Instant end = endMillis != null ? Instant.ofEpochMilli(endMillis) : Instant.MAX;
      return Optional.of(Interval.of(begin, end));
    }
  }

  private static final class ChangeKey {
    final String collectionId;
    final FeatureChange.Action action;

    ChangeKey(String collectionId, FeatureChange.Action action) {
      this.collectionId = collectionId;
      this.action = action;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ChangeKey)) return false;
      ChangeKey other = (ChangeKey) o;
      return action == other.action && collectionId.equals(other.collectionId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(collectionId, action);
    }
  }

  private static final class ChangeAggregate {
    final LinkedHashSet<String> ids = new LinkedHashSet<>();
    Optional<BoundingBox> bbox = Optional.empty();
    Optional<Interval> interval = Optional.empty();

    void add(
        List<String> nextIds, Optional<BoundingBox> nextBbox, Optional<Interval> nextInterval) {
      ids.addAll(nextIds);
      if (nextBbox.isPresent()) {
        bbox =
            bbox.isEmpty() ? nextBbox : Optional.of(BoundingBox.merge(bbox.get(), nextBbox.get()));
      }
      if (nextInterval.isPresent()) {
        interval =
            interval.isEmpty()
                ? nextInterval
                : Optional.of(interval.get().span(nextInterval.get()));
      }
    }
  }

  // --- helpers --------------------------------------------------------------

  // Pick the highest-priority registered MutationStrategy that is enabled for the given
  // collection. The plain strategy binds to FoundationConfiguration and therefore always applies
  // (priority 0), so the lookup never fails. Phase 1.0 only uses this for the dispatch log line
  // above; phases 1.1-1.6 will route per-action behaviour (timestamp resolution,
  // retire-and-insert, retire-only, etc.) through the resolved strategy.
  private MutationStrategy pickStrategy(OgcApiDataV2 apiData, String collectionId) {
    return extensionRegistry.getExtensionsForType(MutationStrategy.class).stream()
        .filter(s -> s.isEnabledForApi(apiData, collectionId))
        .max(java.util.Comparator.comparingInt(MutationStrategy::priority))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No MutationStrategy registered (the default plain strategy should always"
                        + " apply)."));
  }

  private FeatureProvider resolveProvider(OgcApi api, String collectionId) {
    OgcApiDataV2 apiData = api.getData();
    return providers
        .getFeatureProvider(apiData, resolveCollection(apiData, collectionId))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No feature provider available for collection '" + collectionId + "'"));
  }

  // Package-private (rather than private) only so that {@code TransactionExecutorChangesSpec}
  // can swap the resolution out via an anonymous subclass — mocking the full FeatureProvider
  // graph drags in cache / unit-of-measure classes that aren't on the test classpath.
  de.ii.xtraplatform.features.domain.FeatureChanges resolveChanges(
      OgcApi api, String collectionId) {
    return resolveProvider(api, collectionId).changes();
  }

  private static String canonicalCollectionId(OgcApiDataV2 apiData, String collectionId) {
    try {
      return resolveCollection(apiData, collectionId).getId();
    } catch (IllegalArgumentException e) {
      return collectionId;
    }
  }

  /**
   * Resolves a raw collection id (typically the XML element local name from a wfs:Transaction
   * payload) to the canonical ldproxy collection configuration. Tries exact match first, then
   * case-insensitive — ldproxy collection ids are often lowercase by convention while GML feature
   * element names use UpperCamelCase.
   */
  private static de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi resolveCollection(
      OgcApiDataV2 apiData, String collectionId) {
    Map<String, de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi> all =
        apiData.getCollections();
    de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi cfg = all.get(collectionId);
    if (cfg != null) return cfg;
    for (Map.Entry<String, de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi> e :
        all.entrySet()) {
      if (e.getKey().equalsIgnoreCase(collectionId)) return e.getValue();
    }
    throw new IllegalArgumentException(
        "Unknown collection id '"
            + collectionId
            + "' (no exact or case-insensitive match in this API).");
  }

  private static Session openSession(FeatureProvider provider, String collectionId) {
    if (!provider.mutations().isAvailable()) {
      throw new IllegalStateException(
          "Feature provider for collection '"
              + collectionId
              + "' does not expose mutations capability");
    }
    return provider.mutations().get().openSession();
  }

  private FeatureFormatExtension resolveFormat(MediaType contentType) {
    return extensionRegistry.getExtensionsForType(FeatureFormatExtension.class).stream()
        .filter(FeatureFormatExtension::canSupportTransactions)
        .filter(f -> f.getMediaType().type().isCompatible(contentType))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No transaction-capable feature format for media type " + contentType));
  }

  private ValidatorContext buildValidatorContext(
      OgcApiDataV2 apiData,
      String collectionId,
      MediaType mediaType,
      ApiRequestContext requestContext) {
    String canonical = canonicalCollectionId(apiData, collectionId);
    return new ImmutableValidatorContext.Builder()
        .apiData(apiData)
        .collectionId(canonical)
        .mediaType(mediaType)
        .type(ValidatorContext.Type.RECEIVABLES)
        .requestContext(requestContext)
        .declaredProfiles(List.of())
        .defaultProfiles(resolveDefaultProfilesSchema(apiData, canonical))
        .build();
  }

  private List<Profile> resolveDefaultProfilesSchema(OgcApiDataV2 apiData, String collectionId) {
    Map<String, String> defaults =
        apiData
            .getExtension(SchemaConfiguration.class, collectionId)
            .map(SchemaConfiguration::getDefaultProfiles)
            .orElse(Map.of());
    return extensionRegistry.getExtensionsForType(Profile.class).stream()
        .filter(
            p ->
                defaults.containsKey(p.getProfileSet())
                    && p.getId().equals(defaults.get(p.getProfileSet())))
        .toList();
  }

  private FeatureTokenSource decodeFeature(
      MediaType contentType,
      InputStream body,
      OgcApiDataV2 apiData,
      String collectionId,
      EpsgCrs crs,
      Axes axes) {
    FeatureFormatExtension format = resolveFormat(contentType);
    de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, collectionId);
    FeatureSchema featureSchema =
        providers
            .getFeatureSchema(apiData, collectionCfg)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No feature schema for collection '" + collectionId + "'"));
    DecoderContext dctx =
        new ImmutableDecoderContext.Builder()
            .apiData(apiData)
            .collectionId(collectionCfg.getId())
            .featureSchema(featureSchema)
            .crs(crs)
            .axes(axes)
            .mediaType(contentType)
            .build();
    return Source.inputStream(body).via(format.getFeatureDecoder(dctx).get());
  }

  private static String resolveFeatureType(OgcApiDataV2 apiData, String collectionId) {
    try {
      de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi cfg =
          resolveCollection(apiData, collectionId);
      return cfg.getExtension(de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.class)
          .flatMap(de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration::getFeatureType)
          .orElse(cfg.getId());
    } catch (IllegalArgumentException e) {
      return collectionId;
    }
  }

  // Resolve the list of target feature ids for an action. WFS-T parsers populate `targetIds`
  // directly from `fes:ResourceId/@rid`, so no filter parsing is needed there. JSON-tx supplies
  // a CQL2 filter; we accept `IN(<id-property>, [...])` or `=(<id-property>, ...)`, where the
  // property name is the feature type's id-role property (its schema name, or its alias if it
  // declares one). Other filter shapes are rejected with a 4xx error.
  private List<String> resolveTargetIds(
      List<String> directIds,
      Optional<de.ii.xtraplatform.cql.domain.Cql2Expression> filter,
      OgcApiDataV2 apiData,
      String canonicalCollectionId,
      String actionLabel) {
    if (!directIds.isEmpty()) {
      return directIds;
    }
    if (filter.isEmpty()) {
      throw new IllegalArgumentException(
          actionLabel
              + " for collection '"
              + canonicalCollectionId
              + "' requires a filter that selects features by id.");
    }
    Set<String> idNames = idPropertyNames(apiData, canonicalCollectionId);
    return extractIdsFromFilter(filter.get(), idNames);
  }

  private static List<String> extractIdsFromFilter(
      de.ii.xtraplatform.cql.domain.Cql2Expression expression, Set<String> idPropertyNames) {
    if (expression instanceof de.ii.xtraplatform.cql.domain.In) {
      de.ii.xtraplatform.cql.domain.In in = (de.ii.xtraplatform.cql.domain.In) expression;
      ensureIdProperty(in.getArgs().isEmpty() ? null : in.getArgs().get(0), idPropertyNames);
      if (in.getArgs().size() < 2
          || !(in.getArgs().get(1) instanceof de.ii.xtraplatform.cql.domain.ArrayLiteral)) {
        throw new IllegalArgumentException(
            "Malformed IN expression: expected ArrayLiteral second argument.");
      }
      Object raw = ((de.ii.xtraplatform.cql.domain.ArrayLiteral) in.getArgs().get(1)).getValue();
      if (!(raw instanceof List)) {
        throw new IllegalArgumentException(
            "Malformed IN expression: ArrayLiteral value is not a list (was "
                + (raw == null ? "null" : raw.getClass().getSimpleName())
                + ").");
      }
      ImmutableList.Builder<String> out = ImmutableList.builder();
      for (Object element : (List<?>) raw) {
        if (element instanceof de.ii.xtraplatform.cql.domain.ScalarLiteral) {
          Object v = ((de.ii.xtraplatform.cql.domain.ScalarLiteral) element).getValue();
          out.add(String.valueOf(v));
        } else {
          throw new IllegalArgumentException(
              "Only scalar-literal id values are supported in IN expressions; got: "
                  + (element == null ? "null" : element.getClass().getSimpleName()));
        }
      }
      return out.build();
    }
    if (expression instanceof de.ii.xtraplatform.cql.domain.Eq) {
      de.ii.xtraplatform.cql.domain.Eq eq = (de.ii.xtraplatform.cql.domain.Eq) expression;
      ensureIdProperty(eq.getArgs().isEmpty() ? null : eq.getArgs().get(0), idPropertyNames);
      if (eq.getArgs().size() < 2
          || !(eq.getArgs().get(1) instanceof de.ii.xtraplatform.cql.domain.ScalarLiteral)) {
        throw new IllegalArgumentException(
            "Malformed Eq expression: expected ScalarLiteral second argument.");
      }
      Object v = ((de.ii.xtraplatform.cql.domain.ScalarLiteral) eq.getArgs().get(1)).getValue();
      return ImmutableList.of(String.valueOf(v));
    }
    throw new IllegalArgumentException(
        "Only id-based filters (IN or =) on the feature's id property are currently supported by"
            + " the transaction executor; got: "
            + expression.getClass().getSimpleName());
  }

  private static void ensureIdProperty(Object firstArg, Set<String> idPropertyNames) {
    if (!(firstArg instanceof de.ii.xtraplatform.cql.domain.Property)) {
      throw new IllegalArgumentException(
          "Transaction filter must reference the feature's id property as its first argument.");
    }
    String name = ((de.ii.xtraplatform.cql.domain.Property) firstArg).getName();
    if (!idPropertyNames.contains(name)) {
      throw new IllegalArgumentException(
          "Transaction filter must reference the feature's id property (expected one of "
              + idPropertyNames
              + "); got: '"
              + name
              + "'.");
    }
  }

  // Names by which the feature type's id-role property may be addressed in a CQL2 filter: the
  // schema property name plus any declared alias. Resolved per-collection from the FeatureSchema.
  private Set<String> idPropertyNames(OgcApiDataV2 apiData, String canonicalCollectionId) {
    FeatureSchema rootSchema = resolveFeatureSchema(apiData, canonicalCollectionId);
    Optional<FeatureSchema> idProp = rootSchema.getIdProperty();
    if (idProp.isEmpty()) {
      throw new IllegalArgumentException(
          "Collection '"
              + canonicalCollectionId
              + "' has no id-role property; transaction filters cannot resolve it to an id.");
    }
    java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
    names.add(idProp.get().getName());
    idProp.get().getAlias().ifPresent(names::add);
    return names;
  }

  private static void rejectIfError(MutationResult result) {
    result
        .getError()
        .ifPresent(
            err -> {
              if (err instanceof RuntimeException) {
                throw (RuntimeException) err;
              }
              throw new RuntimeException(err.getMessage(), err);
            });
  }

  private static ActionResult skipped(TxAction action) {
    return new ImmutableActionResult.Builder()
        .type(action.getType())
        .collectionId(action.getCollectionId())
        .actionId(action.getActionId())
        .status(ActionStatus.SKIPPED)
        .build();
  }

  private static ActionResult failed(TxAction action, Throwable error) {
    return failed(action, error, List.of(), List.of());
  }

  private static ActionResult failed(
      TxAction action, Throwable error, List<String> failedFeatureIds) {
    return failed(action, error, failedFeatureIds, List.of());
  }

  // Build a FAILED ActionResult and log the failure. User-input errors
  // (IllegalArgumentException — bad payload, unknown property, malformed filter, etc.) are
  // logged at WARN level with the message only; system errors keep the stack trace so
  // bugs and infrastructure issues remain debuggable. Failing items are identified by id
  // when known; items without an id contribute their raw payload (the bytes the client sent)
  // as a content-based locator so the client can correlate the failure with the original
  // request. In the log line we truncate the payload to keep entries readable; the full
  // payload is preserved in the ActionResult.
  private static ActionResult failed(
      TxAction action,
      Throwable error,
      List<String> failedFeatureIds,
      List<String> failedFeaturePayloads) {
    String context = failureContext(failedFeatureIds, failedFeaturePayloads);
    if (isUserError(error)) {
      LOGGER.warn(
          "Transaction action {}{} failed: {}", actionLabel(action), context, errorMessage(error));
    } else {
      LOGGER.warn("Transaction action {}{} failed", actionLabel(action), context, error);
    }
    return new ImmutableActionResult.Builder()
        .type(action.getType())
        .collectionId(action.getCollectionId())
        .actionId(action.getActionId())
        .status(ActionStatus.FAILED)
        .error(errorMessage(error))
        .failedFeatureIds(failedFeatureIds)
        .failedFeaturePayloads(failedFeaturePayloads)
        .build();
  }

  private static String errorMessage(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  // Maximum payload length to keep in the log message; the full payload still lands in
  // ActionResult.failedFeaturePayloads.
  private static final int LOG_PAYLOAD_PREVIEW = 200;

  private static String failureContext(List<String> ids, List<String> payloads) {
    StringBuilder sb = new StringBuilder();
    if (ids != null && !ids.isEmpty()) {
      sb.append(" for id");
      if (ids.size() > 1) sb.append("s");
      sb.append(" ").append(ids);
    }
    if (payloads != null && !payloads.isEmpty()) {
      sb.append(" for unidentified payload");
      if (payloads.size() > 1) sb.append("s");
      sb.append(" [");
      for (int i = 0; i < payloads.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(truncatePayload(payloads.get(i)));
      }
      sb.append("]");
    }
    return sb.toString();
  }

  private static String truncatePayload(String payload) {
    if (payload == null) return "";
    String normalised = payload.replaceAll("\\s+", " ").trim();
    if (normalised.length() <= LOG_PAYLOAD_PREVIEW) {
      return normalised;
    }
    return normalised.substring(0, LOG_PAYLOAD_PREVIEW) + "…";
  }

  // Errors that originate from user input (bad payload, unknown property, malformed filter,
  // unsupported feature kind, etc.) should appear in the log as a one-line WARN without the
  // stack trace; the message itself is the actionable diagnostic. System / infrastructure
  // errors keep the stack trace so genuine bugs stay debuggable.
  private static boolean isUserError(Throwable error) {
    return error instanceof IllegalArgumentException;
  }

  private static ActionResult last(List<ActionResult> results) {
    return results.get(results.size() - 1);
  }

  private static String actionLabel(TxAction action) {
    return action.getActionId().orElse(action.getType() + "@" + action.getCollectionId());
  }

  private static List<ActionResult> flipSuccessesToFailed(
      List<ActionResult> results, Throwable cause) {
    List<ActionResult> out = new ArrayList<>(results.size());
    for (ActionResult r : results) {
      if (r.getStatus() == ActionStatus.SUCCESS) {
        out.add(
            new ImmutableActionResult.Builder()
                .from(r)
                .status(ActionStatus.FAILED)
                .error(
                    "atomic commit failed: "
                        + (cause.getMessage() == null ? cause.toString() : cause.getMessage()))
                .featureIds(List.of())
                .build());
      } else {
        out.add(r);
      }
    }
    return out;
  }

  private static void rollbackQuietly(Iterable<Session> sessions) {
    for (Session s : sessions) {
      try {
        s.rollback();
      } catch (RuntimeException e) {
        LOGGER.warn("Rollback failed: {}", e.getMessage());
      }
    }
  }

  private static void closeQuietly(Iterable<Session> sessions) {
    for (Session s : sessions) {
      try {
        s.close();
      } catch (Exception e) {
        LOGGER.warn("Session close failed: {}", e.getMessage());
      }
    }
  }

  private static void drainQuietly(TxAction action) {
    if (action instanceof TxInsert) {
      Iterator<InsertItem> it = ((TxInsert) action).items();
      while (it.hasNext()) {
        try (InputStream in = it.next().payload()) {
          in.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (java.io.IOException ignored) {
          // best-effort: parser will surface real errors on the next pull
        }
      }
    }
  }
}
