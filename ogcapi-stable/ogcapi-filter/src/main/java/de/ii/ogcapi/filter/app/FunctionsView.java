/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.app;

import de.ii.ogcapi.html.domain.OgcApiView;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@Value.Immutable
@Value.Style(
    builder = "new",
    visibility = ImplementationVisibility.PUBLIC,
    deepImmutablesDetection = true)
public abstract class FunctionsView extends OgcApiView {

  public FunctionsView() {
    super("functions.mustache");
  }

  public abstract List<Map<String, Object>> functions();

  public abstract String none();

  public abstract String argumentsTitle();

  public abstract String returnsTitle();
}
