/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.domain;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.immutables.value.Value.Modifiable;

/**
 * @author zahnen
 */
// TODO Look following classes: FeaturesFormatHtml
@Value.Immutable
@Value.Style(builder = "new")
@Modifiable
public abstract class FeatureCollectionView extends FeaturesView {

  FeatureCollectionView() {
    super("featureCollection.mustache");
  }

  @Value.Derived
  public boolean isCollection() {
    return true;
  }

  @Value.Default
  public boolean fromStoredQuery() {
    return false;
  }

  @Nullable
  public abstract List<Map<String, String>> translationsMapDe();

  public abstract List<Map<String, String>> translationsMapEn();
}
