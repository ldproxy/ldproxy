/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Lists;
import de.ii.ogcapi.foundation.domain.CachingConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock STYLES
 * @langEn ### Layer Control
 *     <p>The layer control dialog in the webmap is activated via the `webmapWithLayerControl`
 *     option (see 'Options' below).
 *     <p>The dialog can be configured in the MapLibre style metadata (member
 *     `ldproxy:layerControl`). On the top level there are the following JSON members:
 *     <p><code>
 * - `onlyLegend` (Boolean, default: false): With `true` only the groups and layers with the symbols are displayed without the possibility to deactivate layers.
 * - `opened` (Boolean, default: false): If `true` is set, the layer selection dialog will be displayed open when the map is loaded.
 * - `entries` (default: one merge group per source layer in the vector tiles): The configuration of the groups and layers as described below.
 *     </code>
 *     <p>In `entries` the following items allowed:
 *     <p><code>
 * - The `id` of a MapLibre layer in the style (string). The layer is displayed in the dialog with the `id` as name. The symbol of the layer is created without specifying attributes or a particular zoom layer.
 * - A layer object. `id` is the `id` of the MapLibre layer in the style (string, mandatory). `label` is the name of the layer in the dialog (string, default: `id`). `zoom` is the zoom level to use when creating symbols (number, default: none). `properties` are attributes (object, default `{}`) to be used during symbol generation.
 * - A group object. `type` is always "group". `id` is an `id` of the group (string, mandatory). `label` is the name of the group in the dialog (string, default: `id`). With `onlyLegend` the possibility to disable layers can be disabled for the group (Boolean, default: `false`). `entries` can contain layers (objects or string), groups or merge groups (array, default: `[]`).
 * - A radio group object. It may only occur on the top level of entries. Only exactly one entry can be selected from the group, e.g. for the selection of a basemap. `type` is always "radio-group". `id`, `label` have the same effect as for normal groups. `entries` can only contain layers (objects or string) (array, default: `[]`).
 * - A merge group object. `type` is always "merge-group". A merge group is a group where `entries` may only contain layers (objects or string) (array, default: `[]`); these entries are not displayed as subentries in the dialog, but a symbol is created from all layers together. Instead of specifying `entries`, `source-layer` (string, default: none) can be specified alternatively; in this case, all layers with this source layer become entries.
 *     </code>
 *     <p>For an example, see below.
 * @langDe ### Layerauswahl
 *     <p>Der Layerauswahldialog in der Webmap wird über die Option `webmapWithLayerControl`
 *     aktiviert (siehe 'Optionen' unten).
 *     <p>Der Dialog kann in den Metadaten des MapLibre-Styles konfiguriert werden (Member
 *     `ldproxy:layerControl`). Auf der obersten Ebene gibt es die folgenden Member:
 *     <p><code>
 * - `onlyLegend` (Boolean, Default: false): Bei `true` werden nur die Gruppen und Layer mit den Symbolen angezeigt ohne die Möglichkeit, Layer zu deaktivieren.
 * - `opened` (Boolean, Default: false): Bei `true` wird der Layerauswahldialog bei Laden der Karte geöffnet dargestellt.
 * - `entries` (Default: eine Merge-Group pro Source-Layer in den Vector Tiles): Die Konfiguration der Gruppen und Layer, wie nachfolgend beschrieben.
 *     </code>
 *     <p>In `entries` sind die folgenden Einträge möglich:
 *     <p><code>
 * - Die `id` eines MapLibre-Layers in dem Style (String). Der Layer wird im Dialog mit der `id` als Namen dargestellt. Das Symbol des Layers wird ohne Angabe von Attributen oder eines bestimmten Zoomlayers erzeugt.
 * - Ein Layer (Objekt). `id` ist die `id` das MapLibre-Layers in dem Style (String, Pflichtangabe). `label` ist der Name des Layers im Dialog (String, Default: `id`). `zoom` ist die bei der Symbolerzeugung zu verwendende Zoomstufe (Nummer, Default: ohne). `properties` sind bei der Symbolerzeugung zu verwendene Attribute (Objekt, Default `{}`).
 * - Eine Gruppe (Objekt). `type` ist immer "group". `id` ist eine `id` der Gruppe (String, Pflichtangabe). `label` ist der Name des Gruppe im Dialog (String, Default: `id`). Mit `onlyLegend` kann für die Gruppe die Möglichkeit deaktiviert werden, Layer zu deaktivieren (Boolean, Default: `false`). `entries` kann Layer (Objekte oder String), Gruppen oder Merge-Gruppen enthalten (Array, Default: `[]`).
 * - Eine Radio-Gruppe (Objekt). Sie darf nur auf der obersten Ebene der Einträge vorkommen. Aus der Gruppe kann nur genau ein Eintrag ausgewählt werden, z.B. für die Auswahl einer Basemap. `type` ist immer "radio-group". `id`, `label` wirken wie bei normalen Gruppen. `entries` kann nur Layer (Objekte oder String) enthalten (Array, Default: `[]`).
 * - Eine Merge-Gruppe (Objekt). `type` ist immer "merge-group". Eine Merge-Gruppe ist eine Gruppe, bei der `entries` nur Layer (Objekte oder String) enthalten darf (Array, Default: `[]`); diese Einträge werden nicht als Untereinträge im Dialog dargestellt, sondern aus allen Layern wird zusammen ein Symbol erzeugt. Statt der Angabe von `entries` kann alternativ auch `source-layer` (String, Default: ohne) angegeben werden; in diesem Fall werden alle Layer mit diesem Source-Layer zu Einträgen.
 *     </code>
 *     <p>Siehe unten für ein Beispiel.
 * @examplesEn Example of the specifications in the configuration file:
 *     <p><code>
 * ```yaml
 * - buildingBlock: STYLES
 *   enabled: true
 *   styleEncodings:
 *   - Mapbox
 *   - HTML
 *   deriveCollectionStyles: true
 *   managerEnabled: false
 *   validationEnabled: false
 *   webmapWithLayerControl: true
 * ```
 *     </code>
 *     <p>Example MapLibre stylesheet with layer control options
 *     <p><code>
 * ```json
 * {
 *   "bearing": 0.0,
 *   "version": 8,
 *   "pitch": 0.0,
 *   "name": "topographic",
 *   "center": [36.1033, 32.6264],
 *   "zoom": 12.241790506353492,
 *   "metadata": {
 *     "ldproxy:layerControl": {
 *       "opened": true,
 *       "entries": [
 *         {
 *           "id": "Basemap",
 *           "type": "radio-group",
 *           "entries": ["Grey Background", "OSM"]
 *         },
 *         {
 *           "id"         : "Agriculture (Surfaces)",
 *           "type"       : "merge-group"           ,
 *           "sourceLayer": "AgricultureSrf"
 *         },
 *         {
 *           "id"         : "Cultural (Points)",
 *           "type"       : "merge-group"      ,
 *           "sourceLayer": "CulturePnt"
 *         },
 *         {
 *           "id"         : "Cultural (Surfaces)",
 *           "type"       : "merge-group"        ,
 *           "sourceLayer": "CultureSrf"
 *         },
 *         {
 *           "id"         : "Facility (Points)",
 *           "type"       : "merge-group"      ,
 *           "sourceLayer": "FacilityPnt"
 *         },
 *         {
 *           "id"         : "Hydrography (Curves)",
 *           "type"       : "merge-group"         ,
 *           "sourceLayer": "HydrographyCrv"
 *         },
 *         {
 *           "id"         : "Hydrography (Surfaces)",
 *           "type"       : "merge-group"           ,
 *           "sourceLayer": "HydrographySrf"
 *         },
 *         {
 *           "id"         : "Military (Surfaces)",
 *           "type"       : "merge-group"        ,
 *           "sourceLayer": "MilitarySrf"
 *         },
 *         {
 *           "id"         : "Settlement (Surfaces)",
 *           "type"       : "merge-group"          ,
 *           "sourceLayer": "SettlementSrf"
 *         },
 *         {
 *           "id"         : "Structure (Points)",
 *           "type"       : "merge-group"       ,
 *           "sourceLayer": "StructurePnt"
 *         },
 *         {
 *           "id": "Transportation - Ground (Curves)",
 *           "type": "group",
 *           "entries": [
 *             {
 *               "id": "National Road",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "transportationgroundcrv.3", "zoom": 8},
 *                 {"id": "transportationgroundcrv.4", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Secondary Road",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "transportationgroundcrv.5", "zoom": 8},
 *                 {"id": "transportationgroundcrv.6", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Local Road",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "transportationgroundcrv.7", "zoom": 8},
 *                 {"id": "transportationgroundcrv.8", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Bridge",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "transportationgroundcrv.0a", "zoom": 8},
 *                 {"id": "transportationgroundcrv.0b", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Railway",
 *               "type": "merge-group",
 *               "entries": ["transportationgroundcrv.1", "transportationgroundcrv.2"]
 *             },
 *             { "id": "Name", "type": "merge-group", "entries": ["transportationgroundcrv.9"] }
 *           ]
 *         },
 *         {
 *           "id"         : "Utility Infrastructure (Points)",
 *           "type"       : "merge-group"                    ,
 *           "sourceLayer": "UtilityInfrastructurePnt"
 *         },
 *         {
 *           "id"         : "Utility Infrastructure (Curves)",
 *           "type"       : "merge-group"                    ,
 *           "sourceLayer": "UtilityInfrastructureCrv"
 *         },
 *         {
 *           "id"         : "Vegetation (Surfaces)",
 *           "type"       : "merge-group"          ,
 *           "sourceLayer": "VegetationSrf"
 *         }
 *       ]
 *     }
 *   },
 *   "sources": {
 *     "daraa": {
 *       "type": "vector",
 *       "tiles": ["{serviceUrl}/tiles/WebMercatorQuad/{z}/{y}/{x}?f=mvt"],
 *       "maxzoom": 16
 *     },
 *     "osm": {
 *       "type": "raster",
 *       "tiles": [
 *         "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
 *         "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
 *         "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"
 *       ],
 *       "tileSize": 256,
 *       "attribution": "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"
 *     }
 *   },
 *   "sprite": "{serviceUrl}/resources/sprites",
 *   "glyphs": "https://go-spatial.github.io/carto-assets/fonts/{fontstack}/{range}.pbf",
 *   "layers": [
 *     {
 *       "id": "Grey Background",
 *       "type": "background",
 *       "layout": {"visibility": "visible"},
 *       "paint": {"background-color": "#d3d3d3"}
 *     },
 *     {
 *       "id": "OSM",
 *       "type": "raster",
 *       "source": "osm",
 *       "layout": {"visibility": "none"}
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "AgricultureSrf",
 *       "paint": {"fill-color": "#7ac5a5"},
 *       "id": "agriculturesrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "VegetationSrf",
 *       "paint": {"fill-color": "#C2E4B9"},
 *       "id": "vegetationsrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "SettlementSrf",
 *       "paint": {"line-color": "#000000", "line-width": 2},
 *       "id": "settlementsrf.1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "SettlementSrf",
 *       "paint": {"fill-color": "#E8C3B2"},
 *       "id": "settlementsrf.2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "MilitarySrf",
 *       "paint": {"fill-color": "#f3602f", "fill-opacity": 0.5},
 *       "id": "militarysrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "CultureSrf",
 *       "paint": {"fill-color": "#ab92d2", "fill-opacity": 0.5},
 *       "id": "culturesrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "HydrographyCrv",
 *       "filter": [ "==", "BH140", ["get", "F_CODE"] ],
 *       "paint": {
 *         "line-color": "#00A0C6",
 *         "line-width": [ "step", ["zoom"], 1, 8, 2, 13, 4 ]
 *       },
 *       "id": "hydrographycrv",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "HydrographySrf",
 *       "filter": [ "==", "BH082", ["get", "F_CODE"] ],
 *       "paint": {"fill-color": "#B0E1ED", "fill-outline-color": "#00A0C6"},
 *       "id": "hydrographysrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AN010", ["get", "F_CODE"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {"line-color": "#404040", "line-width": 4},
 *       "id": "transportationgroundcrv.1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AN010", ["get", "F_CODE"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#ffffff",
 *         "line-dasharray": [6, 6],
 *         "line-width": 2
 *       },
 *       "id": "transportationgroundcrv.2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AQ040", ["get", "F_CODE"] ],
 *         [ "==", 13, ["get", "TRS"] ]
 *       ],
 *       "layout": {"line-join": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 9, 13, 20 ]
 *       },
 *       "id": "transportationgroundcrv.0a",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AQ040", ["get", "F_CODE"] ],
 *         [ "==", 13, ["get", "TRS"] ]
 *       ],
 *       "layout": {"line-join": "round"},
 *       "paint": {
 *         "line-color": "#ffffff",
 *         "line-width": [ "step", ["zoom"], 1, 8, 6, 13, 14 ]
 *       },
 *       "id": "transportationgroundcrv.0b",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 3, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 5, 13, 12 ]
 *       },
 *       "id": "transportationgroundcrv.3",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 4, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 5, 13, 12 ]
 *       },
 *       "id": "transportationgroundcrv.5",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 5, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 3.5, 13, 9 ]
 *       },
 *       "id": "transportationgroundcrv.7",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 5, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#ffffff",
 *         "line-width": [ "step", ["zoom"], 1, 8, 2, 13, 6 ]
 *       },
 *       "id": "transportationgroundcrv.8",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 4, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#cb171a",
 *         "line-width": [ "step", ["zoom"], 1, 8, 3, 13, 8 ]
 *       },
 *       "id": "transportationgroundcrv.6",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 3, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#ff0000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 3, 13, 8 ]
 *       },
 *       "id": "transportationgroundcrv.4",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [ "!=", "No Information", ["get", "ZI005_FNA"] ],
 *       "layout": {
 *         "text-field": "{ZI005_FNA}",
 *         "text-size": 10,
 *         "text-font": ["Metropolis Bold"],
 *         "symbol-placement": "line"
 *       },
 *       "paint": {
 *         "text-halo-color": "#ffffff",
 *         "text-halo-width": 2,
 *         "text-color": "#000000"
 *       },
 *       "id": "transportationgroundcrv.9",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "UtilityInfrastructureCrv",
 *       "filter": [ "==", "AT005", ["get", "F_CODE"] ],
 *       "paint": {
 *         "line-color": "#473895",
 *         "line-width": [ "step", ["zoom"], 1, 8, 1, 13, 4 ]
 *       },
 *       "id": "utilityinfrastructurecrv",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "CulturePnt",
 *       "layout": {"icon-image": "mosque_b", "icon-size": 0.4},
 *       "paint": {"icon-opacity": 1},
 *       "id": "culturepnt",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "StructurePnt",
 *       "filter": [ "!=", "No Information", ["get", "ZI005_FNA"] ],
 *       "layout": {
 *         "icon-image": "square",
 *         "icon-size": 0.15,
 *         "text-field": "{ZI005_FNA}",
 *         "text-size": 14,
 *         "text-offset": [0, 1.5],
 *         "text-font": ["Metropolis Bold"]
 *       },
 *       "paint": {
 *         "icon-opacity": 1,
 *         "text-halo-color": "#ffffff",
 *         "text-halo-width": 2,
 *         "text-color": "#000000"
 *       },
 *       "id": "structurepnt",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "circle",
 *       "source-layer": "UtilityInfrastructurePnt",
 *       "paint": {
 *         "circle-radius": 3,
 *         "circle-opacity": 0.5,
 *         "circle-stroke-color": "#000000",
 *         "circle-stroke-width": 1,
 *         "circle-color": "#ffffff"
 *       },
 *       "id": "utilityinfrastructurepnt",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "FacilityPnt",
 *       "layout": {"icon-image": "circle_b", "icon-size": 0.2},
 *       "paint": {"icon-opacity": 1},
 *       "id": "facilitypnt",
 *       "source": "daraa"
 *     }
 *   ]
 * }
 * ```
 *     </code>
 *     <p>Example style information file
 *     <p><code>
 * ```json
 * {
 *   "title" : "topographic",
 *   "links" : [ {
 *     "rel" : "self",
 *     "type" : "application/json",
 *     "title" : "This document",
 *     "href" : "https://demo.ldproxy.net/daraa/styles/topographic/metadata?f=json"
 *   }, {
 *     "rel" : "alternate",
 *     "type" : "text/html",
 *     "title" : "This document as HTML",
 *     "href" : "https://demo.ldproxy.net/daraa/styles/topographic/metadata?f=html"
 *   } ],
 *   "id" : "topographic",
 *   "scope" : "style",
 *   "stylesheets" : [ {
 *     "title" : "Mapbox",
 *     "version" : "8",
 *     "specification" : "https://docs.mapbox.com/mapbox-gl-js/style-spec/",
 *     "native" : true,
 *     "link" : {
 *       "rel" : "stylesheet",
 *       "type" : "application/vnd.mapbox.style+json",
 *       "title" : "Style in format 'Mapbox'",
 *       "href" : "https://demo.ldproxy.net/daraa/styles/topographic?f=mbs"
 *     }
 *   } ],
 *   "layers" : [ {
 *     "id" : "background"
 *   }, {
 *     "id" : "agriculturesrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/AgricultureSrf/items"
 *     }
 *   }, {
 *     "id" : "vegetationsrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/VegetationSrf/items"
 *     }
 *   }, {
 *     "id" : "settlementsrf.1",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/SettlementSrf/items"
 *     }
 *   }, {
 *     "id" : "settlementsrf.2",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/SettlementSrf/items"
 *     }
 *   }, {
 *     "id" : "militarysrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/MilitarySrf/items"
 *     }
 *   }, {
 *     "id" : "culturesrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/CultureSrf/items"
 *     }
 *   }, {
 *     "id" : "hydrographycrv",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/HydrographyCrv/items"
 *     }
 *   }, {
 *     "id" : "hydrographysrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/HydrographySrf/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.0a",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.0b",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.1",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.2",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.3",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.4",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.5",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.6",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.7",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.8",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.9",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "utilityinfrastructurecrv",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/UtilityInfrastructureCrv/items"
 *     }
 *   }, {
 *     "id" : "culturepnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/CulturePnt/items"
 *     }
 *   }, {
 *     "id" : "structurepnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/StructurePnt/items"
 *     }
 *   }, {
 *     "id" : "utilityinfrastructurepnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/UtilityInfrastructurePnt/items"
 *     }
 *   }, {
 *     "id" : "facilitypnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/FacilityPnt/items"
 *     }
 *   } ]
 * }
 * ```
 *     </code>
 * @examplesDe Beispiel für die Angaben in der Konfigurationsdatei:
 *     <p><code>
 * ```yaml
 * - buildingBlock: STYLES
 *   enabled: true
 *   styleEncodings:
 *   - Mapbox
 *   - HTML
 *   deriveCollectionStyles: true
 *   managerEnabled: false
 *   validationEnabled: false
 * ```
 *     </code>
 *     <p>Beispiel für ein MapLibre-Stylesheet mit Layerauswahldialog:
 *     <p><code>
 * ```json
 * {
 *   "bearing": 0.0,
 *   "version": 8,
 *   "pitch": 0.0,
 *   "name": "topographic",
 *   "center": [36.1033, 32.6264],
 *   "zoom": 12.241790506353492,
 *   "metadata": {
 *     "ldproxy:layerControl": {
 *       "opened": true,
 *       "entries": [
 *         {
 *           "id": "Basemap",
 *           "type": "radio-group",
 *           "entries": ["Grey Background", "OSM"]
 *         },
 *         {
 *           "id"         : "Agriculture (Surfaces)",
 *           "type"       : "merge-group"           ,
 *           "sourceLayer": "AgricultureSrf"
 *         },
 *         {
 *           "id"         : "Cultural (Points)",
 *           "type"       : "merge-group"      ,
 *           "sourceLayer": "CulturePnt"
 *         },
 *         {
 *           "id"         : "Cultural (Surfaces)",
 *           "type"       : "merge-group"        ,
 *           "sourceLayer": "CultureSrf"
 *         },
 *         {
 *           "id"         : "Facility (Points)",
 *           "type"       : "merge-group"      ,
 *           "sourceLayer": "FacilityPnt"
 *         },
 *         {
 *           "id"         : "Hydrography (Curves)",
 *           "type"       : "merge-group"         ,
 *           "sourceLayer": "HydrographyCrv"
 *         },
 *         {
 *           "id"         : "Hydrography (Surfaces)",
 *           "type"       : "merge-group"           ,
 *           "sourceLayer": "HydrographySrf"
 *         },
 *         {
 *           "id"         : "Military (Surfaces)",
 *           "type"       : "merge-group"        ,
 *           "sourceLayer": "MilitarySrf"
 *         },
 *         {
 *           "id"         : "Settlement (Surfaces)",
 *           "type"       : "merge-group"          ,
 *           "sourceLayer": "SettlementSrf"
 *         },
 *         {
 *           "id"         : "Structure (Points)",
 *           "type"       : "merge-group"       ,
 *           "sourceLayer": "StructurePnt"
 *         },
 *         {
 *           "id": "Transportation - Ground (Curves)",
 *           "type": "group",
 *           "entries": [
 *             {
 *               "id": "National Road",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "National2", "zoom": 8},
 *                 {"id": "National1", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Secondary Road",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "Secondary2", "zoom": 8},
 *                 {"id": "Secondary1", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Local Road",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "Local1", "zoom": 8},
 *                 {"id": "Local2", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Bridge",
 *               "type": "merge-group",
 *               "entries": [
 *                 {"id": "Bridge1", "zoom": 8},
 *                 {"id": "Bridge2", "zoom": 8}
 *               ]
 *             },
 *             {
 *               "id": "Railway",
 *               "type": "merge-group",
 *               "entries": ["Railway1", "Railway2"]
 *             },
 *             { "id": "Name", "type": "merge-group", "entries": ["Name"] }
 *           ]
 *         },
 *         {
 *           "id"         : "Utility Infrastructure (Points)",
 *           "type"       : "merge-group"                    ,
 *           "sourceLayer": "UtilityInfrastructurePnt"
 *         },
 *         {
 *           "id"         : "Utility Infrastructure (Curves)",
 *           "type"       : "merge-group"                    ,
 *           "sourceLayer": "UtilityInfrastructureCrv"
 *         },
 *         {
 *           "id"         : "Vegetation (Surfaces)",
 *           "type"       : "merge-group"          ,
 *           "sourceLayer": "VegetationSrf"
 *         }
 *       ]
 *     }
 *   },
 *   "sources": {
 *     "daraa": {
 *       "type": "vector",
 *       "tiles": ["{serviceUrl}/tiles/WebMercatorQuad/{z}/{y}/{x}?f=mvt"],
 *       "maxzoom": 16
 *     },
 *     "osm": {
 *       "type": "raster",
 *       "tiles": [
 *         "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
 *         "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png",
 *         "https://c.tile.openstreetmap.org/{z}/{x}/{y}.png"
 *       ],
 *       "tileSize": 256,
 *       "attribution": "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"
 *     }
 *   },
 *   "sprite": "{serviceUrl}/resources/sprites",
 *   "glyphs": "https://go-spatial.github.io/carto-assets/fonts/{fontstack}/{range}.pbf",
 *   "layers": [
 *     {
 *       "id": "Grey Background",
 *       "type": "background",
 *       "layout": {"visibility": "visible"},
 *       "paint": {"background-color": "#d3d3d3"}
 *     },
 *     {
 *       "id": "OSM",
 *       "type": "raster",
 *       "source": "osm",
 *       "layout": {"visibility": "none"}
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "AgricultureSrf",
 *       "paint": {"fill-color": "#7ac5a5"},
 *       "id": "agriculturesrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "VegetationSrf",
 *       "paint": {"fill-color": "#C2E4B9"},
 *       "id": "vegetationsrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "SettlementSrf",
 *       "paint": {"line-color": "#000000", "line-width": 2},
 *       "id": "settlementsrf.1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "SettlementSrf",
 *       "paint": {"fill-color": "#E8C3B2"},
 *       "id": "settlementsrf.2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "MilitarySrf",
 *       "paint": {"fill-color": "#f3602f", "fill-opacity": 0.5},
 *       "id": "militarysrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "CultureSrf",
 *       "paint": {"fill-color": "#ab92d2", "fill-opacity": 0.5},
 *       "id": "culturesrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "HydrographyCrv",
 *       "filter": [ "==", "BH140", ["get", "F_CODE"] ],
 *       "paint": {
 *         "line-color": "#00A0C6",
 *         "line-width": [ "step", ["zoom"], 1, 8, 2, 13, 4 ]
 *       },
 *       "id": "hydrographycrv",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "fill",
 *       "source-layer": "HydrographySrf",
 *       "filter": [ "==", "BH082", ["get", "F_CODE"] ],
 *       "paint": {"fill-color": "#B0E1ED", "fill-outline-color": "#00A0C6"},
 *       "id": "hydrographysrf",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AN010", ["get", "F_CODE"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {"line-color": "#404040", "line-width": 4},
 *       "id": "Railway1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AN010", ["get", "F_CODE"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#ffffff",
 *         "line-dasharray": [6, 6],
 *         "line-width": 2
 *       },
 *       "id": "Railway2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AQ040", ["get", "F_CODE"] ],
 *         [ "==", 13, ["get", "TRS"] ]
 *       ],
 *       "layout": {"line-join": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 9, 13, 20 ]
 *       },
 *       "id": "Bridge1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AQ040", ["get", "F_CODE"] ],
 *         [ "==", 13, ["get", "TRS"] ]
 *       ],
 *       "layout": {"line-join": "round"},
 *       "paint": {
 *         "line-color": "#ffffff",
 *         "line-width": [ "step", ["zoom"], 1, 8, 6, 13, 14 ]
 *       },
 *       "id": "Bridge2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 3, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 5, 13, 12 ]
 *       },
 *       "id": "National2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 4, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 5, 13, 12 ]
 *       },
 *       "id": "Secondary2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 5, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#000000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 3.5, 13, 9 ]
 *       },
 *       "id": "Local1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 5, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#ffffff",
 *         "line-width": [ "step", ["zoom"], 1, 8, 2, 13, 6 ]
 *       },
 *       "id": "Local2",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 4, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#cb171a",
 *         "line-width": [ "step", ["zoom"], 1, 8, 3, 13, 8 ]
 *       },
 *       "id": "Secondary1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [
 *         "all",
 *         [ "==", "AP030", ["get", "F_CODE"] ],
 *         [ "==", 3, ["get", "RIN_ROI"] ]
 *       ],
 *       "layout": {"line-join": "round", "line-cap": "round"},
 *       "paint": {
 *         "line-color": "#ff0000",
 *         "line-width": [ "step", ["zoom"], 1, 8, 3, 13, 8 ]
 *       },
 *       "id": "National1",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "TransportationGroundCrv",
 *       "filter": [ "!=", "No Information", ["get", "ZI005_FNA"] ],
 *       "layout": {
 *         "text-field": "{ZI005_FNA}",
 *         "text-size": 10,
 *         "text-font": ["Metropolis Bold"],
 *         "symbol-placement": "line"
 *       },
 *       "paint": {
 *         "text-halo-color": "#ffffff",
 *         "text-halo-width": 2,
 *         "text-color": "#000000"
 *       },
 *       "id": "Name",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "line",
 *       "source-layer": "UtilityInfrastructureCrv",
 *       "filter": [ "==", "AT005", ["get", "F_CODE"] ],
 *       "paint": {
 *         "line-color": "#473895",
 *         "line-width": [ "step", ["zoom"], 1, 8, 1, 13, 4 ]
 *       },
 *       "id": "utilityinfrastructurecrv",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "CulturePnt",
 *       "layout": {"icon-image": "mosque_b", "icon-size": 0.4},
 *       "paint": {"icon-opacity": 1},
 *       "id": "culturepnt",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "StructurePnt",
 *       "filter": [ "!=", "No Information", ["get", "ZI005_FNA"] ],
 *       "layout": {
 *         "icon-image": "square",
 *         "icon-size": 0.15,
 *         "text-field": "{ZI005_FNA}",
 *         "text-size": 14,
 *         "text-offset": [0, 1.5],
 *         "text-font": ["Metropolis Bold"]
 *       },
 *       "paint": {
 *         "icon-opacity": 1,
 *         "text-halo-color": "#ffffff",
 *         "text-halo-width": 2,
 *         "text-color": "#000000"
 *       },
 *       "id": "structurepnt",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "circle",
 *       "source-layer": "UtilityInfrastructurePnt",
 *       "paint": {
 *         "circle-radius": 3,
 *         "circle-opacity": 0.5,
 *         "circle-stroke-color": "#000000",
 *         "circle-stroke-width": 1,
 *         "circle-color": "#ffffff"
 *       },
 *       "id": "utilityinfrastructurepnt_circle",
 *       "source": "daraa"
 *     },
 *     {
 *       "type": "symbol",
 *       "source-layer": "FacilityPnt",
 *       "layout": {"icon-image": "circle_b", "icon-size": 0.2},
 *       "paint": {"icon-opacity": 1},
 *       "id": "facilitypnt",
 *       "source": "daraa"
 *     }
 *   ]
 * }
 * ```
 *     </code>
 *     <p>Beispiel für eine Style-Metadaten-Datei:
 *     <p><code>
 * ```json
 * {
 *   "title" : "topographic",
 *   "links" : [ {
 *     "rel" : "self",
 *     "type" : "application/json",
 *     "title" : "Dieses Dokument",
 *     "href" : "https://demo.ldproxy.net/daraa/styles/topographic/metadata?f=json"
 *   }, {
 *     "rel" : "alternate",
 *     "type" : "text/html",
 *     "title" : "Dieses Dokument als HTML",
 *     "href" : "https://demo.ldproxy.net/daraa/styles/topographic/metadata?f=html"
 *   } ],
 *   "id" : "topographic",
 *   "scope" : "style",
 *   "stylesheets" : [ {
 *     "title" : "Mapbox",
 *     "version" : "8",
 *     "specification" : "https://docs.mapbox.com/mapbox-gl-js/style-spec/",
 *     "native" : true,
 *     "link" : {
 *       "rel" : "stylesheet",
 *       "type" : "application/vnd.mapbox.style+json",
 *       "title" : "Style im Format 'Mapbox'",
 *       "href" : "https://demo.ldproxy.net/daraa/styles/topographic?f=mbs"
 *     }
 *   } ],
 *   "layers" : [ {
 *     "id" : "background"
 *   }, {
 *     "id" : "agriculturesrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/AgricultureSrf/items"
 *     }
 *   }, {
 *     "id" : "vegetationsrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/VegetationSrf/items"
 *     }
 *   }, {
 *     "id" : "settlementsrf.1",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/SettlementSrf/items"
 *     }
 *   }, {
 *     "id" : "settlementsrf.2",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/SettlementSrf/items"
 *     }
 *   }, {
 *     "id" : "militarysrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/MilitarySrf/items"
 *     }
 *   }, {
 *     "id" : "culturesrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/CultureSrf/items"
 *     }
 *   }, {
 *     "id" : "hydrographycrv",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/HydrographyCrv/items"
 *     }
 *   }, {
 *     "id" : "hydrographysrf",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/HydrographySrf/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.0a",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.0b",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.1",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.2",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.3",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.4",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.5",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.6",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.7",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.8",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "transportationgroundcrv.9",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/TransportationGroundCrv/items"
 *     }
 *   }, {
 *     "id" : "utilityinfrastructurecrv",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/UtilityInfrastructureCrv/items"
 *     }
 *   }, {
 *     "id" : "culturepnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/CulturePnt/items"
 *     }
 *   }, {
 *     "id" : "structurepnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/StructurePnt/items"
 *     }
 *   }, {
 *     "id" : "utilityinfrastructurepnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/UtilityInfrastructurePnt/items"
 *     }
 *   }, {
 *     "id" : "facilitypnt",
 *     "type" : "geometry",
 *     "sampleData" : {
 *       "rel" : "start",
 *       "href" : "https://demo.ldproxy.net/daraa/collections/FacilityPnt/items"
 *     }
 *   } ]
 * }
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "STYLES")
@JsonDeserialize(builder = ImmutableStylesConfiguration.Builder.class)
public interface StylesConfiguration extends ExtensionConfiguration, CachingConfiguration {

  abstract class Builder extends ExtensionConfiguration.Builder {}

  /**
   * @langEn List of enabled stylesheet encodings. Supported are Mapbox/MapLibre Style ("Mapbox"),
   *     OGC SLD 1.0 ("SLD 1.0"), OGC SLD 1.1 ("SLD 1.1"), QGIS QML ("QGIS"), ArcGIS LYR ("ArcGIS"),
   *     ArcGIS LYRX ("ArcGIS Pro"), 3D Tiles ("3D Tiles") and HTML ("HTML"). HTML is an output only
   *     encoding for web maps that requires a *Mapbox/MapLibre Style* stylesheet. For details see
   *     conformance classes *Mapbox Style*, *OGC SLD 1.0*, *OGC SLD 1.1* und *HTML*. **Upcoming
   *     change** Currently there is no way to disable the defaults "Mapbox" and "HTML". It is
   *     planned to change this in v5, then the defaults need to be repeated, if you want to add
   *     additional encodings.
   * @langDe Steuert, welche Formate für Stylesheets unterstützt werden sollen. Zur Verfügung stehen
   *     Mapbox/MapLibre Style ("Mapbox"), OGC SLD 1.0 ("SLD 1.0"), OGC SLD 1.1 ("SLD 1.1"), QGIS
   *     QML ("QGIS"), ArcGIS LYR ("ArcGIS"), ArcGIS LYRX ("ArcGIS Pro"), 3D Tiles ("3D Tiles") und
   *     HTML ("HTML"). HTML ist ein reines Ausgabeformat im Sinne einer Webmap und wird nur für
   *     Styles unterstützt, für die ein Stylesheet im Format Mapbox/MapLibre Style verfügbar ist.
   *     Siehe die Konformitätsklassen "Mapbox Style", "OGC SLD 1.0", "OGC SLD 1.1" und "HTML".
   *     **Kommende Änderung** Aktuell gibt es keinen Weg die Defaults "Mapbox" und "HTML" zu
   *     deaktivieren. Es ist geplant, das in v5 zu ändern, die Defaults müssen dann wiederholt
   *     werden, wenn man zusätzliche Encodings angeben will.
   * @default [ "Mapbox", "HTML" ]
   */
  List<String> getStyleEncodings();

  /**
   * @langEn Option to manage styles using POST, PUT and DELETE. If `styleInfosOnCollection` is
   *     enabled, style information may be created and updated using PATCH. Siehe die
   *     Konformitätsklasse "Manage styles".
   * @langDe Steuert, ob die Styles über POST, PUT und DELETE verwaltet werden können. Siehe die
   *     Konformitätsklasse "Manage styles".
   * @default false
   */
  @Nullable
  Boolean getManagerEnabled();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isManagerEnabled() {
    return Objects.equals(getManagerEnabled(), true);
  }

  /**
   * @langEn Option to validate styles when using POST and PUT by setting the header `Prefer`. For
   *     details see conformance class *Validation of styles*.
   * @langDe Steuert, ob bei POST und PUT von Styles die Validierung der Styles über den Header
   *     `Prefer` unterstützt werden soll. Siehe die Konformitätsklasse "Validation of styles".
   * @default false
   */
  @Nullable
  Boolean getValidationEnabled();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isValidationEnabled() {
    return Objects.equals(getValidationEnabled(), true);
  }

  @Nullable
  Boolean getUseIdFromStylesheet();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean shouldUseIdFromStylesheet() {
    return Objects.equals(getUseIdFromStylesheet(), true);
  }

  /**
   * @langEn Only applies to styles in Mapbox Style format. Controls whether the styles at the
   *     collection level should be derived from the styles in the parent style collection. The
   *     prerequisite is that the name of the `source` in the stylesheet corresponds to `{apiId}`
   *     and the name of the `source-layer` corresponds to `{collectionId}`. If a style is to be
   *     used for displaying features in the FEATURES_HTML building block, the option should be
   *     enabled.
   * @langDe Nur wirksam bei Styles im Format Mapbox Style. Steuert, ob die Styles auf der Ebene der
   *     Collections aus den Styles aus der übergeordneten Style-Collection abgeleitet werden
   *     sollen. Voraussetzung ist, dass der Name der `source` im Stylesheet der `{apiId}`
   *     entspricht und der Name der `source-layer` der `{collectionId}`. Sofern ein Style für die
   *     Darstellung von Features im Baustein FEATURES_HTML verwendet werden soll, sollte die Option
   *     aktiviert sein.
   * @default false
   */
  @Nullable
  Boolean getDeriveCollectionStyles();

  /**
   * @langEn Option to support popups in web maps for *Mapbox Style* styles that show attributes for
   *     the top-most object.
   * @langDe Steuert, ob bei Webkarten zu Styles im Format Mapbox Style ein Popup mit den Attributen
   *     zum obersten Objekt angezeigt werden soll.
   * @default true
   */
  @Nullable
  Boolean getWebmapWithPopup();

  /**
   * @langEn Option to support layer controls in web maps for *Mapbox Style* styles. Allows to
   *     collectively enable and disable all layers for a certain feature collection.
   * @langDe Steuert, ob bei Webkarten zu Styles im Format Mapbox Style die Layer ein- und
   *     ausgeschaltet werden können. Ein- und ausgeschaltet werden können jeweils gebündelt alle
   *     Layer zu einer Feature Collection.
   * @default false
   */
  @Nullable
  Boolean getWebmapWithLayerControl();

  /**
   * @langEn Option to support layer controls for additional layers like background maps. Requires
   *     `webmapWithLayerControl: true`.
   * @langDe Nur wirksam bei `webmapWithLayerControl: true`. Steuert, ob auch Kartenlayer, die nicht
   *     aus den Vector Tiles dieser API, z.B. eine Hintergrundkarte, ein- und ausgeschaltet werden
   *     können.
   * @default false
   */
  @Nullable
  Boolean getLayerControlAllLayers();

  /**
   * @langEn Option to add the `bounds` property to the vector source in a MapLibre style based on
   *     the bounding box of the dataset or collection.
   * @langDe Option zum Hinzufügen der Eigenschaft `bounds` zur Vektorquelle in einem
   *     MapLibre-Style, der auf der Bounding-Box des Datensatzes oder der Collection basiert.
   * @default true
   */
  @Nullable
  Boolean getAddBoundsToVectorSource();

  /**
   * @langEn Option to support an endpoint to access the legend of a style.
   * @langDe Option zur Unterstützung eines Endpunkts für den Zugriff auf die Legende eines Styles.
   * @default false
   */
  @Nullable
  Boolean getLegendEnabled();

  /**
   * @langEn Option to enable support for conditional processing of PUT requests, based on the time
   *     when the style was last updated. Such requests must include an `If-Unmodified-Since`
   *     header, otherwise they will be rejected. A style will only be changed, if the style was not
   *     changed since the timestamp in the header (or if no last modification time is known for the
   *     style).
   *     <p>The setting is ignored, if `optimisticLockingETag` is enabled.
   *     <p>The option does not affect DELETE requests as each stylesheet of a style will have its
   *     own modification timestamp.
   * @langDe Option zur Aktivierung der Unterstützung für die bedingte Verarbeitung von
   *     PUT-Anfragen, basierend auf der Zeit, zu der der Style zuletzt aktualisiert wurde. Solche
   *     Anfragen müssen einen `If-Unmodified-Since`-Header enthalten, andernfalls werden sie
   *     zurückgewiesen. Ein Style wird nur dann geändert, wenn der Style seit dem Zeitstempel im
   *     Header nicht geändert wurde (oder wenn kein letzter Änderungszeitpunkt für den Style
   *     bekannt ist).
   *     <p>Die Option wird ignoriert, wenn `optimisticLockingETag` aktiviert ist.
   *     <p>Diese Option betrifft keine DELETE-Anfragen, da jedes Stylesheet eines Styles einen
   *     Änderungszeitpunkt hat.
   * @default false
   * @since v3.5
   */
  @Nullable
  Boolean getOptimisticLockingLastModified();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean supportsLastModified() {
    return Objects.equals(getOptimisticLockingLastModified(), true);
  }

  /**
   * @langEn Option to enable support for conditional processing of PUT requests, based on a strong
   *     Entity Tag (ETag) of the style. Such requests must include an `If-Match` header, otherwise
   *     they will be rejected. A style will only be changed, if the style matches the Etag(s) in
   *     the header.
   *     <p>The option does not affect DELETE requests as each stylesheet of a style will have its
   *     own ETag.
   * @langDe Option zur Aktivierung der Unterstützung für die bedingte Verarbeitung von
   *     PUT-Anfragen, basierend auf einem starken Entity Tag (ETag) des Styles. Solche Anfragen
   *     müssen einen `If-Match`-Header enthalten, andernfalls werden sie zurückgewiesen. Ein Style
   *     wird nur dann geändert, wenn der aktuelle ETag des Styles zu den ETag(s) im Header passt.
   *     <p>Diese Option betrifft keine DELETE-Anfragen, da jedes Stylesheet eines Styles einen
   *     eigenen ETag hat.
   * @default false
   * @since v3.5
   */
  @Nullable
  Boolean getOptimisticLockingETag();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean supportsEtag() {
    return Objects.equals(getOptimisticLockingETag(), true);
  }

  @Override
  default Builder getBuilder() {
    return new ImmutableStylesConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ImmutableStylesConfiguration.Builder builder =
        ((ImmutableStylesConfiguration.Builder) source.getBuilder()).from(source).from(this);

    List<String> styleEncodings =
        Lists.newArrayList(((StylesConfiguration) source).getStyleEncodings());
    getStyleEncodings()
        .forEach(
            styleEncoding -> {
              if (!styleEncodings.contains(styleEncoding)) {
                styleEncodings.add(styleEncoding);
              }
            });
    builder.styleEncodings(styleEncodings);

    return builder.build();
  }
}
