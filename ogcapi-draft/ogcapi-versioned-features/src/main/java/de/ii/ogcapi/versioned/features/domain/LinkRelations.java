/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

/**
 * IANA-registered link relation types used by the Versioned Features building block.
 *
 * <ul>
 *   <li>{@code predecessor-version}, {@code successor-version}, {@code latest-version} are defined
 *       by <a href="https://datatracker.ietf.org/doc/html/rfc5829">RFC 5829</a> (Link Relation
 *       Types for Simple Version Navigation between Web Resources).
 *   <li>{@code version-history}, {@code timemap}, {@code memento}, {@code original} are defined by
 *       <a href="https://datatracker.ietf.org/doc/html/rfc7089">RFC 7089</a> (HTTP Framework for
 *       Time-Based Access to Resource States — Memento).
 * </ul>
 */
public final class LinkRelations {

  /** RFC 5829: link to the resource version immediately preceding the current one. */
  public static final String PREDECESSOR_VERSION = "predecessor-version";

  /** RFC 5829: link to the resource version immediately following the current one. */
  public static final String SUCCESSOR_VERSION = "successor-version";

  /** RFC 5829: link to the latest version of the resource. */
  public static final String LATEST_VERSION = "latest-version";

  /** RFC 7089: link to the Time Map resource enumerating all versions of the original. */
  public static final String VERSION_HISTORY = "version-history";

  /** RFC 7089: alias of {@link #VERSION_HISTORY}; the resource is itself a Time Map. */
  public static final String TIMEMAP = "timemap";

  /** RFC 7089: link to a Memento — a snapshot of the resource at a prior datetime. */
  public static final String MEMENTO = "memento";

  /** RFC 7089: link to the original resource that the Memento is a snapshot of. */
  public static final String ORIGINAL = "original";

  private LinkRelations() {}
}
