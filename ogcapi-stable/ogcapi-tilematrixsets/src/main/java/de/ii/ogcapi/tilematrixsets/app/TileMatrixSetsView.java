/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tilematrixsets.app;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetLinks;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class TileMatrixSetsView extends OgcApiView {
  public abstract List<TileMatrixSetLinks> tileMatrixSets();

  @Value.Derived
  public String none() {
    return i18n().get("none", Optional.ofNullable(language()));
  }

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Locale language();

  public TileMatrixSetsView() {
    super("tileMatrixSets.mustache");
    // tileMatrixSets.getLinks(),
    // i18n.get("tileMatrixSetsTitle", language),
    // i18n.get("tileMatrixSetsDescription", language));
    // this.tileMatrixSets = tileMatrixSets.getTileMatrixSets();

  }
}
