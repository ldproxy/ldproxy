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
 * as {@code featureHref + "?datetime=<start>"}, where {@code <start>} is the raw start value as
 * emitted by the provider — a date stays a date. {@code latestStartValue} is the raw start value of
 * the open / latest version so each format can emit the {@code latest-version} link without
 * re-scanning the list.
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
  private final String latestStartValue;

  public TimeMap(
      String collectionId,
      String featureId,
      String featureHref,
      List<Link> resourceLinks,
      List<Memento> mementos,
      String latestStartValue) {
    this.collectionId = collectionId;
    this.featureId = featureId;
    this.featureHref = featureHref;
    this.resourceLinks = resourceLinks;
    this.mementos = mementos;
    this.latestStartValue = latestStartValue;
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

  public String getLatestStartValue() {
    return latestStartValue;
  }

  /**
   * One version of the feature on the Time Map. {@code endValue}/{@code end} are null for the open
   * version. The raw values are the provider values — a date stays a date; the instants are only
   * used where a full datetime is structurally required (the RFC 1123 {@code datetime} attribute)
   * or a datetime value needs display formatting.
   */
  public static final class Memento {
    private final String startValue;
    private final String endValue;
    private final boolean startIsDate;
    private final boolean endIsDate;
    private final Instant start;
    private final Instant end;
    private final String href;

    public Memento(
        String startValue,
        String endValue,
        boolean startIsDate,
        boolean endIsDate,
        Instant start,
        Instant end,
        String href) {
      this.startValue = startValue;
      this.endValue = endValue;
      this.startIsDate = startIsDate;
      this.endIsDate = endIsDate;
      this.start = start;
      this.end = end;
      this.href = href;
    }

    public String getStartValue() {
      return startValue;
    }

    public String getEndValue() {
      return endValue;
    }

    public boolean isStartDate() {
      return startIsDate;
    }

    public boolean isEndDate() {
      return endIsDate;
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
