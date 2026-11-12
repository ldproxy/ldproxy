/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.processes.features.domain.ImmutableProcessesFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title Processes - Features
 * @langEn Processes that aggregate features or find the features nearest to a point.
 * @langDe Prozesse, die Features aggregieren oder die einem Punkt nächstgelegenen Features finden.
 * @scopeEn The building block provides two process steps for the building block
 *     [Processes](processes.md). A process description in `values/processes/{apiId}` uses one of
 *     them as its only step in `job.partials`:
 *     <p><code>
 *     - `feature-aggregate:execute`: counts the selected features and computes aggregates of their
 *       properties, in total and per group.
 *     - `feature-nearest:execute`: returns the selected features within a distance of a point,
 *       nearest first.
 *     </code>
 *     <p>Both steps take the input `source`, the selection of the features, in one of three forms:
 *     <p><code>
 *     - `{"collection": "...", "parameters": {...}}`: the features of a collection, or of several
 *       collections with a list of ids, with query parameters of `/collections/{collectionId}/items`,
 *       e.g. queryables, `filter`, `bbox` or `datetime`. Parameters that select a page, the
 *       coordinate reference system or the format are ignored, as are the parameters that select
 *       the properties or the profiles; those apply only to the features that
 *       `feature-nearest:execute` returns.
 *     - `{"query": "...", "parameters": {...}}`: the features returned by a stored query, with the
 *       values of its parameters.
 *     - `{"href": "..."}`: the URL of a request of `/collections/{collectionId}/items` or of
 *       `/search/{queryId}` of the same API.
 *     </code>
 *     <p>The step `feature-aggregate:execute` takes further inputs:
 *     <p><code>
 *     - `groupBy`: a list of properties, each a name or an object `{"property": "...", "bucket":
 *       ...}`. The bucket is `year` or `month` for temporal values, or the class width of numeric
 *       values. A feature with several values of a property belongs to one group per value; a
 *       feature without a value belongs to the group `null`. If several collections are selected,
 *       the collection is the first group key, `objecttype`. The virtual group key `{"property":
 *       "$geometry", "bucket": ...}` groups the features by the cells of a square grid with the
 *       width in meters in the native coordinate reference system, which must be projected; a
 *       feature belongs to the cell of a point on its geometry, the key is the center of the cell
 *       as `[lon, lat]` in CRS84.
 *     - `aggregates`: a list of aggregates, each `count` or an object `{"function": "...",
 *       "property": "..."}` with the functions `count`, `sum`, `min`, `max`, `avg` and `median`.
 *       The virtual properties `$area` and `$length` are the area and the length of the primary
 *       geometry in the native coordinate reference system, which must be projected. Default is
 *       `count`.
 *     - `clip`: only the features that intersect an area, either `{"point": [lon, lat],
 *       "distance": ...}` with a distance in meters or `{"geometry": {...}}` with a GeoJSON polygon
 *       in CRS84; `$area` and `$length` are computed for the part within the area.
 *     - `limit`: the maximum number of groups, the groups are sorted by the first aggregate,
 *       descending.
 *     </code>
 *     <p>The outputs are `groups` (the groups with their key and aggregates), `total` (the
 *     aggregates of all features), `featuresScanned` and `groupsTruncated`.
 *     <p>The step `feature-nearest:execute` takes the inputs `point` (`[lon, lat]` in CRS84),
 *     `distance` (the maximum distance in meters) and `limit` (the maximum number of features, at
 *     most 100, default 10). The output `features` is a GeoJSON feature collection in which each
 *     feature has the additional member `distance`, the distance from the point in meters. The
 *     features are encoded as in a response of `/collections/{collectionId}/items`, with the
 *     parameters `properties`, `exclude-properties` and `profile` of a collection source. Without
 *     `profile` in the source, the profiles of the parameter `profile` of the execution request
 *     apply.
 *     <p>The process descriptions are written by the deployment, the inputs and outputs follow the
 *     description above. Example of a process `aggregate-features`:
 *     <p><code>
 * ```yaml
 * ---
 * id: aggregate-features
 * title: Aggregate features
 * description: Counts features and computes aggregates of their properties, per group.
 * version: 1.0.0
 * jobControlOptions: [SYNC_EXECUTE]
 * inputs:
 *   source:
 *     title: Selection of the features
 *     schema:
 *       type: object
 *   groupBy:
 *     title: Group keys
 *     minOccurs: 0
 *     schema:
 *       type: array
 *   aggregates:
 *     title: Aggregates
 *     minOccurs: 0
 *     schema:
 *       type: array
 *   clip:
 *     title: Area
 *     minOccurs: 0
 *     schema:
 *       type: object
 *   limit:
 *     title: Maximum number of groups
 *     minOccurs: 0
 *     schema:
 *       type: integer
 *       minimum: 1
 * outputs:
 *   groups:
 *     schema:
 *       type: array
 *   total:
 *     schema:
 *       type: object
 *   featuresScanned:
 *     schema:
 *       type: integer
 *   groupsTruncated:
 *     schema:
 *       type: boolean
 * job:
 *   partials: ['feature-aggregate:execute']
 * ```
 *     </code>
 *     <p>The features are read with the same query as the corresponding request of the API, limited
 *     to the properties that the process needs; the option `maximumFeaturesScanned` limits the
 *     number of features that one execution reads.
 * @scopeDe Der Baustein stellt zwei Prozessschritte für den Baustein [Processes](processes.md)
 *     bereit. Eine Prozessbeschreibung in `values/processes/{apiId}` verwendet einen davon als
 *     einzigen Schritt in `job.partials`:
 *     <p><code>
 *     - `feature-aggregate:execute`: zählt die ausgewählten Features und berechnet Aggregate ihrer
 *       Eigenschaften, insgesamt und je Gruppe.
 *     - `feature-nearest:execute`: liefert die ausgewählten Features innerhalb einer Entfernung von
 *       einem Punkt, das nächstgelegene zuerst.
 *     </code>
 *     <p>Beide Schritte haben die Eingabe `source`, die Auswahl der Features, in einer von drei
 *     Formen:
 *     <p><code>
 *     - `{"collection": "...", "parameters": {...}}`: die Features einer Collection, oder mehrerer
 *       Collections mit einer Liste von Ids, mit Query-Parametern von
 *       `/collections/{collectionId}/items`, z.B. Queryables, `filter`, `bbox` oder `datetime`.
 *       Parameter, die eine Seite, das Koordinatenreferenzsystem oder das Format auswählen, werden
 *       ignoriert, ebenso die Parameter, die die Eigenschaften oder die Profile auswählen; diese
 *       gelten nur für die Features, die `feature-nearest:execute` liefert.
 *     - `{"query": "...", "parameters": {...}}`: die Features, die eine Stored Query liefert, mit
 *       den Werten ihrer Parameter.
 *     - `{"href": "..."}`: die URL einer Anfrage von `/collections/{collectionId}/items` oder von
 *       `/search/{queryId}` derselben API.
 *     </code>
 *     <p>Der Schritt `feature-aggregate:execute` hat weitere Eingaben:
 *     <p><code>
 *     - `groupBy`: eine Liste von Eigenschaften, jeweils ein Name oder ein Objekt `{"property":
 *       "...", "bucket": ...}`. Der Bucket ist `year` oder `month` für zeitliche Werte oder die
 *       Klassenbreite numerischer Werte. Ein Feature mit mehreren Werten einer Eigenschaft gehört
 *       zu einer Gruppe je Wert; ein Feature ohne Wert gehört zur Gruppe `null`. Sind mehrere
 *       Collections ausgewählt, ist die Collection der erste Gruppenschlüssel, `objecttype`. Der
 *       virtuelle Gruppenschlüssel `{"property": "$geometry", "bucket": ...}` gruppiert die Features
 *       nach den Zellen eines quadratischen Gitters mit der Breite in Metern im nativen
 *       Koordinatenreferenzsystem, das projiziert sein muss; ein Feature gehört zur Zelle eines
 *       Punkts auf seiner Geometrie, der Schlüssel ist der Mittelpunkt der Zelle als `[lon, lat]`
 *       in CRS84.
 *     - `aggregates`: eine Liste von Aggregaten, jeweils `count` oder ein Objekt `{"function":
 *       "...", "property": "..."}` mit den Funktionen `count`, `sum`, `min`, `max`, `avg` und
 *       `median`. Die virtuellen Eigenschaften `$area` und `$length` sind die Fläche und die Länge
 *       der primären Geometrie im nativen Koordinatenreferenzsystem, das projiziert sein muss.
 *       Default ist `count`.
 *     - `clip`: nur die Features, die ein Gebiet schneiden, entweder `{"point": [lon, lat],
 *       "distance": ...}` mit einer Entfernung in Metern oder `{"geometry": {...}}` mit einem
 *       GeoJSON-Polygon in CRS84; `$area` und `$length` werden für den Teil innerhalb des Gebiets
 *       berechnet.
 *     - `limit`: die maximale Anzahl der Gruppen, die Gruppen sind absteigend nach dem ersten
 *       Aggregat sortiert.
 *     </code>
 *     <p>Die Ausgaben sind `groups` (die Gruppen mit ihrem Schlüssel und ihren Aggregaten), `total`
 *     (die Aggregate aller Features), `featuresScanned` und `groupsTruncated`.
 *     <p>Der Schritt `feature-nearest:execute` hat die Eingaben `point` (`[lon, lat]` in CRS84),
 *     `distance` (die maximale Entfernung in Metern) und `limit` (die maximale Anzahl an Features,
 *     höchstens 100, Default 10). Die Ausgabe `features` ist eine GeoJSON-FeatureCollection, in der
 *     jedes Feature das zusätzliche Member `distance` hat, die Entfernung vom Punkt in Metern. Die
 *     Features sind wie in einer Antwort von `/collections/{collectionId}/items` kodiert, mit den
 *     Parametern `properties`, `exclude-properties` und `profile` einer Collection-Quelle. Ohne
 *     `profile` in der Quelle gelten die Profile des Parameters `profile` der Anfrage zur
 *     Ausführung.
 *     <p>Die Prozessbeschreibungen schreibt das Deployment, die Eingaben und Ausgaben folgen der
 *     Beschreibung oben. Beispiel eines Prozesses `aggregate-features`:
 *     <p><code>
 * ```yaml
 * ---
 * id: aggregate-features
 * title: Aggregate features
 * description: Counts features and computes aggregates of their properties, per group.
 * version: 1.0.0
 * jobControlOptions: [SYNC_EXECUTE]
 * inputs:
 *   source:
 *     title: Selection of the features
 *     schema:
 *       type: object
 *   groupBy:
 *     title: Group keys
 *     minOccurs: 0
 *     schema:
 *       type: array
 *   aggregates:
 *     title: Aggregates
 *     minOccurs: 0
 *     schema:
 *       type: array
 *   clip:
 *     title: Area
 *     minOccurs: 0
 *     schema:
 *       type: object
 *   limit:
 *     title: Maximum number of groups
 *     minOccurs: 0
 *     schema:
 *       type: integer
 *       minimum: 1
 * outputs:
 *   groups:
 *     schema:
 *       type: array
 *   total:
 *     schema:
 *       type: object
 *   featuresScanned:
 *     schema:
 *       type: integer
 *   groupsTruncated:
 *     schema:
 *       type: boolean
 * job:
 *   partials: ['feature-aggregate:execute']
 * ```
 *     </code>
 *     <p>Die Features werden mit derselben Abfrage gelesen wie bei der entsprechenden Anfrage der
 *     API, beschränkt auf die Eigenschaften, die der Prozess benötigt; die Option
 *     `maximumFeaturesScanned` begrenzt die Anzahl der Features, die eine Ausführung liest.
 * @ref:cfg {@link de.ii.ogcapi.processes.features.domain.ProcessesFeaturesConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.processes.features.domain.ImmutableProcessesFeaturesConfiguration}
 */
@Singleton
@AutoBind
public class ProcessesFeaturesBuildingBlock implements ApiBuildingBlock {

  public static final int MAXIMUM_FEATURES_SCANNED = 100_000;

  @Inject
  public ProcessesFeaturesBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProcessesFeaturesConfiguration.Builder()
        .enabled(false)
        .maximumFeaturesScanned(MAXIMUM_FEATURES_SCANNED)
        .build();
  }
}
