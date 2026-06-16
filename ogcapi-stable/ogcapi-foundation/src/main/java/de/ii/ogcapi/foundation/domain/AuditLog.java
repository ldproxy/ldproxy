/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.Set;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;

/**
 * @langEn Audit logging options for the API. Further options can be configured in the [global
 *     configuration](../application/20-configuration/README.md). For example, audit logging must be
 *     globally enabled before it can be used.
 *     <p>Example configuration:
 * @langDe Audit-Logging Optionen für die API. Weitere Optionen lassen sich in der [globalen
 *     Konfiguration](../application/20-configuration/README.md) einstellen. Beispielsweise muss das
 *     Audit-Logging zur Nutzung global aktiviert sein.
 *     <p>Beispiel Konfiguration:
 * @langAll <code>
 * ```yml
 * auditLog:
 *   enabled: true
 *   includePropertyValues: true
 *   operations:
 *     - "data:read::vineyards"
 *     - "write"
 * ```
 *     </code>
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAuditLog.Builder.class)
public interface AuditLog {

  /**
   * @langEn Option to explicitly disable audit logging for the API.
   * @langDe Option, um das Audit-Logging für die API explizit zu deaktivieren.
   * @default true
   */
  @Default
  default boolean getEnabled() {
    return true;
  }

  /**
   * @langEn If true, the values of the requested properties are logged as well.
   * @langDe Falls true, werden die Werte der angefragten Properties ebenfalls geloggt.
   * @default true
   */
  @Default
  default boolean getIncludePropertyValues() {
    return true;
  }

  /**
   * @langEn Option to specify the operations for which a log entry should be created. The syntax is
   *     described in [Access Control](#access-control).
   * @langDe Option, um die Operationen anzugeben, für die ein Log-Eintrag erstellt werden soll. Die
   *     Syntax ist in [Access Control](#access-control) beschrieben.
   * @default {"data:read", "write"}
   */
  @Default
  default Set<String> getOperations() {
    return Set.of("data:read", "write");
  }
}
