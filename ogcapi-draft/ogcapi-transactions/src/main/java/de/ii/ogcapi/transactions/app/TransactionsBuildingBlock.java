/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.transactions.domain.ImmutableTransactionsConfiguration.Builder;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration.Semantic;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Transactions
 * @langEn Atomic and batch transactions over multiple feature collections.
 * @langDe Atomare und gestapelte Transaktionen über mehrere Objektarten.
 * @scopeEn Provides a single resource `POST /transactions` that accepts a transaction document
 *     describing one or more `insert`, `replace`, `update` or `delete` actions across one or more
 *     feature collections of the API. The canonical request encoding is `application/ogc-tx+json`;
 *     the response is `application/json`.
 *     <p>Two semantics are supported: atomic transactions execute all actions as a single database
 *     transaction (rollback on failure), batch transactions execute each action independently and
 *     report per-action results.
 *     <p>For interoperability with WFS 2.0 producers the building block can additionally accept
 *     `wfs:Transaction` XML payloads in atomic mode.
 *     <p>The `Prefer` request header is honoured per RFC 7240: `return=representation` (the
 *     default), `return=minimal`, or `return=none` controls how much of the transaction response is
 *     returned; `handling=strict` validates every `insert` and `replace` payload against the
 *     feature-type schema before any provider write (this only takes effect when the collection has
 *     schema validation configured — JSON Schema for GeoJSON payloads, XSD for GML payloads).
 *     `respond-async` is rejected with `501`.
 *     <p>The `Content-Crs` request header declares the CRS of coordinates in the request body. When
 *     omitted, the API's default CRS (CRS84 unless overridden) is assumed; values that are
 *     syntactically invalid or name a CRS not in the API's supported list are rejected with a `4xx`
 *     error.
 *     <p>Both successful and atomic-failed responses use the same Part 11 *Transaction Response*
 *     document shape (with `summary`, the per-action result arrays, and an `exceptions[]` array
 *     when applicable), so clients do not need to branch on the response status to parse the body.
 * @scopeDe Stellt eine einzelne Ressource `POST /transactions` bereit, die ein Transaktionsdokument
 *     mit einer oder mehreren `insert`-, `replace`-, `update`- oder `delete`-Aktionen über eine
 *     oder mehrere Objektarten der API entgegennimmt. Die kanonische Anfragecodierung ist
 *     `application/ogc-tx+json`; die Antwort ist `application/json`.
 *     <p>Es werden zwei Semantiken unterstützt: atomare Transaktionen führen alle Aktionen in einer
 *     einzigen Datenbanktransaktion aus (Rollback bei Fehlern), Batch-Transaktionen führen jede
 *     Aktion unabhängig aus und liefern Ergebnisse pro Aktion.
 *     <p>Zur Interoperabilität mit WFS 2.0-Produzenten kann der Baustein zusätzlich
 *     `wfs:Transaction`-XML-Payloads im atomaren Modus entgegennehmen.
 *     <p>Der Anfrage-Header `Prefer` wird gemäß RFC 7240 ausgewertet: `return=representation`
 *     (Standard), `return=minimal` oder `return=none` steuert den Umfang der Transaktionsantwort;
 *     `handling=strict` validiert jeden `insert`- und `replace`-Payload vor dem Schreiben gegen das
 *     Schema der Objektart (greift nur, wenn die Objektart eine Schema-Validierung konfiguriert hat
 *     — JSON Schema für GeoJSON-Payloads, XSD für GML-Payloads). `respond-async` wird mit `501`
 *     abgelehnt.
 *     <p>Der Anfrage-Header `Content-Crs` gibt das CRS der Koordinaten im Anfragetext an. Fehlt der
 *     Header, wird das Standard-CRS der API angenommen (CRS84, sofern nicht überschrieben);
 *     syntaktisch ungültige Werte oder ein nicht von der API unterstütztes CRS werden mit einem
 *     `4xx`-Fehler abgelehnt.
 *     <p>Erfolgreiche und atomar fehlgeschlagene Antworten verwenden dieselbe Dokumentstruktur des
 *     Part-11-*Transaction-Response* (mit `summary`, den pro Aktion gegliederten Ergebnis-Arrays
 *     und gegebenenfalls einem `exceptions[]`-Array), so dass Clients zum Auswerten der Antwort
 *     nicht nach Statuscode unterscheiden müssen.
 * @limitationsEn The following restrictions apply:
 *     <p><code>
 * - Provider and request scope:
 *   - Only feature types from an SQL feature provider that supports mutations
 *     (`datasetChanges.mode` `CRUD`) are supported.
 *   - Asynchronous transactions are not supported.
 *   - Responses are always JSON; the WFS `wfs:TransactionResponse` XML response is not produced.
 *   - `wfs:Transaction` XML payloads are accepted in atomic mode only.
 * - Filters in `update`, `replace`, and `delete` actions:
 *   - Filters are restricted to selecting features by id. JSON action bodies accept either a
 *     CQL2 `IN(<id-property>, [...])` or `<id-property> = ...` expression, where
 *     `<id-property>` is the schema name (or alias, if declared) of the feature type's
 *     property with role `ID`; other CQL2 predicates (comparison, spatial, temporal, or
 *     boolean operators on regular properties) are rejected with a 4xx error.
 *   - `wfs:Transaction` XML accepts only `fes:ResourceId/@rid`; no other `fes:Filter` element
 *     types are recognised.
 * - `update` semantics:
 *   - An `update` action applies property-level partial updates to a single existing feature in
 *     place: each touched property is translated into a native SQL `UPDATE` (for columns on the
 *     feature's main table, including geometries) or a `DELETE`-then-`INSERT` against the
 *     associated child table (for `VALUE_ARRAY` and `OBJECT_ARRAY` properties). All statements
 *     run on the same session connection as the rest of the transaction, so an `update` can
 *     target a feature that was `insert`ed, `replace`d, `update`d, or whose junction rows were
 *     touched earlier in the same atomic request.
 *   - The set of properties that may be the target of a partial update is governed by
 *     `updatableProperties` on the collection's TRANSACTIONS configuration; a property whose
 *     canonical path is not in that list is rejected with a 4xx error. The whitelist defaults
 *     to an empty list, which disables partial updates for the collection. Entries are the
 *     schema property identifiers, joined by `.` (e.g. `lifetime.end` for a nested property,
 *     `name` for a top-level scalar).
 *   - Property paths are resolved against the feature schema and the input format's `useAlias`
 *     setting. JSON-transaction `name` and `delete` paths use the canonical dotted form (the
 *     `.` separator; XPath-style `/` is reserved for `wfs:ValueReference`). A `wfs:Update`'s
 *     `wfs:ValueReference` follows the XPath form and must include the intermediate
 *     object-type element (e.g. `lifetime/Lifetime/end`); XML namespace prefixes are stripped,
 *     only local names and the path structure are significant.
 *   - When the filter selects more than one id (e.g. `IN(id, ['a','b'])` or multiple
 *     `fes:ResourceId/@rid`), the same patch is applied to every selected feature.
 *   - An absent value (a `<wfs:Property>` with no `<wfs:Value>`, or a JSON-tx `delete` entry)
 *     clears the property: SQL `NULL` for a column on the main table, or an empty result for
 *     a `VALUE_ARRAY` / `OBJECT_ARRAY` (all junction rows for that property are removed and
 *     none are re-inserted).
 *   - Geometries: a `wfs:Update` may set a geometry property by placing the corresponding GML
 *     geometry element (e.g. `<gml:Point>`) inside `<wfs:Value>`. A JSON-transaction update
 *     uses a GeoJSON geometry object as the property value. Both forms re-use the same
 *     CRS-aware encoder as `insert` / `replace`. The geometry property must live on the
 *     feature's main table; geometry on a child table is not supported.
 *   - Value arrays (`VALUE_ARRAY`): multiple sibling `<wfs:Property>` elements with the same
 *     `wfs:ValueReference` collapse into one array on the WFS side (each value contributes
 *     one element). A JSON-transaction update uses a JSON array of scalars. The whole array
 *     is replaced.
 *   - Object arrays (`OBJECT_ARRAY`): a `wfs:Update` sets an `OBJECT_ARRAY` property by
 *     placing one or more object-type wrapper elements inside `<wfs:Value>` (one per array
 *     element); a JSON-transaction update uses a JSON array of objects. Object keys / child
 *     element local names are matched against the array's schema in the same way as scalar
 *     paths (id vs alias per `useAlias`); unknown keys are silently ignored. The whole array
 *     is replaced. Nested object children inside an array element, M:N junctions, and
 *     `FEATURE_REF` arrays are not yet supported.
 *     </code>
 * @limitationsDe Es gelten die folgenden Einschränkungen:
 *     <p><code>
 * - Provider und Anfragebereich:
 *   - Es werden nur Objektarten von einem SQL-Feature-Provider unterstützt, der Mutationen
 *     erlaubt (`datasetChanges.mode` `CRUD`).
 *   - Asynchrone Transaktionen werden nicht unterstützt.
 *   - Antworten werden ausschließlich als JSON geliefert; die WFS
 *     `wfs:TransactionResponse`-XML-Antwort wird nicht erzeugt.
 *   - `wfs:Transaction`-XML-Payloads werden nur im atomaren Modus akzeptiert.
 * - Filter in `update`-, `replace`- und `delete`-Aktionen:
 *   - Filter sind auf die Auswahl von Objekten über deren Id beschränkt. JSON-Aktionen
 *     akzeptieren entweder einen CQL2-Ausdruck der Form `IN(<id-eigenschaft>, [...])` oder
 *     `<id-eigenschaft> = ...`, wobei `<id-eigenschaft>` der Schemaname (bzw. Alias, falls
 *     deklariert) der Eigenschaft der Objektart mit Rolle `ID` ist; andere CQL2-Prädikate
 *     (Vergleichs-, Raum-, Zeit- oder boolesche Operatoren auf normalen Eigenschaften)
 *     werden mit einem 4xx-Fehler abgelehnt.
 *   - `wfs:Transaction`-XML akzeptiert ausschließlich `fes:ResourceId/@rid`; andere
 *     `fes:Filter`-Elementtypen werden nicht erkannt.
 * - Semantik von `update`:
 *   - Eine `update`-Aktion führt eine eigenschaftsbezogene Teilaktualisierung an einem
 *     vorhandenen Objekt in situ durch: jede angesprochene Eigenschaft wird in ein natives
 *     SQL-`UPDATE` (für Spalten der Haupttabelle, einschließlich Geometrien) bzw. in ein
 *     `DELETE`-mit-anschließendem-`INSERT` gegen die zugehörige Kindtabelle (für
 *     `VALUE_ARRAY`- und `OBJECT_ARRAY`-Eigenschaften) übersetzt. Alle Anweisungen laufen auf
 *     derselben Sitzungs-Verbindung wie der Rest der Transaktion, so dass ein `update` ein
 *     Objekt verändern kann, das zuvor in derselben atomaren Anfrage per `insert`,
 *     `replace`, `update` oder einer Junction-Mutation berührt wurde.
 *   - Die Menge der Eigenschaften, die per Teilaktualisierung geändert werden dürfen, wird
 *     durch `updatableProperties` in der TRANSACTIONS-Konfiguration der Sammlung festgelegt;
 *     eine Eigenschaft, deren kanonischer Pfad nicht in dieser Liste enthalten ist, wird mit
 *     einem 4xx-Fehler abgelehnt. Die Standard-Whitelist ist leer und deaktiviert
 *     Teil-Aktualisierungen für die Sammlung. Einträge sind die Schemabezeichner der
 *     Eigenschaften, getrennt durch `.` (z.B. `lifetime.end` für eine verschachtelte Eigenschaft,
 *     `name` für eine Eigenschaft der obersten Ebene).
 *   - Eigenschaftspfade werden gegen das Feature-Schema und das `useAlias`-Verhalten des
 *     Eingabeformats aufgelöst. Pfade in `name` und `delete` einer JSON-Transaktion
 *     verwenden die kanonische Punktnotation (Trenner `.`; das XPath-Schrägstrich-Format ist
 *     `wfs:ValueReference` vorbehalten). Ein `wfs:Update` verwendet in `wfs:ValueReference`
 *     die XPath-Form einschließlich des Objekttyp-Zwischenschritts (z.B.
 *     `lifetime/Lifetime/end`); XML-Namensraum-Präfixe werden entfernt, nur lokale Namen und
 *     die Pfadstruktur sind relevant.
 *   - Wählt der Filter mehrere Ids aus (z.B. `IN(id, ['a','b'])` oder mehrere
 *     `fes:ResourceId/@rid`), wird derselbe Patch auf jede ausgewählte Objektinstanz
 *     angewendet.
 *   - Ein fehlender Wert (`<wfs:Property>` ohne `<wfs:Value>` oder ein Eintrag in `delete`
 *     einer JSON-Transaktion) leert die Eigenschaft: SQL-`NULL` für eine Spalte der
 *     Haupttabelle bzw. ein leeres Ergebnis für `VALUE_ARRAY` / `OBJECT_ARRAY` (alle
 *     Junction-Zeilen für die Eigenschaft werden entfernt, keine neuen eingefügt).
 *   - Geometrien: ein `wfs:Update` kann eine Geometrieeigenschaft setzen, indem das
 *     entsprechende GML-Geometrieelement (z.B. `<gml:Point>`) in `<wfs:Value>` platziert
 *     wird. Eine JSON-Transaktion verwendet ein GeoJSON-Geometrieobjekt als
 *     Eigenschaftswert. Beide Formen nutzen denselben CRS-bewussten Encoder wie `insert` /
 *     `replace`. Die Geometrieeigenschaft muss in der Haupttabelle der Objektart liegen;
 *     Geometrien in Kindtabellen werden nicht unterstützt.
 *   - Wert-Arrays (`VALUE_ARRAY`): mehrere geschwisterliche `<wfs:Property>`-Elemente mit
 *     derselben `wfs:ValueReference` werden auf der WFS-Seite zu einem Array zusammengefasst
 *     (jeder Wert liefert ein Element). Eine JSON-Transaktion verwendet ein JSON-Array von
 *     Skalaren. Das gesamte Array wird ersetzt.
 *   - Objekt-Arrays (`OBJECT_ARRAY`): ein `wfs:Update` setzt eine `OBJECT_ARRAY`-Eigenschaft,
 *     indem ein oder mehrere objekttyp-Wrapperelemente in `<wfs:Value>` platziert werden
 *     (eines pro Array-Element); eine JSON-Transaktion verwendet ein JSON-Array von
 *     Objekten. Objektschlüssel bzw. die lokalen Namen der Kind-Elemente werden gegen das
 *     Schema des Arrays auf dieselbe Weise wie skalare Pfade aufgelöst (id oder Alias gemäß
 *     `useAlias`); unbekannte Schlüssel werden stillschweigend ignoriert. Das gesamte Array
 *     wird ersetzt. Verschachtelte Objekt-Kinder innerhalb eines Array-Elements,
 *     M:N-Junctions und `FEATURE_REF`-Arrays werden noch nicht unterstützt.
 *     </code>
 * @conformanceEn The building block is based on the conformance classes "Transactions", "Atomic
 *     Semantics", "Batch Semantics", "JSON Encoding" and "Features" from the [Draft OGC API -
 *     Features - Part 11: Atomic and Batch Transactions](https://docs.ogc.org/DRAFTS/26-018.html).
 * @conformanceDe Der Baustein basiert auf den Konformitätsklassen "Transactions", "Atomic
 *     Semantics", "Batch Semantics", "JSON Encoding" und "Features" aus dem [Entwurf von OGC API -
 *     Features - Part 11: Atomic and Batch Transactions](https://docs.ogc.org/DRAFTS/26-018.html).
 * @ref:cfg {@link de.ii.ogcapi.transactions.domain.TransactionsConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.transactions.domain.ImmutableTransactionsConfiguration}
 */
@Singleton
@AutoBind
public class TransactionsBuildingBlock implements ApiBuildingBlock {

  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionsBuildingBlock.class);

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/26-018.html",
              "OGC API - Features - Part 11: Atomic and Batch Transactions (DRAFT)"));

  private final FeaturesCoreProviders providers;

  @Inject
  public TransactionsBuildingBlock(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return ApiBuildingBlock.super.isEnabledForApi(apiData) && isProviderSupportsMutations(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return ApiBuildingBlock.super.isEnabledForApi(apiData, collectionId)
        && isProviderSupportsMutations(apiData);
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder()
        .enabled(false)
        .atomic(true)
        .batch(true)
        .wfsTransaction(false)
        .defaultSemantic(Semantic.ATOMIC)
        .maxActionsPerRequest(0)
        .build();
  }

  private boolean isProviderSupportsMutations(OgcApiDataV2 apiData) {
    return providers
        .getFeatureProvider(apiData)
        .filter(
            provider -> {
              if (!provider.mutations().isSupported()) {
                LOGGER.warn(
                    "Disabling building block TRANSACTIONS, feature provider with id '{}' does not support mutations: datasetChanges.mode is not 'CRUD'",
                    provider.getId());
                return false;
              }
              return true;
            })
        .isPresent();
  }
}
