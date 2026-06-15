/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import jakarta.validation.constraints.NotNull;

public abstract class ProfileFeatureQuery extends ProfileGeneric {

  protected ProfileFeatureQuery(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  public abstract FeatureQuery transformFeatureQuery(@NotNull FeatureQuery query);

  /**
   * Context-aware variant; defaults to {@link #transformFeatureQuery(FeatureQuery)} so existing
   * profiles don't need changes. Override when the transformation needs per-collection config (e.g.
   * {@code versions-as-features-unique-ids} reads {@code compositeIdPattern}).
   */
  public FeatureQuery transformFeatureQuery(
      @NotNull FeatureQuery query, @NotNull OgcApiDataV2 apiData, @NotNull String collectionId) {
    return transformFeatureQuery(query);
  }
}
