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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.schema.domain.SchemaConfiguration;
import de.ii.ogcapi.features.core.domain.DecoderContext;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.ImmutableDecoderContext;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeature;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.features.core.domain.ImmutableValidatorContext;
import de.ii.ogcapi.features.core.domain.ValidatorContext;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.transactions.domain.ActionResult;
import de.ii.ogcapi.transactions.domain.ActionStatus;
import de.ii.ogcapi.transactions.domain.ExecutionResult;
import de.ii.ogcapi.transactions.domain.ImmutableActionResult;
import de.ii.ogcapi.transactions.domain.ImmutableExecutionResult;
import de.ii.ogcapi.transactions.domain.InsertItem;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionExecutor;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.transactions.domain.TxDelete;
import de.ii.ogcapi.transactions.domain.TxInsert;
import de.ii.ogcapi.transactions.domain.TxReplace;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.ogcapi.transactions.domain.TxUpdate;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.Scalar;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
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
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.features.json.domain.FeatureTokenDecoderGeoJson;
import de.ii.xtraplatform.geometries.domain.Axes;
import de.ii.xtraplatform.streams.domain.Reactive.Source;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
  private static final MediaType GEOJSON_MEDIA_TYPE = new MediaType("application", "geo+json");
  // Per-id GET is fine for typical wfs:Update sizes (1–few ids). Above this many target ids,
  // runUpdate prefetches all current features in a single FEATURES query with an IN-filter to
  // avoid N round-trips.
  private static final int BULK_GET_THRESHOLD = 16;
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
      boolean validate) {
    try (transaction) {
      return transaction.getSemantic() == TxSemantic.ATOMIC
          ? executeAtomic(transaction, api, requestContext, requestCrs, validate)
          : executeBatch(transaction, api, requestContext, requestCrs, validate);
    }
  }

  // --- atomic ---------------------------------------------------------------

  private ExecutionResult executeAtomic(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      boolean validate) {
    Map<String, Session> sessionsByProvider = new LinkedHashMap<>();
    List<ActionResult> results = new ArrayList<>();
    // Ids touched by earlier successful actions in this atomic transaction, keyed by canonical
    // collection id. Consulted by runUpdate to reject same-transaction chaining (the GET inside
    // runUpdate runs on the provider's query connection at READ COMMITTED and cannot see the
    // Session's still-uncommitted writes).
    Map<String, Set<String>> touchedIdsByCollection = new LinkedHashMap<>();
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
                validate,
                false));
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
      boolean validate) {
    List<ActionResult> results = new ArrayList<>();

    Iterator<TxAction> actions = transaction.actions();
    while (actions.hasNext()) {
      TxAction action = actions.next();
      Session session = null;
      try {
        FeatureProvider provider = resolveProvider(api, action.getCollectionId());
        session = openSession(provider, action.getCollectionId());
        // Batch semantics commit between actions, so each action sees a fresh committed
        // snapshot — no need to track touched ids across actions.
        ActionResult r =
            runAction(action, provider, session, api, ctx, requestCrs, Map.of(), validate, true);
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
      boolean validate,
      boolean skipInvalid) {
    try {
      return switch (action.getType()) {
        case INSERT ->
            runInsert((TxInsert) action, session, api, ctx, requestCrs, validate, skipInvalid);
        case REPLACE -> runReplace((TxReplace) action, session, api, ctx, requestCrs, validate);
        case UPDATE ->
            runUpdate(
                (TxUpdate) action, provider, session, api, ctx, requestCrs, touchedIdsByCollection);
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
    List<Integer> skippedIndexes = new ArrayList<>();
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
            item.featureId().ifPresent(skippedIds::add);
            skippedIndexes.add(item.indexInInsert());
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
    if (ids.isEmpty() && !skippedIndexes.isEmpty()) {
      return new ImmutableActionResult.Builder()
          .type(TxActionType.INSERT)
          .collectionId(canonicalCollectionId(apiData, action.getCollectionId()))
          .actionId(action.getActionId())
          .status(ActionStatus.FAILED)
          .error("All items failed schema validation under Prefer: handling=strict")
          .failedFeatureIds(skippedIds)
          .failedFeatureIndexes(skippedIndexes)
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
        .failedFeatureIndexes(skippedIndexes)
        .failedFeatureErrors(skippedErrors)
        .newBoundingBox(extents.bbox())
        .newInterval(extents.intervalMillis())
        .build();
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
    List<Integer> candidateIndexes = new ArrayList<>(batchItems.size());
    for (InsertItem it : batchItems) {
      it.featureId().ifPresent(candidateIds::add);
      candidateIndexes.add(it.indexInInsert());
    }
    String message =
        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    return new ImmutableActionResult.Builder()
        .type(TxActionType.INSERT)
        .collectionId(canonicalCollectionId(apiData, action.getCollectionId()))
        .actionId(action.getActionId())
        .status(ActionStatus.FAILED)
        .error(message)
        .failedFeatureIds(candidateIds)
        .failedFeatureIndexes(candidateIndexes)
        .build();
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
        action
            .getFilter()
            .map(TransactionExecutorImpl::extractIdsFromFilter)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Replace action for collection '"
                            + action.getCollectionId()
                            + "' requires a filter that selects features by id."));
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
    MutationResult mr = session.updateFeature(featureType, id, source, requestCrs, false);
    rejectIfError(mr);

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
      Map<String, Set<String>> touchedIdsByCollection) {
    EpsgCrs crs = requestCrs;
    Axes axes = crsInfo.is3d(crs) ? Axes.XYZ : Axes.XY;
    OgcApiDataV2 apiData = api.getData();
    String featureType = resolveFeatureType(apiData, action.getCollectionId());
    String canonicalCollectionId = canonicalCollectionId(apiData, action.getCollectionId());

    List<String> targetIds =
        action
            .getFilter()
            .map(TransactionExecutorImpl::extractIdsFromFilter)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Update action for collection '"
                            + action.getCollectionId()
                            + "' requires a filter that selects features by id."));
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Update action filter matched no feature ids for collection '"
              + action.getCollectionId()
              + "'.");
    }

    // v1 limitation: the GET that backs runUpdate cannot see uncommitted writes from
    // earlier actions in the same atomic wfs:Transaction (it goes through the provider's
    // query connection at READ COMMITTED, not the Session's mutation connection). Reject
    // the action up front rather than silently 404 / silently merge against a stale snapshot.
    Set<String> alreadyTouched =
        touchedIdsByCollection.getOrDefault(canonicalCollectionId, Set.of());
    if (!alreadyTouched.isEmpty()) {
      Set<String> conflict = new LinkedHashSet<>();
      for (String id : targetIds) {
        if (alreadyTouched.contains(id)) {
          conflict.add(id);
        }
      }
      if (!conflict.isEmpty()) {
        throw new IllegalArgumentException(
            "Update action cannot target feature id(s) "
                + conflict
                + " in collection '"
                + canonicalCollectionId
                + "' because the same id(s) were inserted, replaced, updated, or deleted earlier"
                + " in this atomic transaction. Same-transaction chaining is not supported in v1;"
                + " split the operation into separate transactions or use batch semantics.");
      }
    }

    ObjectNode patchTemplate = buildPatchTemplate(action);

    // Per-id GET is fine for typical wfs:Update sizes. Above BULK_GET_THRESHOLD ids, prefetch
    // every current feature with one FEATURES query using an IN filter and serve the
    // per-target lookup from an in-memory map. Same-transaction chaining is already rejected
    // up front (see touchedIdsByCollection check above).
    Map<String, byte[]> bulkCurrent = null;
    if (targetIds.size() > BULK_GET_THRESHOLD) {
      bulkCurrent =
          fetchCurrentFeaturesAsGeoJsonBulk(
              canonicalCollectionId, featureType, targetIds, provider, crs, ctx);
    }

    List<String> updatedIds = new ArrayList<>();
    ExtentAccumulator extents = new ExtentAccumulator();
    for (String id : targetIds) {
      // Apply the JSON-merge-patch and write the FULL merged document through the partial-
      // update path. The provider's partial-update is delete-then-reinsert from the body, so
      // sending only the patch would null every unchanged property — same pattern as CRUD's
      // patchItemResponse.
      byte[] currentBytes;
      if (bulkCurrent != null) {
        currentBytes = bulkCurrent.get(id);
        if (currentBytes == null) {
          throw new IllegalArgumentException("The requested feature does not exist: '" + id + "'.");
        }
      } else {
        currentBytes =
            fetchCurrentFeatureAsGeoJson(
                canonicalCollectionId, featureType, id, provider, crs, ctx);
      }

      byte[] mergedBytes = applyMergePatch(currentBytes, patchTemplate, id);

      FeatureTokenSource source =
          mergePatchSource(new ByteArrayInputStream(mergedBytes), crs, axes);
      MutationResult mr = session.updateFeature(featureType, id, source, crs, true);
      rejectIfError(mr);
      if (mr.getIds().isEmpty()) {
        updatedIds.add(id);
      } else {
        updatedIds.addAll(mr.getIds());
      }
      extents.merge(mr.getSpatialExtent(), mr.getTemporalExtent());
    }

    return new ImmutableActionResult.Builder()
        .type(TxActionType.UPDATE)
        .collectionId(canonicalCollectionId)
        .actionId(action.getActionId())
        .status(ActionStatus.SUCCESS)
        .featureIds(updatedIds)
        .newBoundingBox(extents.bbox())
        .newInterval(extents.intervalMillis())
        .build();
  }

  // Build the per-action merge-patch object: properties to set/modify are written verbatim,
  // and properties to clear are written as the PATCH_NULL_VALUE sentinel string. The id is
  // injected per-target by applyMergePatch.
  private static ObjectNode buildPatchTemplate(TxUpdate action) {
    ObjectNode feature = MAPPER.createObjectNode();
    feature.put("type", "Feature");
    ObjectNode properties = MAPPER.createObjectNode();
    for (TxUpdate.NameValue nv : action.getAdd()) {
      properties.set(nv.getName(), nv.getValue());
    }
    for (TxUpdate.NameValue nv : action.getModify()) {
      properties.set(nv.getName(), nv.getValue());
    }
    for (String name : action.getDeleteProperties()) {
      properties.put(name, FeatureTransactions.PATCH_NULL_VALUE);
    }
    feature.set("properties", properties);
    return feature;
  }

  private static byte[] applyMergePatch(byte[] currentBytes, ObjectNode patchTemplate, String id) {
    JsonNode currentNode;
    try {
      currentNode = MAPPER.readTree(currentBytes);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not parse current feature as JSON for update: " + e.getMessage(), e);
    }
    ObjectNode patch = patchTemplate.deepCopy();
    patch.put("id", id);
    JsonNode merged;
    try {
      merged = applyJsonMergePatch(currentNode, patch);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Could not apply merge patch to feature '" + id + "': " + e.getMessage(), e);
    }
    try {
      return MAPPER.writeValueAsBytes(merged);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not serialise merged feature '" + id + "': " + e.getMessage(), e);
    }
  }

  // RFC 7396 JSON Merge Patch: for each entry in patch, if value is null remove the key,
  // if it's an object recurse, otherwise replace. Target stays as-is for keys not in patch.
  // We do NOT treat PATCH_NULL_VALUE specially here — it stays as a string in the merged
  // document and is interpreted by the GeoJSON decoder downstream (which honours the option
  // we pass on the FeatureTokenDecoderGeoJson). An explicit JSON null is rare in our patch
  // bodies (we use the sentinel instead) but if it appears RFC 7396 says delete the key.
  private static JsonNode applyJsonMergePatch(JsonNode target, JsonNode patch) {
    if (!patch.isObject()) {
      return patch.deepCopy();
    }
    ObjectNode out = target.isObject() ? (ObjectNode) target.deepCopy() : MAPPER.createObjectNode();
    Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      String key = entry.getKey();
      JsonNode value = entry.getValue();
      if (value.isNull()) {
        out.remove(key);
      } else if (value.isObject()) {
        out.set(
            key,
            applyJsonMergePatch(out.has(key) ? out.get(key) : MAPPER.createObjectNode(), value));
      } else {
        out.set(key, value.deepCopy());
      }
    }
    return out;
  }

  private static FeatureTokenSource mergePatchSource(InputStream body, EpsgCrs crs, Axes axes) {
    return Source.inputStream(body)
        .via(
            new FeatureTokenDecoderGeoJson(
                Optional.of(FeatureTransactions.PATCH_NULL_VALUE), crs, axes));
  }

  private byte[] fetchCurrentFeatureAsGeoJson(
      String canonicalCollectionId,
      String featureType,
      String featureId,
      FeatureProvider provider,
      EpsgCrs crs,
      ApiRequestContext ctx) {
    de.ii.xtraplatform.features.domain.FeatureQuery query =
        ImmutableFeatureQuery.builder()
            .type(featureType)
            .filter(In.of(ScalarLiteral.of(featureId)))
            .returnsSingleFeature(true)
            .crs(crs)
            .build();
    // Build a minimum-viable QueryInputFeature; the queries handler fills the rest from
    // request context and provider capabilities.
    FeaturesCoreQueriesHandler.QueryInputFeature queryInput =
        new ImmutableQueryInputFeature.Builder()
            .collectionId(canonicalCollectionId)
            .featureId(featureId)
            .featureProvider(provider)
            .query(query)
            .defaultCrs(crs)
            .includeBodyLinks(false)
            .build();
    ApiRequestContext geoJsonContext = buildGeoJsonContext(ctx, featureId);
    Response response =
        queriesHandler.handle(FeaturesCoreQueriesHandler.Query.FEATURE, queryInput, geoJsonContext);
    Object entity = response.getEntity();
    if (entity instanceof byte[]) {
      return (byte[]) entity;
    }
    throw new IllegalStateException(
        "Expected byte[] entity from FEATURE query for id '"
            + featureId
            + "', got "
            + (entity == null ? "null" : entity.getClass().getName()));
  }

  // Bulk variant of fetchCurrentFeatureAsGeoJson: one FEATURES query with an IN filter over
  // every target id. Returns a map of feature-id → serialised GeoJSON Feature bytes (suitable
  // for applyMergePatch). Ids absent from the response simply won't appear in the map; the
  // caller throws when looking up a missing id, matching the per-id GET's 404 behaviour.
  private Map<String, byte[]> fetchCurrentFeaturesAsGeoJsonBulk(
      String canonicalCollectionId,
      String featureType,
      List<String> featureIds,
      FeatureProvider provider,
      EpsgCrs crs,
      ApiRequestContext ctx) {
    List<Scalar> literals = new ArrayList<>(featureIds.size());
    for (String id : featureIds) {
      literals.add(ScalarLiteral.of(id));
    }
    de.ii.xtraplatform.features.domain.FeatureQuery query =
        ImmutableFeatureQuery.builder()
            .type(featureType)
            .filter(In.of(literals))
            .crs(crs)
            .limit(featureIds.size())
            .build();
    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(canonicalCollectionId)
            .featureProvider(provider)
            .query(query)
            .defaultCrs(crs)
            .includeBodyLinks(false)
            .sendResponseAsStream(false)
            .build();
    ApiRequestContext geoJsonContext = buildGeoJsonContext(ctx, "(bulk fetch)");
    Response response =
        queriesHandler.handle(
            FeaturesCoreQueriesHandler.Query.FEATURES, queryInput, geoJsonContext);
    Object entity = response.getEntity();
    if (!(entity instanceof byte[])) {
      throw new IllegalStateException(
          "Expected byte[] entity from FEATURES bulk query, got "
              + (entity == null ? "null" : entity.getClass().getName()));
    }
    byte[] bytes = (byte[]) entity;
    JsonNode root;
    try {
      root = MAPPER.readTree(bytes);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not parse FeatureCollection response for bulk update: " + e.getMessage(), e);
    }
    JsonNode features = root.get("features");
    if (features == null || !features.isArray()) {
      throw new IllegalStateException(
          "FeatureCollection response missing 'features' array for bulk update.");
    }
    Map<String, byte[]> out = new LinkedHashMap<>();
    for (JsonNode feature : features) {
      JsonNode idNode = feature.get("id");
      if (idNode == null) continue;
      String id = idNode.asText();
      try {
        out.put(id, MAPPER.writeValueAsBytes(feature));
      } catch (IOException e) {
        throw new IllegalStateException(
            "Could not serialise feature '" + id + "' from bulk fetch: " + e.getMessage(), e);
      }
    }
    return out;
  }

  private static ApiRequestContext buildGeoJsonContext(ApiRequestContext ctx, String contextLabel) {
    try {
      return new ImmutableStaticRequestContext.Builder()
          .from(ctx)
          .requestUri(ctx.getUriCustomizer().clearParameters().build())
          .mediaType(
              new ImmutableApiMediaType.Builder()
                  .type(GEOJSON_MEDIA_TYPE)
                  .label("GeoJSON")
                  .parameter("json")
                  .build())
          .alternateMediaTypes(Set.of())
          .build();
    } catch (java.net.URISyntaxException e) {
      throw new IllegalStateException(
          "Could not build GeoJSON request context for " + contextLabel + ": " + e.getMessage(), e);
    }
  }

  private ActionResult runDelete(TxDelete action, Session session, OgcApi api) {
    List<String> targetIds =
        action
            .getFilter()
            .map(TransactionExecutorImpl::extractIdsFromFilter)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Delete action for collection '"
                            + action.getCollectionId()
                            + "' requires a filter that selects features by id."));
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Delete action filter matched no feature ids for collection '"
              + action.getCollectionId()
              + "'.");
    }

    String featureType = resolveFeatureType(api.getData(), action.getCollectionId());
    List<String> deleted = new ArrayList<>();
    for (String id : targetIds) {
      MutationResult mr = session.deleteFeature(featureType, id);
      rejectIfError(mr);
      // SqlMutationSession.deleteFeature only populates getIds() when the SQL DELETE actually
      // matched a row. Treat an empty result as a no-op so totalDeleted / deleteResults reflect
      // only features that were really removed, not every rid the caller named in the filter.
      if (!mr.getIds().isEmpty()) {
        deleted.add(id);
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
   * payload, e.g. {@code AX_Flurstueck}) to the canonical ldproxy collection configuration. Tries
   * exact match first, then case-insensitive — ldproxy collection ids are often lowercase by
   * convention while GML feature element names use UpperCamelCase.
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

  /**
   * v1 filter resolution: accepts only an {@link de.ii.xtraplatform.cql.domain.In} expression on
   * the {@code _ID_} placeholder whose arguments are scalar literals. General filter→id resolution
   * via {@link de.ii.xtraplatform.features.domain.FeatureQueries#getFeatureStream} is a follow-up.
   */
  private static List<String> extractIdsFromFilter(
      de.ii.xtraplatform.cql.domain.Cql2Expression expression) {
    if (!(expression instanceof de.ii.xtraplatform.cql.domain.In)) {
      throw new IllegalArgumentException(
          "Only id-based filters (IN on _ID_ placeholder) are currently supported by the "
              + "transaction executor; got: "
              + expression.getClass().getSimpleName());
    }
    de.ii.xtraplatform.cql.domain.In in = (de.ii.xtraplatform.cql.domain.In) expression;
    if (!in.isIdFilter()) {
      throw new IllegalArgumentException(
          "Only IN on the _ID_ placeholder is currently supported by the transaction executor.");
    }
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
    LOGGER.warn("Transaction action {} failed", actionLabel(action), error);
    return new ImmutableActionResult.Builder()
        .type(action.getType())
        .collectionId(action.getCollectionId())
        .actionId(action.getActionId())
        .status(ActionStatus.FAILED)
        .error(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())
        .build();
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
