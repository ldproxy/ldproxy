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
import de.ii.ogcapi.processes.domain.model.OgcStatusInfo;
import java.util.Locale;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class StatusInfoView extends OgcApiView {
  public StatusInfoView() {
    super("statusInfo.mustache");
  }

  public abstract OgcStatusInfo statusInfo();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  // keywords

  @Value.Derived
  public String jobIdTitle() {
    return i18n().get("jobIdTitle", language());
  }

  @Value.Derived
  public String processIdTitle() {
    return i18n().get("processIdTitle", language());
  }

  @Value.Derived
  public String messageTitle() {
    return i18n().get("messageTitle", language());
  }

  @Value.Derived
  public String createdTitle() {
    return i18n().get("createdTitle", language());
  }

  @Value.Derived
  public String startedTitle() {
    return i18n().get("startedTitle", language());
  }

  @Value.Derived
  public String finishedTitle() {
    return i18n().get("finishedTitle", language());
  }

  @Value.Derived
  public String updatedTitle() {
    return i18n().get("updatedTitle", language());
  }

  @Value.Derived
  public String progressTitle() {
    return i18n().get("progressTitle", language());
  }
}
