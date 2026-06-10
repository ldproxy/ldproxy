/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import de.ii.xtraplatform.features.domain.FeatureTokenTransformer;
import de.ii.xtraplatform.features.domain.FeatureTokenTransformerExtension;

/**
 * Query-extension that wires the composite-id rewrite into the feature stream when the {@code
 * versions-as-features-unique-ids} profile is selected. Carries the configured {@code
 * compositeIdPattern} and {@code compositeIdTimestampFormat} so the transformer can format the id
 * at feature end without re-reading the API data.
 */
public final class CompositeIdExtension implements FeatureTokenTransformerExtension {

  private final String pattern;
  private final String timestampFormat;

  public CompositeIdExtension(String pattern, String timestampFormat) {
    this.pattern = pattern;
    this.timestampFormat = timestampFormat;
  }

  @Override
  public FeatureTokenTransformer createTransformer() {
    return new FeatureTokenTransformerCompositeId(pattern, timestampFormat);
  }
}
