/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.html;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.processes.domain.ProcessDescriptionRepresentation;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class ProcessDescriptionsView extends OgcApiView {
  public ProcessDescriptionsView() {
    super("processDescriptions.mustache");
  }

  public abstract List<ProcessDescriptionRepresentation> processDescriptions();

  @Value.Derived
  public String none() {
    return i18n().get("none", Optional.ofNullable(language()));
  }

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Locale language();
}
