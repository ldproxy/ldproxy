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
 *   - Filters are restricted to selecting features by id. JSON action bodies accept only a CQL2
 *     `id IN (...)` expression (the `_ID_` placeholder); other CQL2 predicates (comparison,
 *     spatial, temporal, or boolean operators on regular properties) are rejected with a 4xx
 *     error.
 *   - `wfs:Transaction` XML accepts only `fes:ResourceId/@rid`; no other `fes:Filter` element
 *     types are recognised.
 * - `update` semantics:
 *   - Updates are applied as an RFC 7396 JSON Merge Patch over a GeoJSON representation of the
 *     current feature, regardless of the payload encoding.
 *   - In an atomic transaction, an `update` cannot target a feature that was inserted, replaced,
 *     updated, or deleted earlier in the same transaction; the action is rejected up front,
 *     because the read inside the `update` goes through the provider's normal query path and
 *     cannot see the transaction's still-uncommitted writes.
 *   - In a `wfs:Update`, the `wfs:ValueReference` of each `wfs:Property` must be the GeoJSON
 *     property name (the wire name), not the underlying GML element or schema name; an unknown
 *     name is silently ignored and the property is not changed.
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
 *     akzeptieren nur einen CQL2-Ausdruck der Form `id IN (...)` (Platzhalter `_ID_`); andere
 *     CQL2-Prädikate (Vergleichs-, Raum-, Zeit- oder boolesche Operatoren auf normalen
 *     Eigenschaften) werden mit einem 4xx-Fehler abgelehnt.
 *   - `wfs:Transaction`-XML akzeptiert ausschließlich `fes:ResourceId/@rid`; andere
 *     `fes:Filter`-Elementtypen werden nicht erkannt.
 * - Semantik von `update`:
 *   - Updates werden als RFC 7396 JSON Merge Patch über einer GeoJSON-Repräsentation der
 *     aktuellen Objektinstanz angewendet, unabhängig von der Payload-Kodierung.
 *   - In einer atomaren Transaktion darf ein `update` keine Objektinstanz betreffen, die im
 *     selben Vorgang bereits eingefügt, ersetzt, aktualisiert oder gelöscht wurde; die Aktion
 *     wird vorab abgewiesen, da der Lesezugriff innerhalb eines `update` über den normalen
 *     Abfragepfad des Providers geht und noch nicht festgeschriebene Schreibvorgänge derselben
 *     Transaktion nicht sehen kann.
 *   - In einem `wfs:Update` muss `wfs:ValueReference` jeder `wfs:Property` den GeoJSON-Namen
 *     der Eigenschaft (den Wire-Namen) verwenden, nicht den zugrundeliegenden GML-Element-
 *     oder Schemanamen; ein unbekannter Name wird stillschweigend ignoriert und die
 *     Eigenschaft bleibt unverändert.
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
