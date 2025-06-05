/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.features.core.domain.FeatureFormatConfiguration;
import de.ii.ogcapi.features.jsonfg.domain.ImmutableJsonFgConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock JSON_FG
 * @examplesEn Example of the information in the configuration file for the entire API (from the API
 *     for [Topographic Data in Daraa, Syria](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   enabled: true
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>Additional information per feature collection with an attribute `F_CODE` (for which `role:
 *     TYPE` was set in the provider configuration) to set the object type:
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>This outputs the object type as follows for a value of "GB075" in the 'F_CODE' attribut:
 *     <p><code>
 * ```json
 * {
 *   "type": "Feature",
 *   "id": 1,
 *   "featureType": "nas:GB075",
 *   ...
 * }
 * ```
 *     </code>
 * @examplesDe Beispiel für die Angaben in der Konfigurationsdatei für die gesamte API (aus der API
 *     für [Topographische Daten in Daraa, Syrien](https://demo.ldproxy.net/daraa)):
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   enabled: true
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>Ergänzende Angaben pro Feature Collection mit einem Attribut `F_CODE` (für das in der
 *     Provider-Konfiguration `role: TYPE` gesetzt wurde), um die Objektart zu setzen:
 *     <p><code>
 * ```yaml
 * - buildingBlock: JSON_FG
 *   featureType:
 *   - nas:{{type}}
 * ```
 *     </code>
 *     <p>Hierdurch wird bei einem Wert von "GB075" im Attribut `F_CODE` die Objektart wie folgt
 *     ausgegeben:
 *     <p><code>
 * ```json
 * {
 *   "type": "Feature",
 *   "id": 1,
 *   "featureType": "nas:GB075",
 *   ...
 * }
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "JSON_FG")
@JsonDeserialize(builder = Builder.class)
public interface JsonFgConfiguration extends ExtensionConfiguration, FeatureFormatConfiguration {

  enum OPTION {
    featureType,
    featureSchema,
    time,
    place,
    coordRefSys,
    links,
    geometryDimension
  }

  /**
   * @langEn *Partially Deprecated* For schemas specific to the feature type, use `schemaCollection`
   *     and `schemaFeature`. Enables that links to the generic JSON-FG and GeoJSON JSON Schema
   *     documents are added to the JSON-FG response document. The links have the link relation type
   *     "describedby". The schemas can be used to validate the JSON document.
   * @langDe *Teilweise Deprecated* Für Objektart-spezifische Schemas siehe `schemaCollection` and
   *     `schemaFeature`. Aktiviert, dass Links zu den generischen JSON-FG und GeoJSON
   *     JSON-Schema-Dokumenten in das JSON-FG-Antwortdokument eingefügt werden. Die Links haben den
   *     Relationstyp "describedby". Die Schemas können zur Validierung des JSON-Dokuments verwendet
   *     werden.
   * @default false
   * @since v3.1
   */
  @Nullable
  Boolean getDescribedby();

  /**
   * @langEn The URI of a JSON Schema document describing a feature collection with the features of
   *     the collection/dataset. The schema will be referenced from JSON-FG feature collection
   *     responses by a link with the link relation type "describedby". The schemas can be used to
   *     validate the JSON document.
   * @langDe Die URI eines JSON-Schema-Dokuments, das eine Feature Collection mit den Features der
   *     Collection/des Datensatzes beschreibt. Das Schema wird von JSON-FG
   *     Feature-Collection-Responses durch einen Link mit dem Relationstyp "describedby"
   *     referenziert. Die Schemas können zur Validierung des JSON-Dokuments verwendet werden.
   * @default null
   * @since v3.5
   */
  @Nullable
  String getSchemaCollection();

  /**
   * @langEn The URI of a JSON Schema document describing a feature of the collection/dataset. The
   *     schema will be referenced from JSON-FG feature responses by a link with the link relation
   *     type "describedby". The schemas can be used to validate the JSON document.
   * @langDe Die URI eines JSON-Schema-Dokuments, das ein Feature der Collection/des Datensatzes
   *     beschreibt. Das Schema wird von JSON-FG Feature-Responses durch einen Link mit dem
   *     Relationstyp "describedby" referenziert. Die Schemas können zur Validierung des
   *     JSON-Dokuments verwendet werden.
   * @default null
   * @since v3.5
   */
  @Nullable
  String getSchemaFeature();

  /**
   * @langEn Activates the output of the coordinate reference system in a JSON member "coordRefSys"
   *     for features and feature collections. The coordinate reference system is identified by its
   *     OGC URI, for example, `http://www.opengis.net/def/crs/EPSG/0/25832` for ETRS89 / UTM 32N.
   * @langDe Aktiviert die Ausgabe des Koordinatenreferenzsystems in einem JSON-Member "coordRefSys"
   *     bei Features und Feature Collections. Das Koordinatenreferenzsystem wird identifiziert
   *     durch seine OGC URI, zum Beispiel `http://www.opengis.net/def/crs/EPSG/0/25832` für ETRS89
   *     / UTM 32N.
   * @default true
   * @since v3.1
   */
  @Nullable
  Boolean getCoordRefSys();

  /**
   * @langEn Activates support for the "compatibility=geojson" media type parameter. If the
   *     parameter is provided, JSON-FG features with a "place" member that is not `null` will also
   *     include a GeoJSON geometry in the "geometry" member in WGS 84. If the parameter is missing,
   *     the "geometry" member of a JSON-FG feature will be `null`, if the "place" member is not
   *     `null`.
   * @langDe Aktiviert die Unterstützung für den "compatibility=geojson" Media-Type-Parameter. Wenn
   *     der Parameter angegeben wird, enthalten JSON-FG-Features mit einem JSON-Member "place", das
   *     nicht `null` ist, auch eine GeoJSON-Geometrie im JSON-Member "geometry" im
   *     Koordinatenreferenzsystem WGS 84. Fehlt der Parameter, so ist "geometry" eines
   *     JSON-FG-Features `null`, wenn "place" nicht `null` ist.
   * @default true
   * @since v3.3
   */
  @Nullable
  Boolean getGeojsonCompatibility();

  /**
   * @langEn Features are often categorized by type. Typically, all features of the same type have
   *     the same schema and the same properties.
   *     <p>Many GIS clients depend on knowledge about the feature type when processing feature
   *     data. For example, when associating a style to a feature in order to render that feature on
   *     a map.
   *     <p>This option adds a "featureType" member with the specified value.
   *     <p>A value can include a template `{{type}}`, which will be replaced with the value of the
   *     feature property with `role: TYPE` in the provider schema of the feature type of the
   *     collection. The property must be of type `STRING`.
   *     <p>If the feature type in the provider schema includes an `objectType` value, the value
   *     will be used as the default. Otherwise, the default is `null`.
   * @langDe Features werden oft nach der Objektart kategorisiert. In der Regel haben alle Features
   *     derselben Art dasselbe Schema und dieselben Eigenschaften.
   *     <p>Viele GIS-Clients sind bei der Verarbeitung von Features auf das Wissen über den
   *     Objektart angewiesen. Zum Beispiel, wenn einem Feature ein Stil zugeordnet wird, um das
   *     Feature auf einer Karte darzustellen.
   *     <p>Diese Option fügt ein JSON-Member "featureType" mit dem angegebenen Wert hinzu.
   *     <p>Der Wert kann ein Template `{{type}}` enthalten, das durch den Wert der
   *     Objekteigenschaft mit `role: TYPE` im Provider-Schema der Objektart der Collection ersetzt
   *     wird. Die Eigenschaft muss vom Typ `STRING` sein.
   *     <p>Wenn der Objekttyp im Provider-Schema einen Wert für `objectType` hat, dann ist dieser
   *     Wert der Default. Ansonsten ist der Default `null`.
   * @default see description
   * @examplesAll 'Building'
   * @since v3.1
   */
  @Nullable
  Object getFeatureType();

  default String getEffectiveFeatureType(Optional<FeatureSchema> schema) {
    Object value = getFeatureType();
    if (value instanceof List) {
      if (!((List<?>) value).isEmpty()) {
        value = ((List<?>) value).get(0);
      } else {
        value = null;
      }
    }
    if (value instanceof String) {
      return (String) value;
    }
    if (Objects.isNull(value)) {
      return schema.flatMap(FeatureSchema::getObjectType).orElse(null);
    }
    return null;
  }

  /**
   * @langEn Adds the specified links to the `links` array of features. All values of the array must
   *     be a valid link object with `href` and `rel`.
   * @langDe Ergänzt den "links"-Array von Features um die angegebenen Links. Alle Werte des Arrays
   *     müssen ein gültiges Link-Objekt mit `href` und `rel` sein.
   * @default []
   * @since v3.1
   */
  @Nullable
  List<Link> getLinks();

  /**
   * @langEn The option allows selected JSON-FG extensions to be included in the GeoJSON encoding as
   *     well. Allowed values are: `describedby`, `featureType`, `featureSchema`, `time`, `place`,
   *     `coordRefSys`, `links`. `conformsTo` is only used in JSON-FG responses.
   * @langDe Die Option ermöglicht, dass ausgewählte JSON-FG-Erweiterungen auch im GeoJSON-Encoding
   *     berücksichtigt werden. Erlaubte Werte sind: `describedby`, `featureType`, `featureSchema`,
   *     `time`, `place`, `coordRefSys`, `links`. `conformsTo` wird nur in JSON-FG unterstützt.
   * @default []
   * @since v3.1
   */
  List<OPTION> getIncludeInGeoJson();

  @Override
  default Builder getBuilder() {
    return new ImmutableJsonFgConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ImmutableJsonFgConfiguration.Builder builder =
        new ImmutableJsonFgConfiguration.Builder().from(source).from(this);

    ImmutableJsonFgConfiguration src = (ImmutableJsonFgConfiguration) source;

    if (Objects.nonNull(getFeatureType())) builder.featureType(getFeatureType());
    else if (Objects.nonNull(src.getFeatureType())) builder.featureType(src.getFeatureType());

    return builder.build();
  }

  abstract class Builder extends ExtensionConfiguration.Builder {}
}
