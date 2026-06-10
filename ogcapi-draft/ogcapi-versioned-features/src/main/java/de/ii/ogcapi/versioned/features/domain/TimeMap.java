/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import de.ii.ogcapi.foundation.domain.Link;
import java.time.Instant;
import java.util.List;

/**
 * Format-agnostic data model for the {@code /items/{featureId}/versions} (Time Map) endpoint. Built
 * once by the queries handler and rendered by each {@link TimeMapFormatExtension}.
 *
 * <p>{@code featureHref} is the canonical resource URI (no query) used to construct memento hrefs
 * as {@code featureHref + "?datetime=<start>"}. {@code latestStart} pre-computes the start
 * timestamp of the open / latest version so each format can emit the {@code latest-version} link
 * without re-scanning the list.
 *
 * <p>{@code resourceLinks} carries the standard {@code self} + {@code alternate} entries produced
 * by {@code DefaultLinksGenerator} (current representation's {@code self} carrying {@code ?f=…}
 * when other representations exist, plus one {@code alternate} per other representation). Format
 * extensions emit them alongside the Time-Map-specific {@code original}/{@code memento}/{@code
 * latest-version} links.
 */
public final class TimeMap {

  private final String collectionId;
  private final String featureId;
  private final String featureHref;
  private final List<Link> resourceLinks;
  private final List<Memento> mementos;
  private final Instant latestStart;

  public TimeMap(
      String collectionId,
      String featureId,
      String featureHref,
      List<Link> resourceLinks,
      List<Memento> mementos,
      Instant latestStart) {
    this.collectionId = collectionId;
    this.featureId = featureId;
    this.featureHref = featureHref;
    this.resourceLinks = resourceLinks;
    this.mementos = mementos;
    this.latestStart = latestStart;
  }

  public String getCollectionId() {
    return collectionId;
  }

  public String getFeatureId() {
    return featureId;
  }

  public String getFeatureHref() {
    return featureHref;
  }

  public List<Link> getResourceLinks() {
    return resourceLinks;
  }

  public List<Memento> getMementos() {
    return mementos;
  }

  public Instant getLatestStart() {
    return latestStart;
  }

  /** One version of the feature on the Time Map. {@code end} is null for the open version. */
  public static final class Memento {
    private final Instant start;
    private final Instant end;
    private final String href;

    public Memento(Instant start, Instant end, String href) {
      this.start = start;
      this.end = end;
      this.href = href;
    }

    public Instant getStart() {
      return start;
    }

    public Instant getEnd() {
      return end;
    }

    public String getHref() {
      return href;
    }
  }
}
