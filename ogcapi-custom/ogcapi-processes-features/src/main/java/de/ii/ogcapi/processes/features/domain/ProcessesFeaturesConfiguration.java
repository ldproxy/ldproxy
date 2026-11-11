/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock PROCESSES_FEATURES
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: PROCESSES_FEATURES
 *   enabled: true
 *   maximumFeaturesScanned: 200000
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROCESSES_FEATURES")
@JsonDeserialize(builder = ImmutableProcessesFeaturesConfiguration.Builder.class)
public interface ProcessesFeaturesConfiguration extends ExtensionConfiguration {

  /**
   * @langEn The maximum number of features that one execution of a process reads. An execution that
   *     selects more features fails with a client error, the request needs a narrower selection.
   * @langDe Die maximale Anzahl an Features, die eine Ausführung eines Prozesses liest. Eine
   *     Ausführung, die mehr Features auswählt, schlägt mit einem Client-Fehler fehl, die Anfrage
   *     benötigt eine engere Auswahl.
   * @default 100000
   * @since v5.0
   */
  @Nullable
  Integer getMaximumFeaturesScanned();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableProcessesFeaturesConfiguration.Builder();
  }
}
