/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.crud.domain.ImmutableCrudConfiguration.Builder;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.SchemaBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title CRUD
 * @langEn Create, replace, update and delete features.
 * @langDe Erzeugen, Ersetzen, Aktualisieren und Löschen von Features.
 * @scopeEn New or updated features can be submitted in GeoJSON, JSON-FG or GML.
 *     <p>In POST and PUT requests, the "Content-Type" header must be set to "application/geo+json"
 *     for GeoJSON and JSON-FG, or to "application/gml+xml" for GML. PATCH is only supported for
 *     "application/merge-patch+json" and "application/geo+json"; PATCH with "application/gml+xml"
 *     is rejected with HTTP 415 ("Unsupported Media Type").
 *     <p>In PATCH requests, the "Content-Type" header must be set to
 *     "application/merge-patch+json". The payload should include only the changed values (in the
 *     "geometry"/"place" and "properties" members). See [RFC 7396 (JSON Merge
 *     Patch)](https://www.rfc-editor.org/rfc/rfc7396) for details.
 *     <p>The GML payload must be a single bare feature element at the document root. Multi-feature
 *     bodies (e.g. "wfs:FeatureCollection") are rejected with HTTP 400. The decoder honours the
 *     options of the GML building block (alias resolution, value-wrapping, namespace qualification,
 *     codelist URI templates, srsName mappings, uom mappings) symmetrically with the encoder; see
 *     the GML building block for the full list and the per-option direction notes. Structural
 *     validation is limited to the token stream - schema-aware checks against the collection's
 *     feature schema happen downstream as for the JSON formats.
 *     <p>For GML, the coordinate reference system of each geometry is resolved in this order: the
 *     geometry's own "srsName" attribute, the "OGC-Content-Crs" request header, the collection's
 *     storage CRS. A single feature must not mix coordinate reference systems across its
 *     geometries; such a request is rejected with HTTP 400. ADV URN forms on "srsName" (e.g.
 *     "urn:adv:crs:ETRS89_UTM32") are resolved via the GML building block's "srsNameMappings"
 *     option.
 *     <p>The properties must be in the Receivables representation, that is, according to the schema
 *     of the collection excluding properties marked as `readOnly`. For feature references, the
 *     receivables representation is a JSON object with three properties (`id`, the foreign key,
 *     `title`, a descriptive text, and `type`, the name of the type in the feature schema). To
 *     update an existing feature, the current feature to be edited can be requested in the profile
 *     `all-as-receivable`.
 *     <p>If a new or updated feature includes a geometry, the URI of the coordinate reference
 *     system must be represented in the "Content-Crs" header of the request. To avoid coordinate
 *     transformations, the geometry should be in the storage CRS. Only the coordinate reference
 *     systems of the collection (see the `crs` member of the collection) may be used, in the
 *     "Content-Crs" header as well as in the request body (a JSON-FG "coordRefSys" member, a GML
 *     "srsName" attribute); any other coordinate reference system is rejected with HTTP 400.
 *     <p>Features may only have a single geometry property with scope `RECEIVABLES`. The geometry
 *     has to be represented in the "geometry" or "place" member depending on the format (with or
 *     without the JSON-FG extensions), the coordinate reference system and the geometry type (see
 *     JSON-FG for details when "place" has to be used).
 *     <p>A new feature can be created in two ways, depending on the specification of the
 *     `featureId`. If the `featureId` is generated and assigned by ldproxy during the creation,
 *     POST on `{landingPage}/collections/{collectionId}/items` is used and the URI of the generated
 *     feature is in the "Location" header of the response. If the feature ID is assigned by the
 *     client, PUT on `{landingPage}/collections/{collectionId}/items/{featureId}` has to be used.
 *     Clients can determine the behavior from the OpenAPI document or by inspecting the
 *     `supportsNonAutogeneratedResourceIds` field in the collection. An "id" member in a feature
 *     submitted in a POST or PUT request is ignored; a PATCH request that would change the
 *     identifier of the feature is rejected with HTTP 400, since the feature is identified by the
 *     URI of the resource.
 *     <p>To validate a new or updated feature submitted in a POST or PUT request against the schema
 *     of the collection (GeoJSON/JSON-GF) or the referenced XML Schemas (GML), a header `Prefer`
 *     with the value "handling=strict" can be added to the request. If the validation fails, an
 *     error is returned and the response reports the applied preference in a "Preference-Applied"
 *     header. The validation requires a schema: the building block SCHEMA_VALIDATION for GeoJSON
 *     and JSON-FG, the option "schemaLocations" of the GML building block for GML. Without it, the
 *     request body is not validated and the preference is ignored.
 *     <p>A PUT, PATCH or DELETE request cannot be made conditional on an entity tag: no entity tag
 *     is known for the feature in a request that changes it, so an "If-Match" header cannot be met
 *     and the request is rejected with HTTP 412 ("Precondition Failed"). Use an
 *     "If-Unmodified-Since" header instead (see the option `optimisticLockingLastModified`).
 *     <p>If the feature in a POST or PUT request is GeoJSON without the JSON-FG extensions, include
 *     a header `Link` with the value "<http://www.opengis.net/def/profile/OGC/0/rfc7946>;
 *     rel=profile" in the request. For a feature with the JSON-FG extensions, use the value
 *     "<http://www.opengis.net/def/profile/OGC/0/jsonfg>; rel=profile".
 * @scopeDe Neue oder aktualisierte Features können in GeoJSON, JSON-FG oder GML übermittelt werden.
 *     <p>In POST- und PUT-Anfragen muss der Header "Content-Type" für GeoJSON und JSON-FG auf
 *     "application/geo+json" gesetzt sein, für GML auf "application/gml+xml". PATCH wird nur für
 *     "application/merge-patch+json" und "application/geo+json" unterstützt; PATCH mit
 *     "application/gml+xml" wird mit HTTP 415 ("Unsupported Media Type") abgelehnt.
 *     <p>In PATCH-Anfragen muss der Header "Content-Type" auf "application/merge-patch+json"
 *     gesetzt sein. Der Payload sollte nur die geänderten Werte (in den Eigenschaften
 *     "geometry"/"place" und "properties") enthalten. Details dazu finden sich in [RFC 7396 (JSON
 *     Merge Patch)](https://www.rfc-editor.org/rfc/rfc7396).
 *     <p>Der GML-Payload muss aus einem einzelnen Feature-Element auf der obersten Ebene des
 *     Dokuments bestehen. Bodies mit mehreren Features (z.B. "wfs:FeatureCollection") werden mit
 *     HTTP 400 abgelehnt. Der Decoder unterstützt die Optionen des GML-Bausteins (Alias-Auflösung,
 *     Value-Wrapping, Namespace-Qualifizierung, Codelist-URI-Templates, srsName-Mappings,
 *     UoM-Mappings) symmetrisch zum Encoder; die vollständige Liste mit den Hinweisen pro Option
 *     findet sich im GML-Baustein. Die strukturelle Validierung beschränkt sich auf den
 *     Token-Stream - schemabasierte Prüfungen gegen das Feature-Schema der Collection erfolgen wie
 *     bei den JSON-Formaten nachgelagert.
 *     <p>Bei GML wird das Koordinatenreferenzsystem jeder Geometrie in dieser Reihenfolge
 *     aufgelöst: das eigene "srsName"-Attribut der Geometrie, der Header "OGC-Content-Crs" der
 *     Anfrage, das Storage-CRS der Collection. Ein einzelnes Feature darf keine unterschiedlichen
 *     Koordinatenreferenzsysteme über seine Geometrien hinweg verwenden; eine solche Anfrage wird
 *     mit HTTP 400 abgelehnt. ADV-URN-Formen für "srsName" (z.B. "urn:adv:crs:ETRS89_UTM32") werden
 *     über die Option "srsNameMappings" des GML-Bausteins aufgelöst.
 *     <p>Die Eigenschaften müssen in der Repräsentation für Receivables vorliegen, also
 *     entsprechend dem Schema der Collection ohne die als `readOnly` gekennzeichneten
 *     Eigenschaften. Bei Objektverweisen ist die Repräsentation für Receivables ein JSON-Objekt mit
 *     drei Eigenschaften (`id`, dem Fremdschlüssel, `title`, einem beschreibenden Text, und `type`,
 *     dem Namen des Typs im Objektschema). Um ein bestehendes Feature zu aktualisieren, kann das
 *     aktuelle zu bearbeitende Feature im Profil `all-as-receivable` angefordert werden.
 *     <p>Wenn ein neues oder aktualisiertes Feature eine Geometrie enthält, muss die URI des
 *     Koordinatenreferenzsystems im Header "Content-Crs" der Anfrage angegeben werden. Um
 *     Koordinatentransformationen zu vermeiden, sollte die Geometrie im Storage-CRS vorliegen. Es
 *     dürfen nur die Koordinatenreferenzsysteme der Collection verwendet werden (siehe die
 *     Eigenschaft `crs` der Collection), sowohl im Header "Content-Crs" als auch im Payload (eine
 *     "coordRefSys"-Eigenschaft in JSON-FG, ein "srsName"-Attribut in GML); jedes andere
 *     Koordinatenreferenzsystem wird mit HTTP 400 abgelehnt.
 *     <p>Features dürfen nur eine einzige Geometrieeigenschaft mit Scope `RECEIVABLES` haben. Die
 *     Geometrie muss in der Eigenschaft "geometry" oder "place" abhängig vom Format (mit oder ohne
 *     die JSON-FG-Erweiterungen), dem Koordinatenreferenzsystem und dem Geometrietyp dargestellt
 *     werden (Details dazu, wann "place" zu verwenden ist, finden sich in JSON-FG).
 *     <p>Ein neues Feature kann auf zwei Arten erstellt werden, abhängig von der Angabe der
 *     `featureId`. Wenn die `featureId` von ldproxy während der Erstellung generiert und zugewiesen
 *     wird, wird POST auf `{landingPage}/collections/{collectionId}/items` verwendet und die URI
 *     des generierten Features ist im Header "Location" der Antwort enthalten. Wenn die Feature-ID
 *     vom Client vergeben wird, ist PUT auf
 *     `{landingPage}/collections/{collectionId}/items/{featureId}` zu verwenden. Clients können das
 *     Verhalten aus dem OpenAPI-Dokument ermitteln oder durch Inspektion des Feldes
 *     `supportsNonAutogeneratedResourceIds` in der Collection. Eine "id"-Eigenschaft in einem in
 *     einer POST- oder PUT-Anfrage übermittelten Feature wird ignoriert; eine PATCH-Anfrage, die
 *     den Identifikator des Features ändern würde, wird mit HTTP 400 abgelehnt, da das Feature über
 *     die URI der Ressource identifiziert wird.
 *     <p>Um ein in einer POST- oder PUT-Anfrage übermitteltes neues oder aktualisiertes Feature
 *     gegen das Schema der Collection (GeoJSON/JSON-FG) oder die konfigurierten XML Schemas (GML)
 *     zu validieren, kann der Anfrage ein Header `Prefer` mit dem Wert "handling=strict"
 *     hinzugefügt werden. Wenn die Validierung fehlschlägt, wird ein Fehler zurückgegeben und die
 *     Antwort meldet die angewendete Präferenz in einem "Preference-Applied"-Header. Für die
 *     Validierung wird ein Schema benötigt: der Baustein SCHEMA_VALIDATION bei GeoJSON und JSON-FG,
 *     die Option "schemaLocations" des GML-Bausteins bei GML. Ohne ein Schema wird der Payload
 *     nicht validiert und die Präferenz ignoriert.
 *     <p>Eine PUT-, PATCH- oder DELETE-Anfrage kann nicht von einem Entity-Tag abhängig gemacht
 *     werden: In einer Anfrage, die ein Feature ändert, ist kein Entity-Tag des Features bekannt,
 *     daher kann ein "If-Match"-Header nicht erfüllt werden und die Anfrage wird mit HTTP 412
 *     ("Precondition Failed") zurückgewiesen. Verwenden Sie stattdessen einen
 *     "If-Unmodified-Since"-Header (siehe die Option `optimisticLockingLastModified`).
 *     <p>Wenn das Feature in einer POST- oder PUT-Anfrage GeoJSON ohne die JSON-FG-Erweiterungen
 *     ist, fügen Sie der Anfrage einen Header `Link` mit dem Wert
 *     "<http://www.opengis.net/def/profile/OGC/0/rfc7946>; rel=profile" hinzu. Für ein Feature mit
 *     den JSON-FG-Erweiterungen verwenden Sie den Wert
 *     "<http://www.opengis.net/def/profile/OGC/0/jsonfg>; rel=profile".
 * @limitationsEn Only feature types from an SQL feature provider with `dialect` `PGIS` and
 *     `datasetChanges.mode` `CRUD` are supported.
 *     <p>The features may only have a single geometry property with scope `RECEIVABLES`.
 * @limitationsDe Es werden nur Objektarten von einem SQL-Feature-Provider mit `dialect` `PGIS` und
 *     `datasetChanges.mode` `CRUD` unterstützt.
 *     <p>Die Features dürfen nur eine einzige Geometrieeigenschaft mit dem Geltungsbereich
 *     `RECEIVABLES` haben.
 * @conformanceEn The building block is based on the specifications of the conformance classes
 *     "Create/Replace/Delete", "Update", "Optimistic Locking using Timestamps", "Handling
 *     Preference" and "Features" from the [Draft OGC API - Features - Part 4: Create, Replace,
 *     Update and Delete](https://docs.ogc.org/DRAFTS/20-002r1.html). The implementation will change
 *     as the draft will evolve during the standardization process.
 * @conformanceDe Der Baustein basiert auf den Vorgaben der Konformitätsklassen
 *     "Create/Replace/Delete", "Update", "Optimistic Locking using Timestamps", "Handling
 *     Preference" und "Features" aus dem [Entwurf von OGC API - Features - Part 4: Create, Replace,
 *     Update and Delete](https://docs.ogc.org/DRAFTS/20-002r1.html). Die Implementierung wird sich
 *     im Zuge der weiteren Standardisierung der Spezifikation noch ändern.
 * @ref:cfg {@link de.ii.ogcapi.crud.domain.CrudConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.crud.domain.ImmutableCrudConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.crud.app.EndpointCrud}
 * @ref:pathParameters {@link de.ii.ogcapi.features.core.domain.PathParameterCollectionIdFeatures}
 */
@Singleton
@AutoBind
public class CrudBuildingBlock implements ApiBuildingBlock {

  private static final Logger LOGGER = LoggerFactory.getLogger(CrudBuildingBlock.class);

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/20-002r1.html",
              "OGC API - Features - Part 4: Create, Replace, Update and Delete (DRAFT)"));

  private final FeaturesCoreProviders providers;

  @Inject
  public CrudBuildingBlock(FeaturesCoreProviders providers) {
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
    return new Builder().enabled(false).optimisticLockingLastModified(false).build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    api.getData()
        .getCollections()
        .forEach(
            (collectionId, collectionData) -> {
              if (collectionData
                      .getExtension(CrudConfiguration.class)
                      .map(CrudConfiguration::supportsLastModified)
                      .orElse(false)
                  && providers
                      .getFeatureSchema(api.getData(), collectionData)
                      .filter(
                          schema ->
                              schema.getAllNestedProperties().stream()
                                  .anyMatch(SchemaBase::lastModified))
                      .isEmpty()
                  && LOGGER.isWarnEnabled()) {
                LOGGER.warn(
                    "Conditional processing of requests that change a feature is enabled for collection '{}', but the type in the feature provider has no property with 'isLastModified' set to true. The 'If-Unmodified-Since' header will be required, but it cannot be evaluated.",
                    collectionId);
              }
            });

    return ApiBuildingBlock.super.onStartup(api, apiValidation);
  }

  private boolean isProviderSupportsMutations(OgcApiDataV2 apiData) {

    return providers
        .getFeatureProvider(apiData)
        .filter(
            provider -> {
              if (!provider.mutations().isSupported()) {
                LOGGER.warn(
                    "Disabling building block CRUD, feature provider with id '{}' does not support mutations: datasetChanges.mode is not 'CRUD'",
                    provider.getId());

                return false;
              }
              return true;
            })
        .isPresent();
  }
}
