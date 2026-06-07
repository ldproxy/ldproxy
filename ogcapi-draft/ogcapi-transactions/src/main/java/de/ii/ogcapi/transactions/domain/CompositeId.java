/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Result of splitting a raw feature id (as it appears in a {@code wfs:ResourceId rid="…"} filter or
 * a {@code gml:id}) into a canonical id and an optional expected {@code PRIMARY_INTERVAL_START}
 * value.
 *
 * <p>Strategies that don't use a composite-id convention return {@link #passthrough(String)}.
 * NAS-style strategies parse a configured pattern and produce both halves.
 *
 * <p>{@code canonical()} is the id that lands (or is expected to be matched) in the database;
 * {@code expectedStart()} is the open version's expected start, used as an
 * If-Unmodified-Since-style predicate on {@code Replace} / {@code Update} / {@code Delete}. Empty
 * for {@code Insert} (no concurrency check) and for non-composite ids.
 */
public final class CompositeId {

  private final String canonical;
  private final Optional<Instant> expectedStart;

  public CompositeId(String canonical, Optional<Instant> expectedStart) {
    this.canonical = canonical;
    this.expectedStart = expectedStart;
  }

  /** Identity split: the raw id is the canonical, with no expected start. */
  public static CompositeId passthrough(String rawId) {
    return new CompositeId(rawId, Optional.empty());
  }

  public String canonical() {
    return canonical;
  }

  public Optional<Instant> expectedStart() {
    return expectedStart;
  }
}
