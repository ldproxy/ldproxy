/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.List;
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
 *   collectErrors: true
 * ```
 *
 * Per-collection partial-update whitelist (allows `wfs:Update` / JSON-transaction `update`
 * actions to set the listed scalar/datetime/geometry/VALUE_ARRAY/OBJECT_ARRAY properties):
 *
 * ```yaml
 * collections:
 *   places:
 *     api:
 *       - buildingBlock: TRANSACTIONS
 *         updatableProperties:
 *           - location           # geometry on the main table
 *   buildings:
 *     api:
 *       - buildingBlock: TRANSACTIONS
 *         updatableProperties:
 *           - name               # top-level scalar
 *           - lifetime.end       # nested property
 *           - tags               # VALUE_ARRAY
 *           - addresses          # OBJECT_ARRAY
 * ```
 *
 * Transaction-lifecycle hooks (PostgreSQL/PostGIS providers). Statements are selected by the
 * request's `Prefer: handling` preference; a pre-commit error rolls the transaction back, a
 * pre-commit warning lets it commit and is returned in the response:
 *
 * ```yaml
 * - buildingBlock: TRANSACTIONS
 *   transactionSetup:
 *     always:
 *       - SET LOCAL myapp.geometry_validation.enabled = true
 *     strict:
 *       - SET LOCAL myapp.geometry_validation.error_if_invalid = true
 *     lenient:
 *       - SET LOCAL myapp.geometry_validation.error_if_invalid = false
 *   preCommit:
 *     always:
 *       - SELECT myapp.update_derived_data()
 *     strict:
 *       - SELECT myapp.assert_consistency()
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
   * @since v4.8
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
   * @since v4.8
   */
  @Nullable
  Boolean getBatch();

  /**
   * @langEn Enables decoding of WFS 2.0 `wfs:Transaction` XML payloads at `POST /transactions`.
   *     Only valid when `atomic` is `true` and the GML building block is enabled.
   * @langDe Aktiviert die Dekodierung von WFS 2.0 `wfs:Transaction`-XML-Payloads bei `POST
   *     /transactions`. Nur zulässig, wenn `atomic` aktiv und der GML-Baustein aktiviert ist.
   * @default false
   * @since v4.8
   */
  @Nullable
  Boolean getWfsTransaction();

  /**
   * @langEn Semantic applied to requests that do not specify one. The default matches the OGC API
   *     Features Part 11 specification default.
   * @langDe Semantik, die für Anfragen ohne explizite Angabe verwendet wird. Der Standard
   *     entspricht der Vorgabe aus OGC API Features Part 11.
   * @default ATOMIC
   * @since v4.8
   */
  @Nullable
  Semantic getDefaultSemantic();

  /**
   * @langEn Optional upper bound on the number of actions per request. A value of zero or `null`
   *     disables the limit.
   * @langDe Optionale Obergrenze für die Anzahl der Aktionen pro Anfrage. Der Wert `0` oder `null`
   *     deaktiviert die Begrenzung.
   * @default 0
   * @since v4.8
   */
  @Nullable
  Integer getMaxActionsPerRequest();

  /**
   * @langEn Only relevant for atomic semantics. When enabled, a failing action does not stop the
   *     processing of the transaction: the remaining actions are still executed, every error is
   *     collected, and the response reports all of them, while all changes are still rolled back.
   *     This lets clients fix every error in a large transaction in one pass instead of one error
   *     per attempt. Later actions see the changes of earlier successful actions, but not those of
   *     failed actions — an action that depends on a failed action therefore typically reports a
   *     follow-on error. Each action adds a small database overhead (a savepoint), which can be
   *     noticeable in very large transactions. Requires a SQL feature provider; otherwise the
   *     option is ignored. When disabled, processing stops at the first error, the remaining
   *     actions are skipped, and only the first error is reported.
   * @langDe Nur für atomare Semantik relevant. Wenn aktiviert, beendet eine fehlschlagende Aktion
   *     die Verarbeitung der Transaktion nicht: Die verbleibenden Aktionen werden trotzdem
   *     ausgeführt, alle Fehler werden gesammelt und in der Antwort gemeldet, während alle
   *     Änderungen dennoch zurückgerollt werden. Clients können so alle Fehler einer großen
   *     Transaktion in einem Durchgang beheben statt einen Fehler pro Versuch. Spätere Aktionen
   *     sehen die Änderungen früherer erfolgreicher Aktionen, nicht aber die fehlgeschlagener
   *     Aktionen — eine Aktion, die von einer fehlgeschlagenen Aktion abhängt, meldet daher in der
   *     Regel einen Folgefehler. Jede Aktion verursacht einen kleinen Mehraufwand in der Datenbank
   *     (einen Savepoint), der bei sehr großen Transaktionen spürbar sein kann. Erfordert einen
   *     SQL-Feature-Provider; andernfalls wird die Option ignoriert. Wenn deaktiviert, stoppt die
   *     Verarbeitung beim ersten Fehler, die verbleibenden Aktionen werden übersprungen und nur der
   *     erste Fehler wird gemeldet.
   * @default false
   * @since v4.8
   */
  @Nullable
  Boolean getCollectErrors();

  /**
   * @langEn Whitelist of feature properties that may be the target of a partial update (the
   *     `wfs:Update` action of a `wfs:Transaction`, or the JSON-transaction `update` action). Each
   *     entry is a property path written as the schema property identifiers joined by `.`, e.g.
   *     `lifetime.end` for a nested property or `name` for a top-level scalar. Path segments are
   *     always the canonical schema identifiers, independent of the input format's alias setting. A
   *     partial update whose property path is not in this list is rejected with a 4xx error.
   *     <p>Entries set at the API level apply to every collection; entries set on a specific
   *     collection are merged with the API-level list (duplicates de-duplicated). When the
   *     effective list for a collection is empty, partial updates are disabled for that collection.
   * @langDe Whitelist der Eigenschaften, die per Teil-Aktualisierung geändert werden dürfen
   *     (`wfs:Update`-Aktion einer `wfs:Transaction` bzw. `update`-Aktion einer JSON-Transaktion).
   *     Jeder Eintrag ist ein Eigenschaftspfad, dessen Segmente die kanonischen Schemabezeichner
   *     sind und durch `.` verbunden werden, z.B. `lifetime.end` für eine verschachtelte
   *     Eigenschaft oder `name` für eine Eigenschaft der obersten Ebene. Die Segmente verwenden
   *     immer die Schemabezeichner, unabhängig davon, ob für das Eingabeformat Aliase aktiviert
   *     sind. Eine Teil-Aktualisierung mit einem Pfad, der nicht in dieser Liste enthalten ist,
   *     wird mit einem 4xx-Fehler abgewiesen.
   *     <p>Einträge auf API-Ebene gelten für jede Sammlung; Einträge auf einer Sammlung werden mit
   *     der API-Ebene zusammengeführt (Duplikate werden entfernt). Ist die effektive Liste für eine
   *     Sammlung leer, sind Teil-Aktualisierungen für diese Sammlung deaktiviert.
   * @default []
   * @since v4.8
   */
  List<String> getUpdatableProperties();

  /**
   * @langEn SQL statements executed on the database transaction right after it begins, before any
   *     action runs. Typical uses are transaction-scoped settings (`SET LOCAL …`) or calling a
   *     setup function. Each entry is a single statement run verbatim, in order, on the provider's
   *     transaction connection (PostgreSQL/PostGIS feature providers only). The `always`, `strict`
   *     and `lenient` lists select which statements run depending on the request's `Prefer:
   *     handling` preference. A statement that fails aborts the transaction.
   * @langDe SQL-Anweisungen, die auf der Datenbanktransaktion direkt nach deren Beginn ausgeführt
   *     werden, bevor eine Aktion läuft. Typische Anwendungen sind transaktionsbezogene
   *     Einstellungen (`SET LOCAL …`) oder der Aufruf einer Setup-Funktion. Jeder Eintrag ist eine
   *     einzelne Anweisung, die unverändert und in der angegebenen Reihenfolge auf der
   *     Transaktionsverbindung des Providers ausgeführt wird (nur PostgreSQL/PostGIS-Provider). Die
   *     Listen `always`, `strict` und `lenient` legen abhängig von der `Prefer: handling`-Präferenz
   *     der Anfrage fest, welche Anweisungen laufen. Eine fehlschlagende Anweisung bricht die
   *     Transaktion ab.
   * @default null
   * @since v4.8
   */
  @Nullable
  TransactionHooks getTransactionSetup();

  /**
   * @langEn SQL statements executed on the database transaction right before it is committed.
   *     Typical uses are calling functions that update derived data or verify consistency. Each
   *     entry is a single statement run verbatim, in order, on the provider's transaction
   *     connection (PostgreSQL/PostGIS feature providers only). The `always`, `strict` and
   *     `lenient` lists select which statements run depending on the request's `Prefer: handling`
   *     preference. A statement that raises an error aborts the transaction and rolls it back; a
   *     statement that only emits a warning (`RAISE WARNING` / `RAISE NOTICE`) lets the transaction
   *     commit and the warning is returned in the response. The error and warning text is relayed
   *     verbatim into the response (`exceptions` and `warnings`), and the statements run at the
   *     database level without access to the request actions, so write `RAISE` messages that
   *     identify the affected feature(s) — e.g. include the feature identifier — to make the
   *     response actionable.
   * @langDe SQL-Anweisungen, die auf der Datenbanktransaktion direkt vor dem Commit ausgeführt
   *     werden. Typische Anwendungen sind Funktionen, die abgeleitete Daten aktualisieren oder die
   *     Konsistenz prüfen. Jeder Eintrag ist eine einzelne Anweisung, die unverändert und in der
   *     angegebenen Reihenfolge auf der Transaktionsverbindung des Providers ausgeführt wird (nur
   *     PostgreSQL/PostGIS-Provider). Die Listen `always`, `strict` und `lenient` legen abhängig
   *     von der `Prefer: handling`-Präferenz der Anfrage fest, welche Anweisungen laufen. Eine
   *     Anweisung, die einen Fehler auslöst, bricht die Transaktion ab und macht sie rückgängig;
   *     eine Anweisung, die nur eine Warnung ausgibt (`RAISE WARNING` / `RAISE NOTICE`), lässt die
   *     Transaktion committen, und die Warnung wird in der Antwort zurückgegeben. Der Fehler- bzw.
   *     Warnungstext wird unverändert in die Antwort übernommen (`exceptions` und `warnings`), und
   *     die Anweisungen laufen auf Datenbankebene ohne Zugriff auf die Aktionen der Anfrage. Daher
   *     sollten `RAISE`-Meldungen die betroffenen Objekte benennen — z.B. den Objektidentifikator
   *     enthalten —, damit die Antwort auswertbar ist.
   * @default null
   * @since v4.8
   */
  @Nullable
  TransactionHooks getPreCommit();

  @Value.Derived
  @Value.Auxiliary
  default List<List<String>> getUpdatablePropertyPaths() {
    return getUpdatableProperties().stream()
        .map(TransactionsConfiguration::splitPath)
        .collect(ImmutableList.toImmutableList());
  }

  // The ldproxy canonical separator is `.`; `/` is reserved for wfs:ValueReference XPath input
  // and is parsed elsewhere.
  static List<String> splitPath(String raw) {
    String trimmed = raw == null ? "" : raw.trim();
    if (trimmed.isEmpty()) {
      return List.of();
    }
    return ImmutableList.copyOf(trimmed.split("\\."));
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableTransactionsConfiguration.Builder();
  }

  // List fields default to "replace on merge" — override so a per-collection
  // `updatableProperties` entry adds to (rather than replaces) the API-level default.
  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    TransactionsConfiguration src = (TransactionsConfiguration) source;
    return new ImmutableTransactionsConfiguration.Builder()
        .from(source)
        .from(this)
        .updatableProperties(
            java.util.stream.Stream.concat(
                    src.getUpdatableProperties().stream(), getUpdatableProperties().stream())
                .distinct()
                .toList())
        .build();
  }
}
