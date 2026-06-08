/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;

@Value.Immutable
@JsonDeserialize(builder = ImmutableAuditLog.Builder.class)
public interface AuditLog {

  /**
   * @langEn Option to disable audit logging.
   * @langDe Option, um das Erstellen von Audit-Logs zu deaktivieren.
   * @default true
   */
  @Default
  default boolean getEnabled() {
    return true;
  }
}
