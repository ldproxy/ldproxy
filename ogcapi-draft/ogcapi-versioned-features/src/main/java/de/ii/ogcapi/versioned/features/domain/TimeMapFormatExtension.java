/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.common.domain.GenericFormatExtension;

@AutoMultiBind
public interface TimeMapFormatExtension extends GenericFormatExtension {

  /**
   * Build the per-format encoder. The encoder is the sink of the feature stream — it receives each
   * version as a {@link FeatureVersionTimeMap} POJO via the base class, accumulates the mementos,
   * and at end-of-stream emits the format-specific bytes via {@link
   * FeatureEncoderTimeMap#encode(TimeMap)}.
   */
  FeatureEncoderTimeMap getFeatureEncoder(EncodingContextTimeMap encodingContext);

  /**
   * {@code true} when the format renders the feature title (resolved from the collection's HTML
   * feature title template). The queries handler then selects the properties referenced by the
   * template in addition to the primary temporal interval.
   */
  default boolean requiresFeatureTitle() {
    return false;
  }
}
