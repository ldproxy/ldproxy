/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock TRANSACTIONS
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: TRANSACTIONS
 *   enabled: true
 *   atomic: true
 *   batch: true
 *   wfsTransaction: false
 *   defaultSemantic: ATOMIC
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "TRANSACTIONS")
@JsonDeserialize(builder = ImmutableTransactionsConfiguration.Builder.class)
public interface TransactionsConfiguration extends ExtensionConfiguration {

  enum Semantic {
    ATOMIC,
    BATCH
  }

  /**
   * @langEn Advertises the conformance class for atomic transactions. When enabled, requests
   *     without a `semantic` parameter or with `semantic=atomic` are executed as a single database
   *     transaction; any failure rolls back the whole request.
   * @langDe Schaltet die Konformitätsklasse für atomare Transaktionen frei. Anfragen ohne
   *     `semantic`-Parameter oder mit `semantic=atomic` werden in einer einzigen
   *     Datenbanktransaktion ausgeführt; jeder Fehler führt zum Rollback der gesamten Anfrage.
   * @default true
   * @since v4.5
   */
  @Nullable
  Boolean getAtomic();

  /**
   * @langEn Advertises the conformance class for batch transactions. When enabled, requests with
   *     `semantic=batch` execute each action independently and report per-action results, including
   *     exceptions for failures.
   * @langDe Schaltet die Konformitätsklasse für Batch-Transaktionen frei. Anfragen mit
   *     `semantic=batch` führen jede Aktion unabhängig aus und melden Ergebnisse pro Aktion, inkl.
   *     Ausnahmen bei Fehlern.
   * @default true
   * @since v4.5
   */
  @Nullable
  Boolean getBatch();

  /**
   * @langEn Enables decoding of WFS 2.0 `wfs:Transaction` XML payloads at `POST /transactions`.
   *     Only valid when `atomic` is `true` and the GML building block is enabled. Used to receive
   *     NBA messages per GeoInfoDok.
   * @langDe Aktiviert die Dekodierung von WFS 2.0 `wfs:Transaction`-XML-Payloads bei `POST
   *     /transactions`. Nur zulässig, wenn `atomic` aktiv und der GML-Baustein aktiviert ist. Wird
   *     für den Empfang von NBA-Nachrichten gemäß GeoInfoDok verwendet.
   * @default false
   * @since v4.5
   */
  @Nullable
  Boolean getWfsTransaction();

  /**
   * @langEn Semantic applied to requests that do not specify one. The default matches the OGC API
   *     Features Part 11 specification default.
   * @langDe Semantik, die für Anfragen ohne explizite Angabe verwendet wird. Der Standard
   *     entspricht der Vorgabe aus OGC API Features Part 11.
   * @default ATOMIC
   * @since v4.5
   */
  @Nullable
  Semantic getDefaultSemantic();

  /**
   * @langEn Optional upper bound on the number of actions per request. A value of zero or `null`
   *     disables the limit.
   * @langDe Optionale Obergrenze für die Anzahl der Aktionen pro Anfrage. Der Wert `0` oder `null`
   *     deaktiviert die Begrenzung.
   * @default 0
   * @since v4.5
   */
  @Nullable
  Integer getMaxActionsPerRequest();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableTransactionsConfiguration.Builder();
  }
}
