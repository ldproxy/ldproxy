/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import javax.annotation.Nullable;

public interface AliasConfiguration {

  /**
   * @langEn If `true`, properties of the feature schema that declare an `alias` are encoded under
   *     their alias instead of their schema name. Useful for application schemas where each
   *     property carries both a short technical name and a longer mnemonic name (e.g. AdV NAS:
   *     `arf` / `artDerFlurstuecksgrenze`). An explicit `rename` transformation on a property still
   *     takes precedence over the alias. The flag only affects feature encoding output; queryables,
   *     sortables and other schema-derivation paths always use the schema names.
   * @langDe Wenn `true`, werden Eigenschaften des Feature-Schemas, für die ein `alias` angegeben
   *     ist, unter dem Alias anstelle des Schemanamens kodiert. Hilfreich für Anwendungsschemata,
   *     in denen jede Eigenschaft sowohl einen kurzen technischen Namen als auch einen längeren
   *     eingängigen Namen hat (z.B. AdV-NAS: `arf` / `artDerFlurstuecksgrenze`). Eine explizite
   *     `rename`-Transformation für eine Eigenschaft hat weiterhin Vorrang vor dem Alias. Die
   *     Option wirkt nur auf die Feature-Kodierung; Queryables, Sortables und andere
   *     Schema-Ableitungen verwenden immer die Schemanamen.
   * @default false
   */
  @Nullable
  Boolean getUseAlias();

  default boolean isUseAlias() {
    return Boolean.TRUE.equals(getUseAlias());
  }

  default AliasConfiguration mergeInto(AliasConfiguration source) {
    Boolean merged = getUseAlias() != null ? getUseAlias() : source.getUseAlias();
    return () -> merged;
  }
}
