/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock FEATURES_HTML
 * @examplesEn Example of the specifications in the configuration file for the entire API (from the
 *     API for [Topographic Data in Daraa, Syria](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_HTML
 *   enabled: true
 *   style: 'topographic-with-basemap'
 * ```
 *     </code>
 *     <p>Example of the specifications in the configuration file for a feature collection:
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_HTML
 *   itemLabelFormat: '{{ZI005_FNA}}'
 *   transformations:
 *     F_CODE:
 *       codelist: f_code
 *     ZI001_SDV:
 *       dateFormat: MM/dd/yyyy[', 'HH:mm:ss[' 'z]]
 *     RTY:
 *       codelist: rty
 *     FCSUBTYPE:
 *       codelist: fcsubtype
 *     TRS:
 *       codelist: trs
 *     RIN_ROI:
 *       codelist: roi
 *     ZI016_WTC:
 *       codelist: wtc
 *     RLE:
 *       codelist: rle
 *     LOC:
 *       codelist: loc
 * ```
 *     </code>
 *     <p>Example of using CesiumJS for building data that is partially composed of building
 *     components. The floor slab is used as a fallback:
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_HTML
 *   mapClientType: CESIUM
 *   geometryProperties:
 *   - consistsOfBuildingPart.lod1Solid
 *   - lod1Solid
 *   - lod1GroundSurface
 * ```
 *     </code>
 * @examplesDe Beispiel für die Angaben in der Konfigurationsdatei für die gesamte API (aus der API
 *     für [Topographische Daten in Daraa, Syrien](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_HTML
 *   enabled: true
 *   style: 'topographic-with-basemap'
 * ```
 *     </code>
 *     <p>Beispiel für die Angaben in der Konfigurationsdatei für eine Feature Collection:
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_HTML
 *   itemLabelFormat: '{{ZI005_FNA}}'
 *   transformations:
 *     F_CODE:
 *       codelist: f_code
 *     ZI001_SDV:
 *       dateFormat: MM/dd/yyyy[', 'HH:mm:ss[' 'z]]
 *     RTY:
 *       codelist: rty
 *     FCSUBTYPE:
 *       codelist: fcsubtype
 *     TRS:
 *       codelist: trs
 *     RIN_ROI:
 *       codelist: roi
 *     ZI016_WTC:
 *       codelist: wtc
 *     RLE:
 *       codelist: rle
 *     LOC:
 *       codelist: loc
 * ```
 *     </code>
 *     <p>Beispiel für die Verwendung von CesiumJS für Gebäudedaten, die teilweise aus Bauteilen
 *     zusammengesetzt sind. Als Fallback wird die Bodenplatte verwendet:
 *     <p><code>
 * ```yaml
 * - buildingBlock: FEATURES_HTML
 *   mapClientType: CESIUM
 *   geometryProperties:
 *   - consistsOfBuildingPart.lod1Solid
 *   - lod1Solid
 *   - lod1GroundSurface
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "FEATURES_HTML")
@JsonDeserialize(builder = ImmutableFeaturesHtmlConfiguration.Builder.class)
public interface FeaturesHtmlConfiguration
    extends ExtensionConfiguration, PropertyTransformations, ProfilesConfiguration {

  abstract class Builder extends ExtensionConfiguration.Builder {}

  enum POSITION {
    AUTO,
    TOP,
    RIGHT
  }

  /**
   * @default true
   */
  @Nullable
  @Override
  Boolean getEnabled();

  /**
   * @langEn Can be `TOP`, `RIGHT` or `AUTO`. `AUTO` is the default, it chooses `TOP` when any
   *     nested objects are found and `RIGHT` otherwise.
   * @langDe Mögliche Werte sind `TOP`, `RIGHT` und `AUTO`. `AUTO` ist der Default, es wählt
   *     automatisch `TOP` wenn verschachtelte Objekte gefunden werden und sonst `RIGHT`.
   * @default `AUTO`
   */
  @Nullable
  POSITION getMapPosition();

  /**
   * @langEn Define how the feature label for HTML is formed. Default is the feature id. Property
   *     names in double curly braces will be replaced with the corresponding value.
   * @langDe Steuert, wie der Titel eines Features in der HTML-Ausgabe gebildet wird. Standardmäßig
   *     ist der Titel der Identifikator. In der Angabe können über die Angabe des Attributnamens in
   *     doppelt-geschweiften Klammern Ersetzungspunkte für die Attribute des Features verwendet
   *     werden. Es können nur Attribute verwendet werden, die nur einmal pro Feature vorkommen
   *     können. Neben einer direkten Ersetzung mit dem Attributwert können auch
   *     [Filter](../../providers/details/transformations.html#examples-for-stringformat) angewendet
   *     werden. Ist ein Attribut `null`, dann wird der Ersetzungspunkt durch einen leeren String
   *     ersetzt.
   * @default `{{id}}`
   */
  Optional<String> getFeatureTitleTemplate();

  @JsonSerialize(converter = IgnoreLinksWildcardSerializer.class)
  @Override
  Map<String, List<PropertyTransformation>> getTransformations();

  /**
   * @langEn The map client library to use to display features in the HTML representation. The
   *     default is MapLibre GL (`MAP_LIBRE`). Cesium (`CESIUM`) can be used for displaying 3D
   *     features on a globe, if [Features - glTF](features_-_gltf.md) is enabled.
   * @langDe Auswahl des in den Ressourcen "Features" und "Feature" zu verwendenden Map-Clients. Der
   *     Standard ist MapLibre GL JS. Alternativ wird für 3D-Daten auch `CESIUM` unterstützt, wenn
   *     [Features - glTF](features_-_gltf.md) aktiviert ist.
   * @default `MAP_LIBRE`
   */
  @Nullable
  MapClient.Type getMapClientType();

  /**
   * @langEn An optional style in the style repository to use for the map in the HTML representation
   *     of a feature or feature collection. The style should render all data. If set to `DEFAULT`,
   *     the `defaultStyle` configured in the [HTML building block](html.md) is used. If the map
   *     client is MapLibre, the style must be available in the Mapbox format. If the style is set
   *     to `NONE`, a simple wireframe style will be used with OpenStreetMap as a basemap. If the
   *     map client is Cesium, the style must be available in the 3D Tiles format. If the style is
   *     set to `NONE`, the standard 3D Tiles styling is used.
   * @langDe Ein Style im Style-Repository, der standardmäßig in Karten mit den Features verwendet
   *     werden soll. Der Style sollte alle Daten abdecken. Bei `DEFAULT` wird der `defaultStyle`
   *     aus dem [HTML-Baustein](html.md) verwendet. Handelt es sich bei dem Kartenclient um
   *     MapLibre, muss der Style im Mapbox-Format verfügbar sein. Wenn der Style auf `NONE` gesetzt
   *     ist, wird ein einfacher Wireframe Style mit OpenStreetMap als Basiskarte verwendet. Handelt
   *     es sich bei dem Kartenclient um Cesium, muss der Style im 3D-Tiles-Format verfügbar sein.
   *     Ist der Style auf `NONE` gesetzt, wird das Standard 3D Tiles Styling verwendet.
   * @default `DEFAULT`
   */
  @Nullable
  String getStyle();

  /**
   * @langEn If `true`, any `minzoom` or `maxzoom` members are removed from the GeoJSON layers. The
   *     value is ignored, if the map client is not MapLibre or `style` is `NONE`.
   * @langDe Bei `true` werden aus dem in `style` angegebenen Style die `minzoom`- und
   *     `maxzoom`-Angaben bei den Layer-Objekten entfernt, damit die Features in allen Zoomstufen
   *     angezeigt werden. Diese Option sollte nicht gewählt werden, wenn der Style unterschiedliche
   *     Präsentationen je nach Zoomstufe vorsieht, da ansonsten alle Layer auf allen Zoomstufen
   *     gleichzeitig angezeigt werden.
   * @default false
   */
  @Nullable
  Boolean getRemoveZoomLevelConstraints();

  /**
   * @langEn This option works only for CesiumJS as map client. By default, the geometry identified
   *     in the provider as PRIMARY_GEOMETRY is used for representation on the map. This option
   *     allows multiple geometry properties to be specified in a list. The first geometry property
   *     set for a feature will be used.
   * @langDe Diese Option wirkt nur für CesiumJS als Map-Client. Als Standard wird die im Provider
   *     als PRIMARY_GEOMETRY identifizierte Geometrie für die Darstellung in der Karte verwendet.
   *     Diese Option ermöglicht es, mehrere Geometrieeigenschaften anzugeben in einer Liste
   *     anzugeben. Die erste Geometrieeigenschaft, die für ein Feature gesetzt ist, wird dabei
   *     verwendet.
   * @default []
   */
  @Deprecated(forRemoval = true, since = "4.2")
  @Nullable
  List<String> getGeometryProperties();

  /**
   * @langEn This option can be used to set a custom maximum value for the `limit` parameter for the
   *     HTML output. If no value is specified, the value from the Features Core building block
   *     applies. When using CesiumJS as a map client, a value of 100 is recommended.
   * @langDe Mit dieser Option kann für die HTML-Ausgabe ein eigener Maximalwert für den Parameter
   *     `limit` gesetzt werden. Sofern kein Wert angegeben ist, so gilt der Wert aus dem Baustein
   *     "Features Core". Bei der Verwendung von CesiumJS als Map-Client wird ein Wert von 100
   *     empfohlen.
   * @default null
   */
  @Nullable
  Integer getMaximumPageSize();

  /**
   * @langEn If `true`, on the single item page any property that has a description in the provider
   *     schema will get an info icon with the description as a tooltip.
   * @langDe Bei `true` werden auf der Seite für einzelne Items für jedes Property mit einer
   *     Beschreibung im Provider-Schema Info-Icons mit einem Tooltip angezeigt.
   * @default true
   */
  @Nullable
  Boolean getPropertyTooltips();

  /**
   * @langEn If `true`, on the items page any property that has a description in the provider schema
   *     will get an info icon with the description in a tooltip.
   * @langDe Bei `true` werden auf der Seite für Items für jedes Property mit einer Beschreibung im
   *     Provider-Schema Info-Icons mit einem Tooltip angezeigt.
   * @default false
   */
  @Nullable
  Boolean getPropertyTooltipsOnItems();

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set, such
   *     as `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`.
   *     These defaults override the defaults specified in the resource-specific building block.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Diese
   *     Vorgaben haben Vorrang vor den im ressourcenspezifischen Baustein angegebenen
   *     Standardprofilen.
   * @since v4.2
   * @default {"rel": "rel-as-link", "val": "val-as-title"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  String LINK_WILDCARD = "*{objectType=Link}";

  @Value.Check
  default FeaturesHtmlConfiguration transformLinks() {
    if (!hasTransformation(
        LINK_WILDCARD, transformation -> transformation.getObjectReduceFormat().isPresent())) {

      Map<String, List<PropertyTransformation>> transformations =
          withTransformation(
              LINK_WILDCARD,
              new ImmutablePropertyTransformation.Builder()
                  .objectReduceFormat("<a href=\"{{href}}\">{{title}}</a>")
                  .build());

      return new ImmutableFeaturesHtmlConfiguration.Builder()
          .from(this)
          .transformations(transformations)
          .build();
    }

    return this;
  }

  class IgnoreLinksWildcardSerializer
      extends StdConverter<
          Map<String, List<PropertyTransformation>>, Map<String, List<PropertyTransformation>>> {

    @Override
    public Map<String, List<PropertyTransformation>> convert(
        Map<String, List<PropertyTransformation>> value) {
      if (value.containsKey(LINK_WILDCARD)
          && value.get(LINK_WILDCARD).stream()
              .anyMatch(transformation -> transformation.getObjectReduceFormat().isPresent())) {

        return value.entrySet().stream()
            .filter(
                entry ->
                    !Objects.equals(entry.getKey(), LINK_WILDCARD)
                        || entry.getValue().size() != 1
                        || entry.getValue().get(0).getObjectReduceFormat().isEmpty())
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
      }

      return value;
    }
  }

  @Override
  default Builder getBuilder() {
    return new ImmutableFeaturesHtmlConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableFeaturesHtmlConfiguration.Builder()
        .from(source)
        .from(this)
        .transformations(
            PropertyTransformations.super
                .mergeInto((PropertyTransformations) source)
                .getTransformations())
        .defaultProfiles(
            ProfilesConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
