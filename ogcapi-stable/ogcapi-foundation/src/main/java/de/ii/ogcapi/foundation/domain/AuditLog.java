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
 * @langEn Audit logging options for the API. Only relevant if audit logging is enabled in the
 *     [global configuration](../application/20-configuration/120-auditLog.md).
 *     <p>#### Example
 * @langDe Audit-Logging Optionen für die API. Nur relevant, wenn Audit-Logging in der [globalen
 *     Konfiguration](../application/20-configuration/120-auditLog.md) aktiviert ist.
 *     <p>#### Beispiel
 * @langAll <code>
 * ```yml
 * auditLog:
 *   enabled: true
 *   includePropertyValues: true
 *   operations:
 *     - "read"
 *     - "write"
 * ```
 *     </code>
 * @langEn #### Options
 * @langDe #### Optionen
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableAuditLog.Builder.class)
public interface AuditLog {

  /**
   * @langEn If `false`, the audit logging is explicitly disabled for the API.
   * @langDe Falls `false`, wird das Audit-Logging für die API explizit zu deaktiviert.
   * @default true
   * @since 4.8
   */
  @Default
  default boolean getEnabled() {
    return true;
  }

  /**
   * @langEn If `false`, only the keys of accessed properties will be logged.
   * @langDe Falls `false`, werden nur die Keys der abgerufenen Eigenschaften geloggt.
   * @default true
   * @since 4.8
   */
  @Default
  default boolean getIncludePropertyValues() {
    return true;
  }

  /**
   * @langEn Option to specify the operations for which a log entry should be created. The syntax is
   *     the same as for [Access Control](#access-control).
   * @langDe Option, um die Operationen anzugeben, für die ein Log-Eintrag erstellt werden soll. Die
   *     Syntax ist die gleiche wie für [Access Control](#access-control).
   * @default [data:read, write]
   * @since 4.8
   */
  @Default
  default Set<String> getOperations() {
    return Set.of("data:read", "write");
  }
}
