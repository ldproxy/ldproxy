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
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.ImmutableDecoderContext;
import de.ii.ogcapi.features.core.domain.ImmutableValidatorContext;
import de.ii.ogcapi.features.core.domain.ValidatorContext;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration;
import de.ii.ogcapi.foundation.domain.AliasConfiguration;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.transactions.domain.ActionResult;
import de.ii.ogcapi.transactions.domain.ActionStatus;
import de.ii.ogcapi.transactions.domain.CompositeId;
import de.ii.ogcapi.transactions.domain.ExecutionResult;
import de.ii.ogcapi.transactions.domain.ImmutableActionResult;
import de.ii.ogcapi.transactions.domain.ImmutableExecutionResult;
import de.ii.ogcapi.transactions.domain.InsertItem;
import de.ii.ogcapi.transactions.domain.MutationStrategy;
import de.ii.ogcapi.transactions.domain.Transaction;
import de.ii.ogcapi.transactions.domain.TransactionExecutor;
import de.ii.ogcapi.transactions.domain.TransactionHooks;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import de.ii.ogcapi.transactions.domain.TxAction;
import de.ii.ogcapi.transactions.domain.TxActionType;
import de.ii.ogcapi.transactions.domain.TxDelete;
import de.ii.ogcapi.transactions.domain.TxInsert;
import de.ii.ogcapi.transactions.domain.TxReplace;
import de.ii.ogcapi.transactions.domain.TxSemantic;
import de.ii.ogcapi.transactions.domain.TxUpdate;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.cql.domain.ArrayLiteral;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Eq;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureChange;
import de.ii.xtraplatform.features.domain.FeatureChanges;
import de.ii.xtraplatform.features.domain.FeatureMutationHookException;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenSource;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import de.ii.xtraplatform.features.domain.FeatureTransactions.MutationResult;
import de.ii.xtraplatform.features.domain.FeatureTransactions.Session;
import de.ii.xtraplatform.features.domain.ImmutableFeatureChange;
import de.ii.xtraplatform.features.domain.ImmutablePropertyUpdate;
import de.ii.xtraplatform.features.domain.SchemaBase;
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
import java.util.Collection;
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
public class TransactionExecutorImpl extends AbstractVolatileComposed
    implements TransactionExecutor {

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
      FeaturesCoreQueriesHandler queriesHandler,
      VolatileRegistry volatileRegistry) {
    super(TransactionExecutor.class.getSimpleName(), volatileRegistry, true);
    this.providers = providers;
    this.extensionRegistry = extensionRegistry;
    this.crsInfo = crsInfo;
    this.queriesHandler = queriesHandler;

    onVolatileStart();

    addSubcomponent(queriesHandler);

    onVolatileStarted();
  }

  @Override
  public ExecutionResult execute(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext requestContext,
      EpsgCrs requestCrs,
      HeaderPrefer.Handling handling,
      Optional<Instant> ogcMutationDatetime) {
    try (transaction) {
      return transaction.getSemantic() == TxSemantic.ATOMIC
          ? executeAtomic(
              transaction, api, requestContext, requestCrs, handling, ogcMutationDatetime)
          : executeBatch(
              transaction, api, requestContext, requestCrs, handling, ogcMutationDatetime);
    }
  }

  /**
   * The configured transaction-lifecycle hook statements (API-level) for this request's handling.
   *
   * @param preCommit {@code true} for the pre-commit hook, {@code false} for the setup hook
   */
  private static List<String> hookStatements(
      TransactionsConfiguration cfg, HeaderPrefer.Handling handling, boolean preCommit) {
    if (cfg == null) {
      return List.of();
    }
    TransactionHooks hooks = preCommit ? cfg.getPreCommit() : cfg.getTransactionSetup();
    return hooks == null ? List.of() : hooks.effective(handling);
  }

  // Package-private (rather than private) so specs can swap the API-configuration and
  // provider/session resolution out via an anonymous subclass — mocking the full OgcApi /
  // FeatureProvider graph drags in classes that aren't on the test classpath (see
  // resolveChanges).
  TransactionsConfiguration transactionsConfig(OgcApi api) {
    return api.getData().getExtension(TransactionsConfiguration.class).orElse(null);
  }

  String resolveProviderId(OgcApi api, String collectionId) {
    return resolveProvider(api, collectionId).getId();
  }

  String canonicalCollectionId(OgcApi api, String collectionId) {
    return canonicalCollectionId(api.getData(), collectionId);
  }

  Session openSessionFor(OgcApi api, String collectionId) {
    return openSession(resolveProvider(api, collectionId), collectionId);
  }

  /**
   * When a hook statement fails, the warnings emitted by hook statements that ran before it are
   * carried on the exception — recover them so they are still reported on the failure path.
   */
  private static void recoverHookWarnings(RuntimeException e, List<String> warnings) {
    if (e instanceof FeatureMutationHookException hookEx) {
      warnings.addAll(hookEx.getWarnings());
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
      HeaderPrefer.Handling handling,
      Optional<Instant> ogcMutationDatetime) {
    // handling=strict turns on per-payload schema validation before any write.
    boolean validate = handling == HeaderPrefer.Handling.STRICT;
    TransactionsConfiguration cfg = transactionsConfig(api);
    List<String> setup = hookStatements(cfg, handling, false);
    List<String> preCommit = hookStatements(cfg, handling, true);
    // collectErrors: keep executing after a failed action so the response reports every error.
    // Each action then runs inside a savepoint — after a failed SQL statement the shared
    // transaction is only usable again once the action's writes are rolled back to it.
    boolean collectErrors = cfg != null && Boolean.TRUE.equals(cfg.getCollectErrors());
    List<String> warnings = new ArrayList<>();
    // Single timestamp for the whole atomic transaction — every action shares it so versioned
    // strategies can reject same-feature chains under the no-backdating rule.
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
    // A fatal error leaves a session unusable (failed open, setup hook, or savepoint handling)
    // and always stops execution; a failed action only stops it when errors are not collected.
    Throwable fatalError = null;
    boolean anyFailed = false;

    Iterator<TxAction> actions = transaction.actions();
    while (actions.hasNext()) {
      TxAction action = actions.next();
      if (fatalError != null || (anyFailed && !collectErrors)) {
        // execution has stopped — skip remaining actions but still drain insert items so the
        // parser stays in a consistent state
        drainQuietly(action);
        results.add(skipped(canonicalCollectionId(api, action.getCollectionId()), action));
        continue;
      }
      ActionResult result = null;
      try {
        String providerId = resolveProviderId(api, action.getCollectionId());
        Session session = sessionsByProvider.get(providerId);
        if (session == null) {
          session = openSessionFor(api, action.getCollectionId());
          // Store before running setup so a setup failure still leaves the session in the map
          // for the rollback/close paths below.
          sessionsByProvider.put(providerId, session);
          warnings.addAll(session.execute(setup));
          if (collectErrors && !session.supportsSavepoints()) {
            LOGGER.warn(
                "collectErrors is enabled but the feature provider session does not support"
                    + " savepoints — stopping at the first error for this transaction");
            collectErrors = false;
          }
        }
        boolean savepointActive = false;
        if (collectErrors) {
          session.savepoint();
          savepointActive = true;
        }
        result =
            runAction(
                action,
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
                transaction.isWfs());
        result = withActionWarnings(result, session);
        if (result.getStatus() == ActionStatus.FAILED) {
          anyFailed = true;
          if (savepointActive) {
            // undo just this action's writes; the transaction stays usable for the rest
            session.rollbackToSavepoint();
          }
        } else {
          if (savepointActive) {
            session.releaseSavepoint();
          }
          if (result.getStatus() == ActionStatus.SUCCESS) {
            touchedIdsByCollection
                .computeIfAbsent(result.getCollectionId(), k -> new HashSet<>())
                .addAll(result.getFeatureIds());
          }
        }
      } catch (RuntimeException e) {
        // session acquisition, a setup hook, or savepoint handling failed — the transaction is
        // no longer usable
        recoverHookWarnings(e, warnings);
        if (result == null) {
          result = failed(canonicalCollectionId(api, action.getCollectionId()), action, e);
        }
        anyFailed = true;
        fatalError = e;
      }
      results.add(result);
    }

    boolean commitSucceeded = false;
    Optional<String> transactionError = Optional.empty();
    if (!anyFailed) {
      try {
        // Pre-commit hooks run on the still-open transaction; a raised error aborts the
        // whole transaction, a warning lets it commit and is surfaced in the response.
        for (Session s : sessionsByProvider.values()) {
          warnings.addAll(s.execute(preCommit));
        }
        sessionsByProvider.values().forEach(Session::commit);
        commitSucceeded = true;
      } catch (RuntimeException e) {
        // pre-commit or commit failed — a transaction-level error not attributable to a single
        // action; rollback whatever can still be rolled back
        recoverHookWarnings(e, warnings);
        if (e instanceof FeatureMutationHookException) {
          // expected, configuration-driven rollback (a hook check fired) — reported to the
          // client, so keep the log quiet and without a stack trace
          LOGGER.debug("Pre-commit hook aborted the atomic transaction: {}", e.getMessage());
        } else {
          LOGGER.warn("Atomic transaction commit failed", e);
        }
        rollbackQuietly(sessionsByProvider.values());
        transactionError = Optional.of("atomic commit failed: " + errorMessage(e));
        results = flipSuccessesToRolledBack(results);
      }
    } else {
      rollbackQuietly(sessionsByProvider.values());
      results = flipSuccessesToRolledBack(results);
    }
    closeQuietly(sessionsByProvider.values());

    if (commitSucceeded) {
      emitChanges(api, results);
    }

    return new ImmutableExecutionResult.Builder()
        .semantic(TxSemantic.ATOMIC)
        .actionResults(results)
        .warnings(warnings)
        .transactionError(transactionError)
        .build();
  }

  // --- batch ----------------------------------------------------------------

  private ExecutionResult executeBatch(
      Transaction transaction,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      HeaderPrefer.Handling handling,
      Optional<Instant> ogcMutationDatetime) {
    // handling=strict turns on per-payload schema validation before any write.
    boolean validate = handling == HeaderPrefer.Handling.STRICT;
    TransactionsConfiguration cfg = transactionsConfig(api);
    List<String> setup = hookStatements(cfg, handling, false);
    List<String> preCommit = hookStatements(cfg, handling, true);
    List<String> warnings = new ArrayList<>();
    List<ActionResult> results = new ArrayList<>();
    // Reused across actions in a batch even though each action commits independently — keeps the
    // MutationStrategy lookup to one walk per (transaction, collectionId).
    Map<String, MutationStrategy> strategyByCollection = new LinkedHashMap<>();

    Iterator<TxAction> actions = transaction.actions();
    while (actions.hasNext()) {
      TxAction action = actions.next();
      Session session = null;
      try {
        session = openSessionFor(api, action.getCollectionId());
        warnings.addAll(session.execute(setup));
        // Batch semantics commit between actions, so each action sees a fresh committed
        // snapshot — no need to track touched ids across actions.
        // Per-action timestamp under batch semantics — each action's mutation is independent.
        Instant batchScopeTimestamp = nowInstant();
        ActionResult r =
            runAction(
                action,
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
        r = withActionWarnings(r, session);
        if (r.getStatus() == ActionStatus.SUCCESS) {
          // Pre-commit hooks run per action under batch semantics; a raised error fails just
          // this action (caught below), a warning lets it commit and is surfaced.
          warnings.addAll(session.execute(preCommit));
          session.commit();
        } else {
          session.rollback();
        }
        results.add(r);
      } catch (RuntimeException e) {
        recoverHookWarnings(e, warnings);
        if (e instanceof FeatureMutationHookException) {
          LOGGER.debug("Pre-commit hook failed the batch action: {}", e.getMessage());
        }
        if (session != null) {
          try {
            session.rollback();
          } catch (RuntimeException ignored) {
            // already failing, swallow
          }
        }
        results.add(failed(canonicalCollectionId(api, action.getCollectionId()), action, e));
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
        .warnings(warnings)
        .build();
  }

  // --- action dispatch ------------------------------------------------------

  // Package-private so specs can script per-action outcomes via an anonymous subclass without
  // stubbing the OgcApi / FeatureProvider graph (see transactionsConfig above).
  ActionResult runAction(
      TxAction action,
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
    // Pass the canonical collection id (e.g. lowercase `ap_pto`) into the strategy lookup —
    // raw action ids straight off the wire are often the mixed-case GML element name (`AP_PTO`)
    // and would cause `ApiExtension.isEnabledForApi` to miss the collection entirely.
    MutationStrategy strategy =
        strategyByCollection.computeIfAbsent(
            action.getCollectionId(),
            id -> pickStrategy(api.getData(), canonicalCollectionId(api.getData(), id)));
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
            runInsert(
                (TxInsert) action,
                session,
                api,
                ctx,
                requestCrs,
                touchedIdsByCollection,
                validate,
                skipInvalid,
                strategy,
                mutationTimestamp);
        case REPLACE ->
            runReplace(
                (TxReplace) action,
                session,
                api,
                ctx,
                requestCrs,
                touchedIdsByCollection,
                validate,
                strategy,
                mutationTimestamp);
        case UPDATE ->
            runUpdate(
                (TxUpdate) action,
                session,
                api,
                ctx,
                requestCrs,
                touchedIdsByCollection,
                fromWfs,
                strategy,
                mutationTimestamp);
        case DELETE ->
            runDelete(
                (TxDelete) action,
                session,
                api,
                touchedIdsByCollection,
                strategy,
                mutationTimestamp);
        default -> throw new IllegalArgumentException("Unknown action type: " + action.getType());
      };
    } catch (RuntimeException e) {
      return failed(canonicalCollectionId(api, action.getCollectionId()), action, e);
    }
  }

  private ActionResult runInsert(
      TxInsert action,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      Map<String, Set<String>> touchedIdsByCollection,
      boolean validate,
      boolean skipInvalid,
      MutationStrategy strategy,
      Instant mutationTimestamp) {
    Axes axes = crsInfo.is3d(requestCrs) ? Axes.XYZ : Axes.XY;
    OgcApiDataV2 apiData = api.getData();
    String featureType = resolveFeatureType(apiData, action.getCollectionId());
    String canonicalCollectionId = canonicalCollectionId(apiData, action.getCollectionId());
    Map<SchemaBase.Role, Object> roleOverrides =
        strategy.insertRoleOverrides(apiData, action, mutationTimestamp, Optional.empty());
    boolean checkChain = strategy.disallowsSameFeatureChain(apiData, action);
    Set<String> touched =
        checkChain
            ? touchedIdsByCollection.getOrDefault(canonicalCollectionId, Set.of())
            : Set.of();
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
    boolean preflight = strategy.requiresInsertPreflight();
    Iterator<InsertItem> items = action.items();
    while (items.hasNext()) {
      InsertItem item = items.next();
      // Composite-id split: the raw gml:id may carry a uniqueness suffix; the
      // canonical id is what we check / write. Items without a featureId pass through unchanged.
      Optional<CompositeId> compositeForItem =
          item.featureId()
              .map(raw -> strategy.splitCompositeId(apiData, canonicalCollectionId, raw));
      Optional<String> canonicalIdForItem = compositeForItem.map(CompositeId::canonical);
      boolean needsIdOverride =
          compositeForItem.map(c -> !c.canonical().equals(item.featureId().get())).orElse(false);
      if (checkChain
          && canonicalIdForItem.isPresent()
          && touched.contains(canonicalIdForItem.get())) {
        return failed(
            canonicalCollectionId,
            action,
            chainRejectError(canonicalCollectionId, canonicalIdForItem.get(), action),
            List.of(canonicalIdForItem.get()));
      }
      if (preflight && canonicalIdForItem.isPresent()) {
        // Versioned-Insert pre-flight: refuse to write if any version of this id already exists
        // (open or retired). Adding new versions of an existing feature goes through Replace /
        // Update / Delete; Insert is reserved for brand-new ids.
        //
        // The per-item batch must be flushed first so existing-row checks see writes from earlier
        // items in this same action (e.g. an Insert/Insert pair for the same id within one
        // action, second insert would otherwise miss the first).
        if (!batch.isEmpty()) {
          ActionResult failedBatch =
              flushInsertBatch(
                  action,
                  apiData,
                  session,
                  featureType,
                  batch,
                  batchItems,
                  requestCrs,
                  ids,
                  extents,
                  roleOverrides);
          if (failedBatch != null) return failedBatch;
        }
        MutationResult precheck =
            session.assertNoConflictingVersion(featureType, canonicalIdForItem.get());
        if (precheck.getError().isPresent()) {
          return failed(
              canonicalCollectionId,
              action,
              precheck.getError().get(),
              List.of(canonicalIdForItem.get()));
        }
      }
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
            if (canonicalIdForItem.isPresent()) {
              skippedIds.add(canonicalIdForItem.get());
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
        if (needsIdOverride) {
          // Composite gml:id was carrying a uniqueness suffix; flush the current batch and write
          // this item on its own with an extra ID role override forcing the canonical id into
          // the storage column. Subsequent items rejoin the normal batch.
          if (!batch.isEmpty()) {
            ActionResult failedBatch =
                flushInsertBatch(
                    action,
                    apiData,
                    session,
                    featureType,
                    batch,
                    batchItems,
                    requestCrs,
                    ids,
                    extents,
                    roleOverrides);
            if (failedBatch != null) return failedBatch;
          }
          LinkedHashMap<SchemaBase.Role, Object> perItemOverrides =
              new java.util.LinkedHashMap<>(roleOverrides);
          perItemOverrides.put(SchemaBase.Role.ID, canonicalIdForItem.get());
          List<FeatureTokenSource> single = new ArrayList<>(1);
          single.add(source);
          List<InsertItem> singleItem = new ArrayList<>(1);
          singleItem.add(item);
          ActionResult failedSingle =
              flushInsertBatch(
                  action,
                  apiData,
                  session,
                  featureType,
                  single,
                  singleItem,
                  requestCrs,
                  ids,
                  extents,
                  perItemOverrides);
          if (failedSingle != null) return failedSingle;
          continue;
        }
        batch.add(source);
        batchItems.add(item);
      } catch (java.io.IOException e) {
        return failedInsert(action, apiData, batchItems, e);
      }
      if (batch.size() >= INSERT_BATCH_SIZE) {
        ActionResult failed =
            flushInsertBatch(
                action,
                apiData,
                session,
                featureType,
                batch,
                batchItems,
                requestCrs,
                ids,
                extents,
                roleOverrides);
        if (failed != null) return failed;
      }
    }
    if (!batch.isEmpty()) {
      ActionResult failed =
          flushInsertBatch(
              action,
              apiData,
              session,
              featureType,
              batch,
              batchItems,
              requestCrs,
              ids,
              extents,
              roleOverrides);
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
      ExtentAccumulator extents,
      Map<SchemaBase.Role, Object> roleOverrides) {
    try {
      MutationResult mr =
          roleOverrides.isEmpty()
              ? session.createFeatures(featureType, batch, crs)
              : session.createFeatures(featureType, batch, crs, roleOverrides);
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
    return failed(
        canonicalCollectionId(apiData, action.getCollectionId()),
        action,
        error,
        candidateIds,
        candidatePayloads);
  }

  private ActionResult runReplace(
      TxReplace action,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      Map<String, Set<String>> touchedIdsByCollection,
      boolean validate,
      MutationStrategy strategy,
      Instant mutationTimestamp) {
    Axes axes = crsInfo.is3d(requestCrs) ? Axes.XYZ : Axes.XY;
    OgcApiDataV2 apiData = api.getData();
    String featureType = resolveFeatureType(apiData, action.getCollectionId());
    String canonicalCollectionId = canonicalCollectionId(apiData, action.getCollectionId());

    List<String> targetIds =
        resolveTargetIds(
            action.getTargetIds(),
            action.getFilter(),
            apiData,
            canonicalCollectionId,
            "Replace action");
    if (targetIds.isEmpty()) {
      throw new IllegalArgumentException(
          "Replace action filter matched no feature ids for collection '"
              + canonicalCollectionId
              + "'.");
    }
    if (targetIds.size() > 1) {
      throw new IllegalArgumentException(
          "Replace action must target exactly one feature id; got "
              + targetIds.size()
              + " for collection '"
              + canonicalCollectionId
              + "'.");
    }
    String rawId = targetIds.get(0);
    // Composite-id split: on versioned collections the rid may carry a packed
    // PRIMARY_INTERVAL_START suffix. `canonical` is what the database stores; `expectedStart`
    // becomes an If-Unmodified-Since-style predicate on the retire SQL.
    CompositeId composite = strategy.splitCompositeId(apiData, canonicalCollectionId, rawId);
    String id = composite.canonical();

    String chainConflict =
        firstChainConflict(
            strategy, apiData, action, List.of(id), touchedIdsByCollection, canonicalCollectionId);
    if (chainConflict != null) {
      return failed(
          canonicalCollectionId,
          action,
          chainRejectError(canonicalCollectionId, chainConflict, action),
          List.of(id));
    }

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
      if (strategy.retiresOnReplace()) {
        // Versioned Replace: retire the open version (`PRIMARY_INTERVAL_END = ts WHERE
        // PRIMARY_INTERVAL_END IS NULL AND startCol < ts [AND startCol = <expectedStart>]`),
        // 409 if zero rows matched and no expectedStart was supplied (concurrent retirement,
        // unknown id, or backdating violation); 412 when expectedStart was supplied and no row
        // matched it (the open version is not the one the client thought). Then insert the new
        // body as a fresh version with the strategy's role overrides (end = NULL forced on
        // Replace; plus the denorm PREDECESSOR_INTERVAL_START captured before the retire). The
        // retire itself sets the retired row's SUCCESSOR_INTERVAL_START to the same ts.
        //
        // Body-extracted retire timestamp: in client mode the new version's start lives in the
        // Replace body's lzi.beg; using it as the retire timestamp gives v1 a contiguous
        // interval [old_start, new_start] instead of leaving end = now (the scopeTimestamp
        // placeholder). Falls back to mutationTimestamp when the body has no recognisable
        // start.
        FeatureSchema collectionSchemaForReplace =
            resolveFeatureSchema(apiData, canonicalCollectionId);
        Instant retireTimestamp =
            strategy
                .extractPrimaryIntervalStart(
                    apiData, collectionSchemaForReplace, action.getMediaType(), action.getFeature())
                .orElse(mutationTimestamp);
        Optional<String> predecessorStart = session.getOpenVersionStart(featureType, id);
        MutationResult retire =
            session.retireFeature(featureType, id, retireTimestamp, composite.expectedStart());
        rejectIfError(retire);
        if (retire.getIds().isEmpty()) {
          if (composite.expectedStart().isPresent()) {
            throw new IllegalArgumentException(
                "Precondition failed: feature id '"
                    + id
                    + "' in collection '"
                    + canonicalCollectionId
                    + "' has no open version with PRIMARY_INTERVAL_START = "
                    + composite.expectedStart().get()
                    + " (the composite rid suffix is stale or the open version has been"
                    + " superseded since the client read it).");
          }
          throw new IllegalArgumentException(
              "No open version of feature id '"
                  + id
                  + "' in collection '"
                  + canonicalCollectionId
                  + "' was available for retirement (already retired or unknown).");
        }
        Map<SchemaBase.Role, Object> roleOverrides =
            new java.util.LinkedHashMap<>(
                strategy.insertRoleOverrides(apiData, action, retireTimestamp, predecessorStart));
        // Composite-id: the Replace body's gml:id may carry the same composite
        // suffix the rid does (NAS uses it to keep XML IDs unique when the same feature is
        // touched more than once in one transaction). The stored objid must be the canonical
        // id; force it via the ID role override, same as the per-item Insert path.
        if (!composite.canonical().equals(rawId)) {
          roleOverrides.put(SchemaBase.Role.ID, composite.canonical());
        }
        mr =
            roleOverrides.isEmpty()
                ? session.createFeatures(featureType, List.of(source), requestCrs)
                : session.createFeatures(featureType, List.of(source), requestCrs, roleOverrides);
        rejectIfError(mr);
      } else {
        mr = session.updateFeature(featureType, id, source, requestCrs, false);
        rejectIfError(mr);
      }
    } catch (RuntimeException e) {
      return failed(canonicalCollectionId, action, e, List.of(id));
    }

    return new ImmutableActionResult.Builder()
        .type(TxActionType.REPLACE)
        .collectionId(canonicalCollectionId)
        .actionId(action.getActionId())
        .status(ActionStatus.SUCCESS)
        .featureIds(mr.getIds().isEmpty() ? List.of(id) : mr.getIds())
        .newBoundingBox(mr.getSpatialExtent())
        .newInterval(toIntervalMillis(mr.getTemporalExtent()))
        .build();
  }

  private ActionResult runUpdate(
      TxUpdate action,
      Session session,
      OgcApi api,
      ApiRequestContext ctx,
      EpsgCrs requestCrs,
      Map<String, Set<String>> touchedIdsByCollection,
      boolean fromWfs,
      MutationStrategy strategy,
      Instant mutationTimestamp) {
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
              + canonicalCollectionId
              + "'.");
    }

    String chainConflict =
        firstChainConflict(
            strategy, apiData, action, targetIds, touchedIdsByCollection, canonicalCollectionId);
    if (chainConflict != null) {
      return failed(
          canonicalCollectionId,
          action,
          chainRejectError(canonicalCollectionId, chainConflict, action),
          targetIds);
    }

    // Every Update is a native SQL UPDATE issued on the session's own connection, so
    // prior writes in the same atomic transaction are visible to it. The v1 touched-id reject
    // and the GET-merge-write fall-back have been removed; chaining Insert/Replace/Update with
    // Update on the same id now works natively. Versioned strategies override this via
    // MutationStrategy.chooseUpdateMode(...) and the patchOpenVersion / cloneAndPatchFeature
    // Session methods.

    List<FeatureTransactions.PropertyUpdate> updates;
    try {
      updates = buildPropertyUpdates(action, apiData, canonicalCollectionId, fromWfs, crs);
    } catch (RuntimeException e) {
      // Action-level failure (bad payload, non-whitelisted property, unknown path, etc.) —
      // attribute it to every target id so the log line and the result both name the
      // features the client tried to update.
      return failed(canonicalCollectionId, action, e, targetIds);
    }

    MutationStrategy.UpdateMode mode;
    try {
      FeatureSchema schema = resolveFeatureSchema(apiData, canonicalCollectionId);
      mode = strategy.chooseUpdateMode(apiData, schema, action, updates, mutationTimestamp);
    } catch (RuntimeException e) {
      return failed(canonicalCollectionId, action, e, targetIds);
    }

    List<String> updatedIds = new ArrayList<>();
    for (String rawId : targetIds) {
      // Composite-id split. For NATIVE updates the canonical id is just the raw id
      // (no versioning); for RETIRE_IN_PLACE and CLONE_AND_PATCH the suffix becomes an
      // If-Unmodified-Since predicate on the open-version lookup.
      CompositeId composite = strategy.splitCompositeId(apiData, canonicalCollectionId, rawId);
      String id = composite.canonical();
      try {
        MutationResult mr =
            switch (mode) {
              case NATIVE -> session.patchFeature(featureType, id, updates, crs);
              case RETIRE_IN_PLACE ->
                  session.patchOpenVersion(
                      featureType, id, updates, crs, composite.expectedStart());
              case CLONE_AND_PATCH ->
                  session.cloneAndPatchFeature(
                      featureType, id, updates, mutationTimestamp, crs, composite.expectedStart());
            };
        rejectIfError(mr);
        if (mr.getIds().isEmpty()) {
          boolean retireOrClone =
              mode == MutationStrategy.UpdateMode.RETIRE_IN_PLACE
                  || mode == MutationStrategy.UpdateMode.CLONE_AND_PATCH;
          if (retireOrClone && composite.expectedStart().isPresent()) {
            return failed(
                canonicalCollectionId,
                action,
                new IllegalArgumentException(
                    "Precondition failed: feature id '"
                        + id
                        + "' in collection '"
                        + canonicalCollectionId
                        + "' has no open version with PRIMARY_INTERVAL_START = "
                        + composite.expectedStart().get()
                        + " (the composite rid suffix is stale or the open version has been"
                        + " superseded since the client read it)."),
                List.of(id));
          }
          updatedIds.add(id);
        } else {
          updatedIds.addAll(mr.getIds());
        }
      } catch (RuntimeException e) {
        return failed(canonicalCollectionId, action, e, List.of(id));
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
      result.add(ImmutablePropertyUpdate.builder().path(path).value(Optional.empty()).build());
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
    return ImmutablePropertyUpdate.builder().path(canonicalPath).value(Optional.of(value)).build();
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
    FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return collectionCfg
        .getExtension(TransactionsConfiguration.class)
        .map(TransactionsConfiguration::getUpdatablePropertyPaths)
        .orElse(List.of());
  }

  private FeatureSchema resolveFeatureSchema(OgcApiDataV2 apiData, String canonicalCollectionId) {
    FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return providers
        .getFeatureSchema(apiData, collectionCfg)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No feature schema for collection '" + canonicalCollectionId + "'"));
  }

  private static boolean gmlUseAlias(OgcApiDataV2 apiData, String canonicalCollectionId) {
    FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return collectionCfg
        .getExtension(GmlConfiguration.class)
        .map(AliasConfiguration::isUseAlias)
        .orElse(false);
  }

  private static boolean geoJsonUseAlias(OgcApiDataV2 apiData, String canonicalCollectionId) {
    FeatureTypeConfigurationOgcApi collectionCfg =
        resolveCollection(apiData, canonicalCollectionId);
    return collectionCfg
        .getExtension(GeoJsonConfiguration.class)
        .map(AliasConfiguration::isUseAlias)
        .orElse(false);
  }

  private ActionResult runDelete(
      TxDelete action,
      Session session,
      OgcApi api,
      Map<String, Set<String>> touchedIdsByCollection,
      MutationStrategy strategy,
      Instant mutationTimestamp) {
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
              + canonicalCollectionId
              + "'.");
    }

    String chainConflict =
        firstChainConflict(
            strategy, apiData, action, targetIds, touchedIdsByCollection, canonicalCollectionId);
    if (chainConflict != null) {
      return failed(
          canonicalCollectionId,
          action,
          chainRejectError(canonicalCollectionId, chainConflict, action),
          targetIds);
    }

    String featureType = resolveFeatureType(api.getData(), action.getCollectionId());
    boolean retires = strategy.retiresOnDelete();
    List<String> deleted = new ArrayList<>();
    for (String rawId : targetIds) {
      // Composite-id split: on versioned collections the rid may carry a packed
      // PRIMARY_INTERVAL_START suffix. `canonical` is the database id; `expectedStart` becomes
      // an If-Unmodified-Since-style predicate on the retire SQL.
      CompositeId composite = strategy.splitCompositeId(apiData, canonicalCollectionId, rawId);
      String id = composite.canonical();
      try {
        MutationResult mr =
            retires
                ? session.retireFeature(
                    featureType, id, mutationTimestamp, composite.expectedStart())
                : session.deleteFeature(featureType, id);
        rejectIfError(mr);
        if (retires) {
          // Versioned delete (retire): zero rows matched means there is no open version of this
          // id — either already retired or unknown, OR the expectedStart didn't match (412
          // Precondition Failed semantics). Surface a clear error so the client can distinguish
          // from the successful no-op semantics of plain DELETE.
          if (mr.getIds().isEmpty()) {
            if (composite.expectedStart().isPresent()) {
              throw new IllegalArgumentException(
                  "Precondition failed: feature id '"
                      + id
                      + "' in collection '"
                      + canonicalCollectionId
                      + "' has no open version with PRIMARY_INTERVAL_START = "
                      + composite.expectedStart().get()
                      + " (the composite rid suffix is stale or the open version has been"
                      + " superseded since the client read it).");
            }
            throw new IllegalArgumentException(
                "No open version of feature id '"
                    + id
                    + "' in collection '"
                    + canonicalCollectionId
                    + "' was available for retirement (already retired or unknown).");
          }
          deleted.addAll(mr.getIds());
        } else {
          // SqlMutationSession.deleteFeature only populates getIds() when the SQL DELETE actually
          // matched a row. Treat an empty result as a no-op so totalDeleted / deleteResults
          // reflect only features that were really removed, not every rid the caller named in the
          // filter.
          if (!mr.getIds().isEmpty()) {
            deleted.add(id);
          }
        }
      } catch (RuntimeException e) {
        return failed(canonicalCollectionId, action, e, List.of(id));
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
        FeatureChanges sink;
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

  // Strategy hook: in atomic transactions, some strategies forbid touching
  // the same feature id more than once (e.g. versioned + server mode, where every action shares
  // one mutationTimestamp). Called by each run method after target ids are resolved. Returns the
  // first conflicting id; null if none.
  private static String firstChainConflict(
      MutationStrategy strategy,
      OgcApiDataV2 apiData,
      TxAction action,
      Collection<String> candidateIds,
      Map<String, Set<String>> touchedIdsByCollection,
      String canonicalCollectionId) {
    if (!strategy.disallowsSameFeatureChain(apiData, action)) {
      return null;
    }
    Set<String> touched = touchedIdsByCollection.get(canonicalCollectionId);
    if (touched == null || touched.isEmpty()) {
      return null;
    }
    for (String id : candidateIds) {
      if (touched.contains(id)) {
        return id;
      }
    }
    return null;
  }

  private static IllegalArgumentException chainRejectError(
      String collectionId, String featureId, TxAction action) {
    return new IllegalArgumentException(
        "Same-feature chain rejected: feature id '"
            + featureId
            + "' in collection '"
            + collectionId
            + "' was already touched by an earlier action in this atomic transaction, and the"
            + " collection's mutation strategy forbids same-feature chains under the active mode"
            + " (no-backdating rule for versioned collections under mutationTime: server). Split"
            + " the actions into separate transactions or switch to mutationTime: client.");
  }

  // Pick the highest-priority registered MutationStrategy that is enabled for the given
  // collection. The plain strategy binds to FoundationConfiguration and therefore always applies
  // (priority 0), so the lookup never fails. The resolved strategy routes the per-action
  // behaviour (timestamp resolution, retire-and-insert, retire-only, etc.) and is named in
  // the dispatch log line above.
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
  FeatureChanges resolveChanges(OgcApi api, String collectionId) {
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
  private static FeatureTypeConfigurationOgcApi resolveCollection(
      OgcApiDataV2 apiData, String collectionId) {
    Map<String, FeatureTypeConfigurationOgcApi> all = apiData.getCollections();
    FeatureTypeConfigurationOgcApi cfg = all.get(collectionId);
    if (cfg != null) return cfg;
    for (Map.Entry<String, FeatureTypeConfigurationOgcApi> e : all.entrySet()) {
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
    FeatureTypeConfigurationOgcApi collectionCfg = resolveCollection(apiData, collectionId);
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
      FeatureTypeConfigurationOgcApi cfg = resolveCollection(apiData, collectionId);
      return cfg.getExtension(FeaturesCoreConfiguration.class)
          .flatMap(FeaturesCoreConfiguration::getFeatureType)
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
      Optional<Cql2Expression> filter,
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
      Cql2Expression expression, Set<String> idPropertyNames) {
    if (expression instanceof In) {
      In in = (In) expression;
      ensureIdProperty(in.getArgs().isEmpty() ? null : in.getArgs().get(0), idPropertyNames);
      if (in.getArgs().size() < 2 || !(in.getArgs().get(1) instanceof ArrayLiteral)) {
        throw new IllegalArgumentException(
            "Malformed IN expression: expected ArrayLiteral second argument.");
      }
      Object raw = ((ArrayLiteral) in.getArgs().get(1)).getValue();
      if (!(raw instanceof List)) {
        throw new IllegalArgumentException(
            "Malformed IN expression: ArrayLiteral value is not a list (was "
                + (raw == null ? "null" : raw.getClass().getSimpleName())
                + ").");
      }
      ImmutableList.Builder<String> out = ImmutableList.builder();
      for (Object element : (List<?>) raw) {
        if (element instanceof ScalarLiteral) {
          Object v = ((ScalarLiteral) element).getValue();
          out.add(String.valueOf(v));
        } else {
          throw new IllegalArgumentException(
              "Only scalar-literal id values are supported in IN expressions; got: "
                  + (element == null ? "null" : element.getClass().getSimpleName()));
        }
      }
      return out.build();
    }
    if (expression instanceof Eq) {
      Eq eq = (Eq) expression;
      ensureIdProperty(eq.getArgs().isEmpty() ? null : eq.getArgs().get(0), idPropertyNames);
      if (eq.getArgs().size() < 2 || !(eq.getArgs().get(1) instanceof ScalarLiteral)) {
        throw new IllegalArgumentException(
            "Malformed Eq expression: expected ScalarLiteral second argument.");
      }
      Object v = ((ScalarLiteral) eq.getArgs().get(1)).getValue();
      return ImmutableList.of(String.valueOf(v));
    }
    throw new IllegalArgumentException(
        "Only id-based filters (IN or =) on the feature's id property are currently supported by"
            + " the transaction executor; got: "
            + expression.getClass().getSimpleName());
  }

  private static void ensureIdProperty(Object firstArg, Set<String> idPropertyNames) {
    if (!(firstArg instanceof Property)) {
      throw new IllegalArgumentException(
          "Transaction filter must reference the feature's id property as its first argument.");
    }
    String name = ((Property) firstArg).getName();
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

  private static ActionResult skipped(String collectionId, TxAction action) {
    return new ImmutableActionResult.Builder()
        .type(action.getType())
        .collectionId(collectionId)
        .actionId(action.getActionId())
        .status(ActionStatus.SKIPPED)
        .build();
  }

  private static ActionResult failed(String collectionId, TxAction action, Throwable error) {
    return failed(collectionId, action, error, List.of(), List.of());
  }

  private static ActionResult failed(
      String collectionId, TxAction action, Throwable error, List<String> failedFeatureIds) {
    return failed(collectionId, action, error, failedFeatureIds, List.of());
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
      String collectionId,
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
        .collectionId(collectionId)
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

  private static String actionLabel(TxAction action) {
    return action.getActionId().orElse(action.getType() + "@" + action.getCollectionId());
  }

  /**
   * Attaches the non-fatal SQL warnings the session collected while this action ran (e.g.
   * PostgreSQL {@code RAISE WARNING} / {@code RAISE NOTICE} from triggers). Draining per action
   * attributes each warning to the action that caused it.
   */
  private static ActionResult withActionWarnings(ActionResult result, Session session) {
    List<String> actionWarnings = session.drainWarnings();
    if (actionWarnings.isEmpty()) {
      return result;
    }
    return new ImmutableActionResult.Builder().from(result).warnings(actionWarnings).build();
  }

  /**
   * The atomic transaction failed after some actions had already succeeded — their writes are gone,
   * so their results must not read as successes in the response.
   */
  private static List<ActionResult> flipSuccessesToRolledBack(List<ActionResult> results) {
    List<ActionResult> out = new ArrayList<>(results.size());
    for (ActionResult r : results) {
      if (r.getStatus() == ActionStatus.SUCCESS) {
        out.add(
            new ImmutableActionResult.Builder()
                .from(r)
                .status(ActionStatus.ROLLED_BACK)
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
