/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format.html;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.processes.domain.model.ogc.OgcProcess;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class ProcessView extends OgcApiView {
  public ProcessView() {
    super("process.mustache");
  }

  public abstract OgcProcess process();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();
}
