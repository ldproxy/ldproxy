/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * @buildingBlock CITY_JSON
 * @langEn ### Prerequisites
 *     <p>The building block requires that the feature provider includes a type `building` that is
 *     mapped to a CityJSON Building feature. Properties of the type `building` are mapped to
 *     CityJSON as follows:
 *     <p><code>
 * - `consistsOfBuildingPart`: The value must be an object with the same properties as `building`.
 *   The object is encoded as a BuildingPart feature of the Building feature.
 * - `address`: The value must be an array of address objects. The following attributes are mapped
 *   to an Address object, all other properties are ignored:
 *   - `multiPoint`: a  MULTI_POINT geometry representing the address location
 *   - `ThoroughfareName`: a string
 *   - `ThoroughfareNumber`: a string
 *   - `LocalityName`: a string
 *   - `PostalCode`: a string
 *   - `AdministrativeArea`: a string
 *   - `CountryName`: a string
 * - `lod1Solid`: a closed MULTI_POLYGON geometry that represents the shell of the building at LoD 1.
 * - `lod2Solid`: a closed MULTI_POLYGON geometry that represents the shell of the building at LoD 2.
 * - `surfaces`: an array of semantic surface objects. The object must have a `surfaceType` and the
 *   values must be one of the following: `CeilingSurface`, `ClosureSurface`, `Door`, `FloorSurface`,
 *   `GroundSurface`, `InteriorWallSurface`, `OuterCeilingSurface`, `OuterFloorSurface`, `RoofSurface`,
 *   `WallSurface`, or `Window`. The object should have a property with a POLYGON or MULTI_POLYGON
 *   geometry that represents the surface geometry.
 * - all other properties: The property will be mapped to a CityJSON attribute, except for `gml_id` properties.
 *     </code>
 *     <p>The property of the `building` with the role `ID` will be used as the CityJSON id. Since
 *     the embedded building parts do not have a property with a role `ID`, the building part
 *     feature will use the value of a property `id` as the id of the CityJSON building part,
 *     otherwise a UUID will be generated. If `id` is provided, its values must be unique.
 *     <p>The [example](#examples) includes a sample type definition for the building features in a
 *     PostgreSQL feature provider based on the CityGML profile of the German surveying and mapping
 *     authorities.
 * @langDe ### Voraussetzungen
 *     <p>Der Baustein erfordert, dass der Feature-Provider einen Typ `building` enthält, der auf
 *     ein CityJSON-Building-Feature abgebildet wird. Eigenschaften des Typs `building` werden wie
 *     folgt auf CityJSON abgebildet:
 *     <p><code>
 * - `consistsOfBuildingPart`: Der Wert muss ein Objekt mit denselben Eigenschaften wie bei `building` sein.
 *   Das Objekt wird als ein BuildingPart-Feature zu dem Gebäude kodiert.
 * - `address`: Der Wert muss ein Array von Adressobjekten sein. Die folgenden Attribute werden abgebildet
 *   auf ein Address-Objekt abgebildet, alle anderen Eigenschaften werden ignoriert:
 *   - `multiPoint`: eine MULTI_POINT-Geometrie, die den Standort der Adresse darstellt
 *   - `ThoroughfareName`: eine Zeichenkette
 *   - `ThoroughfareNumber`: eine Zeichenkette
 *   - `LocalityName`: eine Zeichenkette
 *   - `PostalCode`: eine Zeichenkette
 *   - `AdministrativeArea`: eine Zeichenkette
 *   - `CountryName`: eine Zeichenkette
 * - `lod1Solid`: eine geschlossene MULTI_POLYGON-Geometrie, die die Hülle des Gebäudes bei LoD 1 darstellt.
 * - `lod2Solid`: eine geschlossene MULTI_POLYGON-Geometrie, die die Hülle des Gebäudes bei LoD 2 darstellt.
 * - `surfaces`: ein Array von semantischen Oberflächenobjekten. Das Objekt muss einen `surfaceType` haben und die
 *   Werte müssen einer der folgenden sein: `CeilingSurface`, `ClosureSurface`, `Door`, `FloorSurface`,
 *   `GroundSurface`, `InteriorWallSurface`, `OuterCeilingSurface`, `OuterFloorSurface`, `RoofSurface`,
 *   `WallSurface`, oder `Window`. Das Objekt sollte eine Eigenschaft mit einer POLYGON oder MULTI_POLYGON
 *   Geometrie, die die Flächengeometrie darstellt.
 * - alle anderen Eigenschaften: Die Eigenschaft wird auf ein CityJSON-Attribut abgebildet, mit Ausnahme der Eigenschaft "gml_id".
 *     </code>
 *     <p>Die Eigenschaft des `building`-Typs mit der Rolle `ID` wird als CityJSON-ID verwendet. Da
 *     die eingebetteten Gebäudeteile keine Eigenschaft mit der Rolle `ID` haben, wird - sofern
 *     vorhanden - der Wert einer Eigenschaft `id` als Id des CityJSON-Gebäudeteils verwendet;
 *     ansonsten wird eine UUID generiert. Die Werte der Eigenschaft `id` müssen eindeutig sein.
 *     <p>Das [Beispiel](#beispiele) enthält eine Typdefinition für Gebäudeobjekte in einem
 *     PostgreSQL-Feature-Provider auf Grundlage des CityGML-Profils der deutschen
 *     Vermessungsverwaltung.
 * @examplesAll API:
 *     <p><code>
 * ```yaml
 * - buildingBlock: CITY_JSON
 *   enabled: true
 *   textSequences: false
 *   version: V11
 * ```
 *     </code>
 *     <p>Feature Provider:
 *     <p><code>
 * ```yaml
 * types:
 *   building:
 *     label: Gebäude
 *     sourcePath: /building{filter=fk_buildingpart_parent IS NULL}
 *     type: OBJECT
 *     objectType: Building
 *     properties:
 *       id:
 *         label: Objekt-ID
 *         sourcePath: id
 *         type: INTEGER
 *         role: ID
 *       gml_id:
 *         label: ALKIS-ID
 *         sourcePath: gml_id
 *         type: STRING
 *       name:
 *         label: Name
 *         description: 'Nur bei Gebäudeeigennamen gesetzt.'
 *         sourcePath: feature_name
 *         type: STRING
 *       bbox:
 *         label: Bounding Box
 *         sourcePath: envelope
 *         type: GEOMETRY
 *         geometryType: POLYGON
 *         role: PRIMARY_GEOMETRY
 *       lod12GroundSurface:
 *         label: Grundfläche
 *         sourcePath: lod12groundsurface
 *         type: GEOMETRY
 *         geometryType: MULTI_POLYGON
 *       measuredHeight:
 *         label: Gebäudehöhe [m]
 *         description: 'Höhe des Gebäudes aus der Differenz in Metern zwischen dem höchsten Bezugspunkt und dem tiefsten Bezugspunkt des Gebäudes.'
 *         sourcePath: measuredheight
 *         type: FLOAT
 *       storeysAboveGround:
 *         label: Geschosse
 *         sourcePath: storeysaboveground
 *         type: INTEGER
 *       roofType:
 *         label: Dachform
 *         description: 'Die Bedeutung der einzelnen Werte ergibt aus der Codelist <a href="https://repository.gdi-de.org/schemas/adv/citygml/Codelisten/RoofTypeTypeAdV.xml" target="_blank">RoofTypeTypeAdV.xml</a>.'
 *         sourcePath: rooftype
 *         type: STRING
 *         constraints:
 *           codelist: RoofType
 *       creationDate:
 *         label: Erzeugungsdatum
 *         sourcePath: creation
 *         type: DATE
 *         role: PRIMARY_INSTANT
 *       function:
 *         label: Gebäudefunktion
 *         description: 'Die Bedeutung der einzelnen Werte ergibt aus der Codelist <a href="https://repository.gdi-de.org/schemas/adv/citygml/Codelisten/BuildingFunctionTypeAdV.xml" target="_blank">BuildingFunctionTypeAdV.xml</a>.'
 *         sourcePath: function
 *         type: STRING
 *         constraints:
 *           codelist: BuildingFunctionType
 *       externalReferences:
 *         label: Fachdatenverbindungen
 *         sourcePath: '[id=fk_feature]extref_building{sortKey=name}'
 *         type: OBJECT_ARRAY
 *         properties:
 *           name:
 *             label: Bezeichnung
 *             sourcePath: name
 *             type: STRING
 *           informationSystem:
 *             label: Informationssystem
 *             sourcePath: informationsystem
 *             type: STRING
 *       bezugspunktDach:
 *         label: Bezugspunkt Dach
 *         sourcePath: '[id=fk_feature]att_string_building{filter=name=''BezugspunktDach''}{sortKey=name}/value'
 *         type: STRING
 *         constraints:
 *           codelist: BezugspunktDach
 *       datenquelleBodenhoehe:
 *         label: Datenquelle Bodenhöhe
 *         sourcePath: '[id=fk_feature]att_string_building{filter=name=''DatenquelleBodenhoehe''}{sortKey=name}/value'
 *         type: STRING
 *         constraints:
 *           codelist: DatenquelleBodenhoehe
 *       datenquelleDachhoehe:
 *         label: Datenquelle Dachhöhe
 *         sourcePath: '[id=fk_feature]att_string_building{filter=name=''DatenquelleDachhoehe''}{sortKey=name}/value'
 *         type: STRING
 *         constraints:
 *           codelist: DatenquelleDachhoehe
 *       datenquelleLage:
 *         label: Datenquelle Lage
 *         sourcePath: '[id=fk_feature]att_string_building{filter=name=''DatenquelleLage''}{sortKey=name}/value'
 *         type: STRING
 *         constraints:
 *           codelist: DatenquelleLage
 *       gemeindeschluessel:
 *         label: Gemeindeschlüssel
 *         sourcePath: '[id=fk_feature]att_string_building{filter=name=''Gemeindeschluessel''}{sortKey=name}/value'
 *         type: STRING
 *       address:
 *         label: Adresse
 *         sourcePath: '[id=fk_feature]address{sortKey=id}'
 *         type: OBJECT_ARRAY
 *         properties:
 *           id:
 *             label: Objekt-ID
 *             sourcePath: id
 *             type: INTEGER
 *             transformations:
 *               remove: ALWAYS
 *           multiPoint:
 *             sourcePath: multipoint
 *             type: GEOMETRY
 *             geometryType: MULTI_POINT
 *             forcePolygonCCW: false
 *           ThoroughfareName:
 *             label: Straße
 *             sourcePath: street
 *             type: STRING
 *           ThoroughfareNumber:
 *             label: Hausnummer
 *             sourcePath: code
 *             type: STRING
 *           PostalCode:
 *             label: Postleitzahl
 *             sourcePath: postalcode
 *             type: STRING
 *           LocalityName:
 *             label: Stadt
 *             sourcePath: city
 *             type: STRING
 *           AdministrativeArea:
 *             label: Verwaltungsbezirk
 *             sourcePath: administrativearea
 *             type: STRING
 *           CountryName:
 *             label: Land
 *             sourcePath: country
 *             type: STRING
 *       lod1Solid:
 *         label: Geometrie (LoD 1)
 *         sourcePath: lod1solid
 *         type: GEOMETRY
 *         geometryType: MULTI_POLYGON
 *         forcePolygonCCW: false
 *       lod1TerrainIntersection:
 *         sourcePath: lod1terrainintersection
 *         type: GEOMETRY
 *         geometryType: MULTI_LINE_STRING
 *         forcePolygonCCW: false
 *       lod2Solid:
 *         label: Geometrie (LoD 2)
 *         sourcePath: lod2solid
 *         type: GEOMETRY
 *         geometryType: MULTI_POLYGON
 *         forcePolygonCCW: false
 *       surfaces:
 *         label: Begrenzungsflächen (LoD 2)
 *         sourcePath: '[id=fk_feature]building_surface{sortKey=gml_id}'
 *         type: OBJECT_ARRAY
 *         properties:
 *           gml_id:
 *             label: ID
 *             sourcePath: gml_id
 *             type: STRING
 *           creationDate:
 *             label: Erzeugungsdatum
 *             sourcePath: creation
 *             type: DATE
 *             transformations:
 *               dateFormat: 'yyyy-MM-dd'
 *           surfaceType:
 *             label: Flächentyp
 *             sourcePath: surface_type
 *             type: STRING
 *           lod2MultiSurface:
 *             label: Geometrie (LoD 2)
 *             sourcePath: geom
 *             type: GEOMETRY
 *             geometryType: MULTI_POLYGON
 *             forcePolygonCCW: false
 *       lod2TerrainIntersection:
 *         sourcePath: lod2terrainintersection
 *         type: GEOMETRY
 *         geometryType: MULTI_LINE_STRING
 *         forcePolygonCCW: false
 *       fk_buildingpart_parent:
 *         label: Gebäude-Objekt-ID
 *         sourcePath: fk_buildingpart_parent
 *         type: INTEGER
 *       consistsOfBuildingPart:
 *         label: Gebäudeteile
 *         sourcePath: '[id=fk_buildingpart_parent]building'
 *         type: OBJECT_ARRAY
 *         objectType: BuildingPart
 *         properties:
 *           id:
 *             label: Objekt-ID
 *             sourcePath: id
 *             type: INTEGER
 *           gml_id:
 *             label: ALKIS-ID
 *             sourcePath: gml_id
 *             type: STRING
 *           name:
 *             label: Name
 *             sourcePath: feature_name
 *             type: STRING
 *           lod12GroundSurface:
 *             sourcePath: lod12groundsurface
 *             type: GEOMETRY
 *             geometryType: MULTI_POLYGON
 *           measuredHeight:
 *             label: Gebäudehöhe [m]
 *             sourcePath: measuredheight
 *             type: FLOAT
 *           storeysAboveGround:
 *             label: Geschosse
 *             sourcePath: storeysaboveground
 *             type: INTEGER
 *           roofType:
 *             label: Dachform
 *             description: 'Die Bedeutung der einzelnen Werte ergibt aus der Codelist <a href="https://repository.gdi-de.org/schemas/adv/citygml/Codelisten/RoofTypeTypeAdV.xml" target="_blank">RoofTypeTypeAdV.xml</a>.'
 *             sourcePath: rooftype
 *             type: STRING
 *             constraints:
 *               codelist: RoofType
 *           creationDate:
 *             label: Erzeugungsdatum
 *             sourcePath: creation
 *             type: DATE
 *           function:
 *             label: Gebäudefunktion
 *             sourcePath: function
 *             type: STRING
 *             constraints:
 *               codelist: BuildingFunctionType
 *           externalReferences:
 *             label: Fachdatenverbindungen
 *             sourcePath: '[id=fk_feature]extref_building{sortKey=name}'
 *             type: OBJECT_ARRAY
 *             properties:
 *               name:
 *                 label: Bezeichnung
 *                 sourcePath: name
 *                 type: STRING
 *               informationSystem:
 *                 label: Informationssystem
 *                 sourcePath: informationsystem
 *                 type: STRING
 *           bezugspunktDach:
 *             label: Bezugspunkt Dach
 *             sourcePath: '[id=fk_feature]att_string_building{filter=name=''BezugspunktDach''}{sortKey=name}/value'
 *             type: STRING
 *             constraints:
 *               codelist: BezugspunktDach
 *           datenquelleBodenhoehe:
 *             label: Datenquelle Bodenhöhe
 *             sourcePath: '[id=fk_feature]att_string_building{filter=name=''DatenquelleBodenhoehe''}{sortKey=name}/value'
 *             type: STRING
 *             constraints:
 *               codelist: DatenquelleBodenhoehe
 *           datenquelleDachhoehe:
 *             label: Datenquelle Dachhöhe
 *             sourcePath: '[id=fk_feature]att_string_building{filter=name=''DatenquelleDachhoehe''}{sortKey=name}/value'
 *             type: STRING
 *             constraints:
 *               codelist: DatenquelleDachhoehe
 *           datenquelleLage:
 *             label: Datenquelle Lage
 *             sourcePath: '[id=fk_feature]att_string_building{filter=name=''DatenquelleLage''}{sortKey=name}/value'
 *             type: STRING
 *             constraints:
 *               codelist: DatenquelleLage
 *           address:
 *             label: Adresse
 *             sourcePath: '[id=fk_feature]address{sortKey=id}'
 *             type: OBJECT_ARRAY
 *             properties:
 *               id:
 *                 label: Objekt-ID
 *                 sourcePath: id
 *                 type: INTEGER
 *                 transformations:
 *                   remove: ALWAYS
 *               multiPoint:
 *                 sourcePath: multipoint
 *                 type: GEOMETRY
 *                 geometryType: MULTI_POINT
 *                 forcePolygonCCW: false
 *               ThoroughfareName:
 *                 label: Straße
 *                 sourcePath: street
 *                 type: STRING
 *               ThoroughfareNumber:
 *                 label: Hausnummer
 *                 sourcePath: code
 *                 type: STRING
 *               PostalCode:
 *                 label: Postleitzahl
 *                 sourcePath: postalcode
 *                 type: STRING
 *               LocalityName:
 *                 label: Stadt
 *                 sourcePath: city
 *                 type: STRING
 *               AdministrativeArea:
 *                 label: Verwaltungsbezirk
 *                 sourcePath: administrativearea
 *                 type: STRING
 *               CountryName:
 *                 label: Land
 *                 sourcePath: country
 *                 type: STRING
 *           lod1Solid:
 *             label: Geometrie (LoD 1)
 *             sourcePath: lod1solid
 *             type: GEOMETRY
 *             geometryType: MULTI_POLYGON
 *             forcePolygonCCW: false
 *           lod1TerrainIntersection:
 *             sourcePath: lod1terrainintersection
 *             type: GEOMETRY
 *             geometryType: MULTI_LINE_STRING
 *             forcePolygonCCW: false
 *           lod2Solid:
 *             label: Geometrie (LoD 2)
 *             sourcePath: lod2solid
 *             type: GEOMETRY
 *             geometryType: MULTI_POLYGON
 *             forcePolygonCCW: false
 *           surfaces:
 *             label: Begrenzungsflächen (LoD 2)
 *             sourcePath: '[id=fk_feature]building_surface{sortKey=gml_id}'
 *             type: OBJECT_ARRAY
 *             properties:
 *               gml_id:
 *                 label: ID
 *                 sourcePath: gml_id
 *                 type: STRING
 *               creationDate:
 *                 label: Erzeugungsdatum
 *                 sourcePath: creation
 *                 type: DATE
 *                 transformations:
 *                   dateFormat: 'yyyy-MM-dd'
 *               surfaceType:
 *                 label: Flächentyp
 *                 sourcePath: surface_type
 *                 type: STRING
 *               lod2MultiSurface:
 *                 label: Geometrie (LoD 2)
 *                 sourcePath: geom
 *                 type: GEOMETRY
 *                 geometryType: MULTI_POLYGON
 *                 forcePolygonCCW: false
 *           lod2TerrainIntersection:
 *             sourcePath: lod2terrainintersection
 *             type: GEOMETRY
 *             geometryType: MULTI_LINE_STRING
 *             forcePolygonCCW: false
 *           fk_buildingpart_parent:
 *             label: Gebäude-Objekt-ID
 *             sourcePath: fk_buildingpart_parent
 *             type: INTEGER
 *             transformations:
 *               remove: ALWAYS
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "CITY_JSON")
@JsonDeserialize(builder = ImmutableCityJsonConfiguration.Builder.class)
public interface CityJsonConfiguration
    extends ExtensionConfiguration, PropertyTransformations, ProfilesConfiguration {

  enum Version {
    V10("1.0"),
    V11("1.1");

    private final String text;

    Version(String value) {
      text = value;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  /**
   * @langEn Enables support for CityJSON text sequences (media type `application/city+json-seq`).
   *     Requires version 1.1 or later.
   * @langDe Aktiviert die Unterstützung für CityJSON Text Sequences (Media-Type
   *     `application/city+json-seq`). Erfordert mindestens Version 1.1.
   * @default false
   * @since v3.3
   */
  Optional<Boolean> getTextSequences();

  /**
   * @langEn Select the CityJSON version that should be returned. Supported versions are `V10`
   *     (CityJSON 1.0) and `V11` (CityJSON 1.1).
   * @langDe Wählen Sie die CityJSON-Version, die zurückgegeben werden soll. Unterstützte Versionen
   *     sind `V10` (CityJSON 1.0) und `V11` (CityJSON 1.1).
   * @default V11
   * @since v3.3
   */
  Optional<Version> getVersion();

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set, such
   *     as `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`.
   *     These defaults override the defaults specified in the [Features](features.md) building
   *     block.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Diese
   *     Vorgaben haben Vorrang vor den im [Features](features.md)-Baustein angegebenen
   *     Standardprofilen.
   * @since v4.2
   * @default {}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableCityJsonConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableCityJsonConfiguration.Builder()
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
