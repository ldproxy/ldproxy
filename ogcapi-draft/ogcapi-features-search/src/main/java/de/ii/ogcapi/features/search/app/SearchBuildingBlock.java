/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.search.domain.ImmutableSearchConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - Search
 * @langEn Support for fetching features from multiple collections as well as for stored queries.
 * @langDe Unterstützung für den Abruf von Features aus mehreren Collections sowie für gespeicherte
 *     Abfragen.
 * @scopeEn This building block supports searching for features from one or more collections. That
 *     is, it supports query expressions that cannot be expressed, or cannot be conveniently
 *     expressed, using the filtering mechanisms available through the building blocks *Features*
 *     and *Filter*.
 *     <p>Examples of the types of query expressions that can be expressed:
 *     <p><code>
 * - query expressions with a long expression text that cannot be conveniently specified as URL;
 * - query expressions that, in a single request, fetch resources from one or more collections;
 * - stored queries;
 * - stored queries referencing multiple resource collections;
 * - stored queries with parameters.
 *     </code>
 *     <p>A query expression is expressed as a JSON object.
 *     <p>The query expression object can describe a single query (the properties "collections",
 *     "filter", "filterCrs", "properties" and "sortby" are members of the query expression object)
 *     or multiple queries (a "queries" member with an array of query objects is present) in a
 *     single request.
 *     <p>For each query:
 *     <p><code>
 * - The value of "collection" is an array with one item, the identifier of the collection to query.
 * - The value of "filter" is a CQL2 JSON filter expression. See the [Filter building block](filter.md).
 * - The value of "filterCrs" is the URI of the coordinate reference system of coordinates in a "filter". The default is "http://www.opengis.net/def/crs/OGC/1.3/CRS84" (WGS 84, longitude/latitude).
 * - The value of "properties" is an array with the names of properties to include in the response. See the [Projections building block](projections.md).
 * - The value of "sortby" is used to sort the features in the response. See the [Sorting building block](sorting.md).
 *     </code>
 *     <p>For multiple queries:
 *     <p><code>
 * - If multiple queries are specified, the results are concatenated. The response is a single
 *     feature collection. The feature ids in the response to a multi-collection query must be
 *     unique. Since the identifier of a feature only has to be unique per collection, they need
 *     to be combined with the collection identifier. A concatenation with "." as the joining
 *     character is used (e.g., "apronelement.123456").
 * - The direct members "filter" and "properties" represent "global" constraints that must be
 *     combined with the corresponding member in each query. The global and local property selection
 *     list are concatenated and then the global and local filters are combined using the logical
 *     operator specified by the "filterOperator" member.
 *   - The global member "filter" must only reference queryables that are common to all
 *     collections being queried.
 *   - The global member "properties" must only reference presentables that are common to all
 *     collections being queried.
 *     </code>
 *     <p>General remarks:
 *     <p><code>
 * - A "title" and "description" for the query expression can be added. Providing both is
 *     strongly recommended to explain the query to users.
 * - The "limit" member applies to the entire result set.
 * - The “crs” and “verticalCrs” members are optional and can be used to specify the coordinate
 *     reference system for the coordinates in the response. If no value is specified, the default
 *     coordinate reference system is used. If "verticalCrs" is specified, then it must be the URI
 *     of a vertical coordinate reference system and "crs" must be specified and be the URI of a
 *     horizontal 2D coordinate reference system.
 * - "sortby" will only apply per query. A global "sortby" would require that the
 *     results of all queries are compiled first and then the combined result set is sorted. This
 *     would not support "streaming" the response.
 * - In case of a parameterized stored query, the query expression may contain JSON objects
 *     with a member "$parameter". The value of "$parameter" is an object with a member where the
 *     key is the parameter name and the value is a JSON schema describing the parameter. When
 *     executing the stored query, all objects with a "$parameter" member are replaced with the
 *     value of the parameter for this query execution. Comma-separated parameter values are
 *     converted to an array, if the parameter is of type "array".
 * - Parameters may also be provided in a member "parameters" in the query expression and
 *     referenced using "$ref".
 * - Parameters may be used in all members of the query expression, except "id", "title",
 *     "description", and "offset".
 *     </code>
 * @scopeDe Dieser Baustein unterstützt die Suche nach Features aus einer oder mehreren Collections.
 *     Das heißt, es unterstützt Abfragen, die mit den Filtermechanismen, die durch die Bausteine
 *     *Features* und *Filter* zur Verfügung stehen, nicht oder nicht bequem ausgedrückt werden
 *     können.
 *     <p>Beispiele für die Arten von Abfragen:
 *     <p><code>
 * - Abfragen mit einem langen Ausdruckstext, der nicht bequem als URL angegeben werden kann;
 * - Abfragen, die in einer einzigen Anfrage Ressourcen aus einer oder mehreren Collections abrufen;
 * - gespeicherte Abfragen;
 * - gespeicherte Abfragen, die mehrere Collections referenzieren;
 * - gespeicherte Abfragen mit Parametern.
 *     </code>
 *     <p>Eine Query Expression wird als JSON-Objekt ausgedrückt.
 *     <p>Das Query-Expression-Objekt kann eine einzelne Abfrage (Key-Value-Paare "collections",
 *     "filter", "properties" und "sortby") oder mehrere Abfragen (Key-Value-Paar "queries" mit
 *     einem Array von Abfrageobjekten) in einer einzigen Anfrage beschreiben.
 *     <p>Für jede Abfrage:
 *     <p><code>
 * - Der Wert von "collection" ist ein Array mit einem Element, dem Identifikator der abzufragenden Feature Collection.
 * - Der Wert von "filter" ist ein CQL2 JSON-Filterausdruck. Siehe den [Filter-Baustein](filter.md).
 * - Der Wert von "filterCrs" ist die URI des Koordinatenreferenzsystems der Koordinaten in einem "filter". Der Standardwert ist "http://www.opengis.net/def/crs/OGC/1.3/CRS84" (WGS 84, Longitude/Latitude).
 * - Der Wert von "properties" ist ein Array mit den Namen der Eigenschaften, die in die Antwort aufgenommen werden sollen. Siehe den [Projections-Baustein](projections.md).
 * - Der Wert von "sortby" wird zum Sortieren der Merkmale in der Antwort verwendet. Siehe den [Sorting-Baustein](sorting.md).
 *     </code>
 *     <p>Für mehrere Abfragen:
 *     <p><code>
 * - Wenn mehrere Abfragen angegeben werden, werden die Ergebnisse zusammengeführt. Die Antwort ist eine einzelne Feature Collection. Die Feature-IDs in der Antwort auf eine Abfrage mit mehreren Feature Collections müssen eindeutig sein. Da der Bezeichner eines Features nur pro Feature Collection eindeutig sein muss, müssen sie mit der Feature-Collection-ID kombiniert werden. Es wird eine Verkettung mit "." als Verbindungszeichen verwendet (z. B. "apronelement.123456").
 * - Die direkten Key-Value-Paare "filter" und "properties" stellen 'globale' Bedingungen dar, die in jeder Abfrage mit dem entsprechenden Key-Value-Paar kombiniert werden müssen. Die globale und die lokale Eigenschaftsauswahlliste werden verkettet. Die globalen und lokalen Filter werden mit dem logischen Operator kombiniert, der durch das Mitglied "filterOperator" angegeben wird.
 *   - Das globale Mitglied "filter" darf nur auf Queryables verweisen, die allen abgefragten Collections gemeinsam sind.
 *   - Das globale Mitglied "properties" darf nur auf Presentables verweisen, die allen abgefragten Collections gemeinsam sind.
 *     </code>
 *     <p>Allgemeines:
 *     <p><code>
 * - Ein "Titel" und eine "Beschreibung" für die Query Expression können hinzugefügt werden. Es wird dringend empfohlen, beides anzugeben, um Benutzern die Abfrage zu erklären.
 * - Das Element "limit" gilt für die gesamte Ergebnismenge.
 * - Die Elemente "crs" und "verticalCrs" sind optional und können verwendet werden, um das Koordinatenreferenzsystem für die Koordinaten in der Antwort zu spezifizieren. Ist kein Wert angegeben, dann wird das Standard-Koordinatenreferenzsystem verwendet. Ist "verticalCrs" angegeben, dann muss es die URI eines vertikalen Koordinatenreferenzsystem sind und "crs" angegeben sein und die URI eines horizontales 2D-Koordinatenreferenzsystem sein.
 * - "sortby" wird nur pro Abfrage angewendet. Ein globales "sortby" würde erfordern, dass die Ergebnisse aller Abfragen zuerst kompiliert werden und dann die kombinierte Ergebnismenge sortiert wird. Dies würde das "Streaming" der Antwort nicht unterstützen.
 * - Im Falle einer parametrisierten gespeicherten Abfrage kann der Abfrageausdruck JSON-Objekte mit einem Member "$parameter" enthalten. Der Wert von "$parameter" ist ein Objekt mit einem Key-Value-Paar, bei dem der Schlüssel der Parametername und der Wert ein JSON-Schema ist, das den Parameter beschreibt. Bei der Ausführung der gespeicherten Abfrage werden alle Objekte mit einem "$parameter"-Member durch den Wert des Parameters für diese Abfrageausführung ersetzt. Kommagetrennte Parameterwerte werden in ein Array umgewandelt, wenn der Parameter vom Typ "array" ist.
 * - Parameter können auch in einem Member "parameters" im Abfrageausdruck angegeben werden und mit "$ref" referenziert werden.
 * - Parameter können in allen Membern der Query Expression verwendet werden, außer in "id", "title", "description" und "offset".
 *     </code>
 * @cfgFilesEn Stored queries must be located in the value store under the relative path
 *     `queries/{apiId}/{queryId}.json`.
 * @cfgFilesDe Stored-Queries müssen im Value-Store unter dem relativen Pfad
 *     `queries/{apiId}/{queryId}.json` abgelegt werden.
 * @conformanceEn This building block implements the OGC API Features extensions specified in
 *     chapters 5 and 6 of the [OGC Testbed-18 Filtering Service and Rule Set Engineering
 *     Report](https://docs.ogc.org/per/22-024r2.html). The implementation will change as the draft
 *     will evolve during the standardization process.
 * @conformanceDe Dieser Baustein implementiert die OGC-API-Features-Erweiterungen, die in den
 *     Kapiteln 5 und 6 des [OGC Testbed-18 Filtering Service and Rule Set Engineering
 *     Report](https://docs.ogc.org/per/22-024r2.html) spezifiziert sind. Die Implementierung wird
 *     sich im Zuge der weiteren Standardisierung der Spezifikation noch ändern.
 * @limitationsEn Parameterized stored queries have the following constraints:
 *     <p><code>
 * - The JSON Schema of a parameter supports a subset of the language. In particular,
 *     `patternProperties`, `additionalProperties`, `allOf`, `oneOf`, `prefixItems`,
 *     `additionalItems` and `items: false` are not supported.
 * - Parameters in filter expressions are limited to scalar values (string, number, boolean) or
 *     or arrays of scalar values.
 * - POST to execute a stored query is not supported. This will be added after discussing the
 *     specification in OGC (e.g., whether the payload should be JSON or URL-encoded query
 *     parameters).
 *     </code>
 *     <p>Ad-hoc queries have the following limitations:
 *     <p><code>
 * - Paging is not supported for ad-hoc queries and sufficiently large values for `limit` should be used. See [issue 906](https://github.com/interactive-instruments/ldproxy/issues/906).
 *     </code>
 * @limitationsDe Für parametrisierte gespeicherte Abfragen gelten die folgenden Beschränkungen:
 *     <p><code>
 * - Das JSON-Schema eines Parameters unterstützt eine Teilmenge der Sprache. Insbesondere werden `patternProperties`, `additionalProperties`, `allOf`, `oneOf`, `prefixItems`, `additionalItems` und `items: false` nicht unterstützt.
 * - Parameter in Filterausdrücken sind auf Skalarwerte (Strings, Zahlen, Boolesche Werte) oder Arrays von Skalarwerten beschränkt.
 * - POST zur Ausführung einer gespeicherten Abfrage wird nicht unterstützt. Dies wird hinzugefügt, nachdem die Spezifikation in OGC diskutiert wurde (z.B. ob die Nutzlast JSON oder URL-kodierte Abfrageparameter sein sollen).
 *     </code>
 *     <p>Ad-hoc-Queries haben die folgenden Beschränkungen:
 *     <p><code>
 * - Paging wird für Ad-Hoc-Queries nicht unterstützt. Bis zur Klärung sollten ausreichend große Werte für `limit` verwendet werden. Siehe [Issue 906](https://github.com/interactive-instruments/ldproxy/issues/906).
 *     </code>
 * @ref:cfg {@link de.ii.ogcapi.features.search.domain.SearchConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.search.domain.ImmutableSearchConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.features.search.app.EndpointAdHocQuery}, {@link
 *     de.ii.ogcapi.features.search.app.EndpointStoredQueries}, {@link
 *     de.ii.ogcapi.features.search.app.EndpointStoredQuery}, {@link
 *     de.ii.ogcapi.features.search.app.EndpointStoredQueriesManager}, {@link
 *     de.ii.ogcapi.features.search.app.EndpointStoredQueryDefinition}, {@link
 *     de.ii.ogcapi.features.search.app.EndpointParameters}, {@link
 *     de.ii.ogcapi.features.search.app.EndpointParameter}
 * @ref:queryParameters {@link de.ii.ogcapi.features.search.app.QueryParameterFAdHocQuery}, {@link
 *     de.ii.ogcapi.features.search.app.QueryParameterFStoredQueries}, {@link
 *     de.ii.ogcapi.features.search.app.QueryParameterFStoredQuery}, {@link
 *     de.ii.ogcapi.features.search.app.QueryParameterFQueryDefinition}, {@link
 *     de.ii.ogcapi.features.search.app.QueryParameterDryRunStoredQueriesManager}
 * @ref:pathParameters {@link de.ii.ogcapi.features.search.app.PathParameterQueryId}, {@link
 *     de.ii.ogcapi.features.search.app.PathParameterName}
 */
@Singleton
@AutoBind
public class SearchBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/per/22-024r2.html",
              "Testbed-18: Filtering Service and Rule Set Engineering Report (PREDRAFT)"));
  public static final String QUERY_ID_PATTERN = "[\\w\\-]+";

  @Inject
  public SearchBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableSearchConfiguration.Builder()
        .enabled(false)
        .managerEnabled(false)
        .validationEnabled(false)
        .allLinksAreLocal(false)
        .build();
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return SearchBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return SearchBuildingBlock.SPEC;
  }
}
