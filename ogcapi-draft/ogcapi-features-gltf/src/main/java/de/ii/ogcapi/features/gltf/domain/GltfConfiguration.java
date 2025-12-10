/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock GLTF
 * @langEn ### Prerequisites
 *     <p>Theis building block requires that the feature provider includes a type `building`. The
 *     requirements for the type are the same as in the configuration of the [CityJSON
 *     encoding](features_-_cityjson.html#configuration).
 * @langDe ### Voraussetzungen
 *     <p>Dieser Baustein erfordert, dass der Feature Provider einen Typ "building" enthält. Die
 *     Anforderungen an den Typ sind dieselben wie in der Konfiguration der
 *     [CityJSON-Kodierung](features_-_cityjson.html#konfiguration).
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: GLTF
 *   enabled: true
 *   withNormals: true
 *   polygonOrientationNotGuaranteed: true
 *   meshQuantization: true
 *   properties:
 *     gml_id:
 *       type: STRING
 *       stringOffsetType: UINT16
 *       noData: ''
 *     function:
 *       type: STRING
 *       stringOffsetType: UINT16
 *       noData: ''
 *     roofType:
 *       type: ENUM
 *       componentType: UINT16
 *       noData: 0
 *     name:
 *       type: STRING
 *       stringOffsetType: UINT16
 *       noData: ''
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "GLTF")
@JsonDeserialize(builder = ImmutableGltfConfiguration.Builder.class)
public interface GltfConfiguration
    extends ExtensionConfiguration, PropertyTransformations, ProfilesConfiguration {

  /**
   * @langEn Enables support for the glTF 2.0 extension
   *     [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization).
   * @langDe Aktiviert die Unterstützung für die glTF 2.0 Erweiterung
   *     [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization).
   * @default true
   * @since v3.4
   */
  @Nullable
  Boolean getMeshQuantization();

  @Value.Derived
  @JsonIgnore
  default boolean useMeshQuantization() {
    return Boolean.TRUE.equals(getMeshQuantization());
  }

  /**
   * @langEn If `true`, the normals are computed for every vertex.
   * @langDe Wenn `true`, werden die Normalen für jeden Punkt berechnet.
   * @default true
   * @since v3.4
   */
  @Nullable
  Boolean getWithNormals();

  @Value.Derived
  @JsonIgnore
  default boolean writeNormals() {
    return Boolean.TRUE.equals(getWithNormals());
  }

  /**
   * @langEn If `true`, the polygon edges are outlined in Cesium.
   * @langDe Wenn `true`, werden die Kanten der Polygone in Cesium hervorgehoben.
   * @default false
   * @since v3.4
   */
  @Nullable
  Boolean getWithOutline();

  @Value.Derived
  @JsonIgnore
  default boolean writeOutline() {
    return Boolean.TRUE.equals(getWithOutline());
  }

  /**
   * @langEn If `true`, materials are defined as
   *     [double-sided](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#double-sided).
   * @langDe Wenn `true`, werden Materialien als
   *     [double-sided](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html#double-sided)
   *     definiert.
   * @default true
   * @since v3.4
   */
  @Nullable
  Boolean getPolygonOrientationNotGuaranteed();

  @Value.Derived
  @JsonIgnore
  default boolean polygonOrientationIsNotGuaranteed() {
    return Boolean.TRUE.equals(getPolygonOrientationNotGuaranteed());
  }

  /**
   * @langEn Use this option to specify which feature attributes are included in the glTF model.
   *     `type` is one of `SCALAR`, `STRING` or `ENUM`. For a scalar, specify the `componentType`
   *     (see [3D Metadata
   *     Specification](https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata#component-type)),
   *     default is `UNIT16`. For a string, specify the `stringOffsetType` as `UNIT8`, `UNIT16`, or
   *     `UNIT32` depending on the expected length of the string buffer, default is `UNIT32`. For an
   *     enum, the feature property must either have an `enum` constraint in the provider schema or
   *     a `codelist` constraint where the codelist uses an integer code; specify the
   *     `componentType` according to the range of code values, default is `UINT32`. In addition, a
   *     sentinel value can be specified using `noData` (see [3D Metadata
   *     Specification](https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata) for
   *     details).
   * @langDe Verwenden Sie diese Option, um anzugeben, welche Feature-Attribute in das glTF-Modell
   *     aufgenommen werden sollen. `type` ist entweder `SCALAR`, `STRING` oder `ENUM`. Für einen
   *     Skalar geben Sie den `componentType` an (siehe [3D Metadata
   *     Specification](https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata#component-type)),
   *     Standard ist `UNIT16`. Für eine Zeichenkette geben Sie den `stringOffsetType` als `UNIT8`,
   *     `UNIT16` oder `UNIT32` an, abhängig von der erwarteten Länge des Text-Buffers, Standard ist
   *     `UNIT32`. Bei einer Aufzählung muss die Feature-Eigenschaft entweder einen
   *     `enum`-Constraint im Provider-Schema haben oder einen `codelist`-Constraint, bei dem die
   *     Codeliste einen Integer-Code verwendet; geben Sie den `componentType` entsprechend dem
   *     Bereich der Codes an, Standard ist `UINT32`. Darüber hinaus kann ein Sentinel-Wert mit
   *     `noData` angegeben werden (siehe [3D Metadata
   *     Specification](https://github.com/CesiumGS/3d-tiles/tree/main/specification/Metadata) für
   *     weitere Einzelheiten).
   * @default {}
   * @since v3.4
   */
  Map<String, GltfPropertyDefinition> getProperties();

  /**
   * @langEn If `true`, for buildings in Level-of-Detail 2 with information about the semantics of
   *     each surface (wall, roof, etc.), a property "surfaceType" is added and available for each
   *     vertex.
   * @langDe Wenn `true`, wird für Gebäude in Level-of-Detail 2 mit Informationen über die Semantik
   *     jeder Oberfläche (Wand, Dach, etc.) wird automatisch eine weitere Eigenschaft "surfaceType"
   *     hinzugefügt, die für jeden Vertex verfügbar ist.
   * @default false
   * @since v3.4
   */
  @Nullable
  Boolean getWithSurfaceType();

  @Value.Derived
  @JsonIgnore
  default boolean includeSurfaceType() {
    return Boolean.TRUE.equals(getWithSurfaceType());
  }

  /**
   * @langEn If the data is flattened and the feature schema includes arrays, `maxMultiplicity`
   *     properties will be created for each array property. If an instance has more values in an
   *     array, only the first values are included in the data.
   * @langDe Wenn die Daten abgeflacht sind und das Feature-Schema Arrays enthält, werden
   *     `maxMultiplicity` Eigenschaften für jede Array-Eigenschaft erstellt. Wenn eine Instanz
   *     mehrere Werte in einem Array hat, werden nur die ersten Werte in die Daten aufgenommen.
   * @default 3
   * @since v3.4
   */
  @Nullable
  Integer getMaxMultiplicity();

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

  /**
   * @langEn By default, the glTF schema describing the feature properties as part of the
   *     `EXT_structural_metadata` extension is referenced by the URI under which the schema is
   *     published via the API. Use this option to specify a different URI. Note that the option is
   *     ignored, if `embedSchema` is set to `true`.
   * @langDe Standardmäßig wird das glTF-Schema, das die Feature-Eigenschaften als Teil der
   *     `EXT_structural_metadata`-Erweiterung beschreibt, über die URI referenziert, unter der das
   *     Schema über die API veröffentlicht wird. Verwenden Sie diese Option, um eine andere URI
   *     anzugeben. Beachten Sie, dass die Option ignoriert wird, wenn `embedSchema` auf `true`
   *     gesetzt ist.
   * @default null
   * @since v4.6
   */
  @Nullable
  String getSchemaUri();

  /**
   * @langEn If set to `true`, the glTF schema describing the feature properties as part of the
   *     `EXT_structural_metadata` extension is embedded in the glTF asset instead of referencing
   *     the schema by URI.
   * @langDe Falls auf `true` gesetzt, wird das glTF-Schema, das die Feature-Eigenschaften als Teil
   *     der `EXT_structural_metadata`-Erweiterung beschreibt, in das glTF-Asset eingebettet,
   *     anstatt das Schema über eine URI zu referenzieren.
   * @default false
   * @since v4.6
   */
  @Nullable
  Boolean getEmbedSchema();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableGltfConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableGltfConfiguration.Builder()
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
