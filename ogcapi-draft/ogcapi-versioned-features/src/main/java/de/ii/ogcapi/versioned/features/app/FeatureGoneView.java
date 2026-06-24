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
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Mustache view backing the HTML representation of a {@code 410 Gone} response on {@code
 * /items/{featureId}}. Renders the standard header/breadcrumb chain plus a short explanation that
 * the feature existed at other points in time and a link to the Time Map (version history) for the
 * canonical feature.
 */
@Value.Immutable
@Value.Style(builder = "new")
public abstract class FeatureGoneView extends OgcApiView {

  public abstract String featureId();

  public abstract String timeMapHref();

  public abstract String latestVersionHref();

  public abstract I18n i18n();

  public abstract Locale language();

  @Value.Derived
  public String timeMapLabel() {
    return i18n().get("versionHistoryLink", Optional.ofNullable(language()));
  }

  @Value.Derived
  public String latestVersionLabel() {
    return i18n().get("latestVersionLink", Optional.ofNullable(language()));
  }

  public FeatureGoneView() {
    super("featureGone.mustache");
  }
}
