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
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetOgcApi;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class TileMatrixSetView extends OgcApiView {

  public abstract TileMatrixSetOgcApi tileMatrixSet();

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  @Value.Derived
  public String idTitle() {
    return i18n().get("idTitle", language());
  }

  @Value.Derived
  public String boundingBoxTitle() {
    return i18n().get("boundingBoxTitle", language());
  }

  @Value.Derived
  public String wellKnownScaleSetTitle() {
    return i18n().get("wellKnownScaleSetTitle", language());
  }

  @Value.Derived
  public String supportedCrsTitle() {
    return i18n().get("supportedCrsTitle", language());
  }

  @Value.Derived
  public String scaleDenominatorTitle() {
    return i18n().get("scaleDenominatorTitle", language());
  }

  @Value.Derived
  public String cellSizeTitle() {
    return i18n().get("cellSizeTitle", language());
  }

  @Value.Derived
  public String cornerOfOriginTitle() {
    return i18n().get("cornerOfOriginTitle", language());
  }

  @Value.Derived
  public String pointOfOriginTitle() {
    return i18n().get("pointOfOriginTitle", language());
  }

  @Value.Derived
  public String tileWidthTitle() {
    return i18n().get("tileWidthTitle", language());
  }

  @Value.Derived
  public String tileHeightTitle() {
    return i18n().get("tileHeightTitle", language());
  }

  @Value.Derived
  public String matrixWidthTitle() {
    return i18n().get("matrixWidthTitle", language());
  }

  @Value.Derived
  public String matrixHeightTitle() {
    return i18n().get("matrixHeightTitle", language());
  }

  public TileMatrixSetView() {
    super("tileMatrixSet.mustache");
  }
}
