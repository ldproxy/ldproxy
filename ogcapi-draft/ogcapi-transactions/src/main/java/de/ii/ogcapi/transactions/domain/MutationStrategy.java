/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import de.ii.xtraplatform.features.domain.FeatureTransactions.PropertyUpdate;
import de.ii.xtraplatform.features.domain.SchemaBase;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-collection mutation behaviour applied by the transaction executor.
 *
 * <p>Implementations are discovered via the {@code ExtensionRegistry}; for a given collection the
 * executor picks the highest-{@link #priority() priority} strategy whose {@link
 * ApiExtension#isEnabledForApi(OgcApiDataV2, String)} returns {@code true} — i.e. the strategy's
 * declared building-block configuration is enabled on that collection. The default plain strategy
 * binds to {@code TransactionsConfiguration} and therefore applies whenever the Transactions
 * building block itself is enabled, so the executor always finds a strategy when it runs.
 *
 * <p>The interface itself carries no per-action methods yet. Phase 1.1 (mutation-timestamp
 * resolution) introduces the first hook; Phase 1.2-1.6 add the {@code Insert} / {@code Replace} /
 * {@code Update} / {@code Delete} hooks that diverge between plain and versioned collections.
 */
public interface MutationStrategy extends ApiExtension {

  /**
   * Strategies with a higher priority win when more than one is enabled for the same collection.
   * The default plain strategy uses {@code 0}; specialised strategies (e.g. versioned collections)
   * override with a larger value.
   */
  default int priority() {
    return 0;
  }

  /**
   * Resolve the mutation timestamp the strategy will associate with this action.
   *
   * <p>The executor captures a per-scope timestamp ({@code scopeTimestamp}) — once at executor
   * entry for an atomic transaction, per action for a batch transaction — and offers it to the
   * strategy. Plain strategies ignore the inputs and return {@code scopeTimestamp} unchanged.
   * Versioned strategies dispatch on their {@code mutationTime} configuration: {@code server}
   * returns {@code scopeTimestamp}; {@code client} resolves from the action payload's
   * primary-interval-start property when present, falling back to {@code ogcMutationDatetimeHeader}
   * (the {@code OGC-Mutation-Datetime} request header) and ultimately erroring with 400 when
   * neither is supplied.
   *
   * @param apiData the API data, used to look up per-collection configuration
   * @param action the action whose timestamp is being resolved
   * @param scopeTimestamp the per-scope server clock reading captured by the executor
   * @param ogcMutationDatetimeHeader the parsed {@code OGC-Mutation-Datetime} request header, if
   *     supplied by the client
   */
  default Instant resolveMutationTimestamp(
      OgcApiDataV2 apiData,
      TxAction action,
      Instant scopeTimestamp,
      Optional<Instant> ogcMutationDatetimeHeader) {
    return scopeTimestamp;
  }

  /**
   * Per-role column overrides applied by the feature provider to every feature written by an {@code
   * Insert} action under this strategy. An entry with a non-null value forces the role-bearing
   * column to that value (in the same SQL-literal form the encoder would have stored: bare for
   * numerics, single-quoted for {@code STRING}/{@code DATETIME}/{@code DATE}); an entry with a
   * {@code null} value clears the column so it lands as SQL {@code NULL}. An empty map means "leave
   * client-supplied tokens in place" — the default for the plain strategy.
   *
   * <p>Versioned strategies in {@code server} mode populate {@code PRIMARY_INTERVAL_START} (from
   * {@code mutationTimestamp}) and {@code PRIMARY_INTERVAL_END = null}. When {@code
   * predecessorIntervalStart} is non-empty (i.e. the caller is feeding the new version of an
   * existing feature, as in retire-and-insert flows), versioned strategies additionally populate
   * {@code PREDECESSOR_INTERVAL_START} so the new row's denorm pointer is set; see plan §1.6.
   *
   * @param apiData the API data, used to look up per-collection configuration
   * @param action the insert action whose overrides are being computed
   * @param mutationTimestamp the resolved mutation timestamp for this action
   * @param predecessorIntervalStart {@code PRIMARY_INTERVAL_START} value of the version being
   *     retired in this same operation, if any — used to populate the new row's predecessor denorm
   *     column. Empty for plain {@code Insert} actions (no retire pair).
   */
  default Map<SchemaBase.Role, Object> insertRoleOverrides(
      OgcApiDataV2 apiData,
      TxAction action,
      Instant mutationTimestamp,
      Optional<String> predecessorIntervalStart) {
    return Map.of();
  }

  /**
   * Whether a {@code Replace} action on this collection retires the existing version and inserts a
   * new one instead of overwriting the row in place. Versioned strategies return {@code true};
   * plain inherits the default {@code false}, keeping the native {@code UPDATE} path.
   *
   * <p>When {@code true}, the executor retires the target id via {@link
   * FeatureTransactions.Session#retireFeature(String, String, Instant)} and writes the replacement
   * payload through the standard insert path (carrying {@link #insertRoleOverrides
   * insertRoleOverrides}). A zero-row retirement is surfaced to the client as a 409-style conflict.
   */
  default boolean retiresOnReplace() {
    return false;
  }

  /**
   * How the executor should handle an {@code Update} action on this collection.
   *
   * <ul>
   *   <li>{@code NATIVE} — patch the row in place via {@code Session.patchFeature(...)} (the
   *       default plain path).
   *   <li>{@code RETIRE_IN_PLACE} — apply the property updates (including a non-null value for
   *       {@code PRIMARY_INTERVAL_END}) to the open version's row via {@code
   *       Session.patchOpenVersion(...)}, gated by an {@code endCol IS NULL} predicate. Used by
   *       versioned strategies when the update retires the feature (and the optional sibling
   *       modifications are allowed by the strategy's configuration).
   *   <li>{@code CLONE_AND_PATCH} — clone the open version into a new row, apply the property
   *       updates, retire the old row. Used by versioned strategies when the update creates a new
   *       version.
   * </ul>
   */
  enum UpdateMode {
    NATIVE,
    RETIRE_IN_PLACE,
    CLONE_AND_PATCH
  }

  /**
   * Classify how this {@code Update} action should run on this collection. The default plain
   * strategy returns {@link UpdateMode#NATIVE} (the existing patch-in-place path). Versioned
   * strategies inspect the property updates to choose between {@link UpdateMode#RETIRE_IN_PLACE}
   * (when {@code PRIMARY_INTERVAL_END} is set, optionally alongside whitelisted siblings) and
   * {@link UpdateMode#CLONE_AND_PATCH} (when the update creates a new version), rejecting forbidden
   * combinations (e.g. modifying {@code PRIMARY_INTERVAL_START}, reopening a retired version,
   * mixing retirement with non-whitelisted siblings) with a {@code 400 Bad Request}.
   *
   * @param apiData the API data, used to look up per-collection configuration
   * @param collectionSchema the canonical feature schema of the update's target collection
   * @param action the update action being classified
   * @param updates the resolved property updates (already path-canonicalised and whitelist-checked
   *     by the executor)
   * @param mutationTimestamp the resolved mutation timestamp for this action
   */
  default UpdateMode chooseUpdateMode(
      OgcApiDataV2 apiData,
      FeatureSchema collectionSchema,
      TxAction action,
      List<PropertyUpdate> updates,
      Instant mutationTimestamp) {
    return UpdateMode.NATIVE;
  }

  /**
   * Whether a {@code Delete} action on this collection retires the open version (sets {@code
   * PRIMARY_INTERVAL_END = mutationTimestamp}) instead of physically removing rows. Versioned
   * strategies return {@code true}; plain inherits the default {@code false}, keeping the SQL
   * {@code DELETE} path.
   *
   * <p>When {@code true}, the executor calls {@link
   * FeatureTransactions.Session#retireFeature(String, String, Instant)} per target id. A zero-row
   * retirement is surfaced to the client as a 409-style conflict (already retired or unknown id).
   */
  default boolean retiresOnDelete() {
    return false;
  }

  /**
   * Whether this strategy forbids more than one write action targeting the same feature id within a
   * single atomic transaction. Versioned strategies return {@code true} when the collection's
   * {@code mutationTime} is {@code server} (every action in the atomic transaction shares one
   * timestamp, so a same-feature chain would violate the no-backdating rule from plan §1.5);
   * versioned strategies in {@code client} mode and the plain strategy return {@code false}.
   *
   * <p>The executor evaluates this for atomic transactions only — batch transactions commit each
   * action independently with its own timestamp and never share a feature id across actions in a
   * way the strategy cares about.
   */
  default boolean disallowsSameFeatureChain(OgcApiDataV2 apiData, TxAction action) {
    return false;
  }

  /**
   * Whether the executor should run the versioned-insert pre-flight check via {@link
   * FeatureTransactions.Session#assertNoConflictingVersion(String, String, Instant)} for every
   * {@code Insert} item that carries an id. Used by versioned strategies to reject inserts that
   * would create a second open version, an overlap with a closed version, or a backdated version
   * for the same feature id. Plain strategies inherit the default {@code false}.
   */
  default boolean requiresInsertPreflight() {
    return false;
  }

  /**
   * Split a raw feature id — as it arrived on the wire (e.g. inside a {@code <wfs:ResourceId
   * rid="…"/>} filter or as a {@code gml:id} attribute) — into the canonical id and, when the
   * strategy uses a composite-id convention, the expected {@code PRIMARY_INTERVAL_START} of the
   * open version. Strategies that do not use a composite convention return {@link
   * CompositeId#passthrough(String)}.
   *
   * <p>For NAS-style versioned collections (see {@code
   * VersionedFeaturesConfiguration.compositeIdPattern}), the pattern's named groups {@code id} and
   * {@code start} feed this method's return value. The executor uses {@code canonical} for every
   * SQL operation; {@code expectedStart} acts as an If-Unmodified-Since-style predicate on {@code
   * Replace} / {@code Update} / {@code Delete}, surfacing a 412 Precondition Failed when the open
   * version's start does not match.
   */
  default CompositeId splitCompositeId(OgcApiDataV2 apiData, String collectionId, String rawId) {
    return CompositeId.passthrough(rawId);
  }

  /**
   * Extract the new version's {@code PRIMARY_INTERVAL_START} value from the body of a {@code
   * Replace} action, without consuming the body's bytes for the actual write.
   *
   * <p>Versioned strategies use this to drive the retire-and-insert pair under {@code mutationTime:
   * client} (and as a tightening of the {@code server}-mode placeholder): the retired row's end
   * column should equal the new version's start (contiguous intervals), and the new row's {@code
   * PREDECESSOR_INTERVAL_START} pointer plus the retired row's {@code SUCCESSOR_INTERVAL_START}
   * pointer share that same value. Empty when the body has no recognisable start value or when the
   * strategy doesn't support body extraction.
   *
   * @param apiData the API data, used for per-collection configuration lookups
   * @param collectionSchema the canonical feature schema (used to find which property carries the
   *     {@code PRIMARY_INTERVAL_START} role and what alias the wire payload uses)
   * @param mediaType the request body's media type ({@code application/xml} for {@code
   *     wfs:Transaction}, {@code application/ogc-tx+json} for JSON-tx)
   * @param body the Replace action's payload bytes (a single feature)
   */
  default Optional<Instant> extractPrimaryIntervalStart(
      OgcApiDataV2 apiData, FeatureSchema collectionSchema, MediaType mediaType, byte[] body) {
    return Optional.empty();
  }
}
