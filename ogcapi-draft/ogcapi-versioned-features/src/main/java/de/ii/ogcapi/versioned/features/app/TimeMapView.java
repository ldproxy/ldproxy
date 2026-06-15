/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Mustache view backing the HTML representation of the Time Map. Renders one entry per version with
 * a clickable {@code memento} link and the version's start timestamp. The {@code original} link
 * points back to the canonical feature resource (no datetime).
 */
@Value.Immutable
@Value.Style(builder = "new")
public abstract class TimeMapView extends OgcApiView {

  public abstract String featureId();

  public abstract String featureHref();

  public abstract List<Entry> entries();

  public abstract I18n i18n();

  public abstract Locale language();

  @Value.Derived
  public String mementoLabel() {
    return i18n().get("mementoLink", Optional.ofNullable(language()));
  }

  @Value.Derived
  public String originalLabel() {
    return i18n().get("originalLink", Optional.ofNullable(language()));
  }

  @Value.Derived
  public String latestVersionLabel() {
    return i18n().get("latestVersionLink", Optional.ofNullable(language()));
  }

  @Value.Derived
  public Entry latest() {
    return entries().isEmpty() ? null : entries().get(entries().size() - 1);
  }

  public TimeMapView() {
    super("timeMap.mustache");
  }

  /** One row on the Time Map page. */
  @Value.Immutable
  @Value.Style(builder = "new")
  public interface Entry {
    String href();

    String startLabel();

    Optional<String> endLabel();

    @Value.Default
    default boolean isOpen() {
      return endLabel().isEmpty();
    }

    Instant startInstant();
  }
}
