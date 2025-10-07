/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.styles.domain.Styles;
import de.ii.ogcapi.tiles.domain.StyleEntry;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class StylesView extends OgcApiView {
  public abstract Styles styles();

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  @Value.Derived
  public List<StyleEntry> styleEntries() {
    return styles().getStyles();
  }

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  public StylesView() {
    super("styles.mustache");
  }
}
