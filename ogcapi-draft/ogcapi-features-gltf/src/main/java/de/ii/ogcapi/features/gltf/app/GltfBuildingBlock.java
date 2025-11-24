/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gltf.domain.ImmutableGltfConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - glTF
 * @langEn Encode CityGML building features (LoD1, LoD2) as glTF 2.0.
 * @langDe Kodierung von CityGML Gebäuden (LoD1, LoD2) in glTF 2.0.
 * @scopeEn The building block *Features - glTF* adds support for glTF 2.0 as a feature encoding.
 *     Supported are the CityGML feature types `Building` and `BuildingPart`.
 *     <p>This building block supports the glTF 2.0 Extension
 *     [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization)
 *     for a compact encoding of vertices and normals. The use of this extension is recommended and
 *     enabled by default.
 *     <p>Any feature property that is included in the glTF model enables support for the glTF 2.0
 *     extensions
 *     [EXT_mesh_features](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_mesh_features)
 *     and
 *     [EXT_structural_metadata](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_structural_metadata).
 *     The properties are stored in binary tables.
 * @scopeDe Der Baustein *Features - glTF* unterstützt glTF 2.0 als Kodierung für Features.
 *     Unterstützt werden die Objektarten `Building` und `BuildingPart`. Der Baustein *Features -
 *     glTF* bietet Unterstützung für glTF 2.0 als Feature-Kodierung. Unterstützt werden die
 *     CityGML-Objektarten `Building` und `BuildingPart`.
 *     <p>Dieser Baustein unterstützt die glTF 2.0 Erweiterung
 *     [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization)
 *     für eine kompakte Kodierung von Vertices und Normalen. Die Verwendung dieser Erweiterung wird
 *     empfohlen und ist standardmäßig aktiviert.
 *     <p>Jede Feature-Eigenschaft, die im glTF-Modell enthalten ist, aktiviert die Unterstützung
 *     für die glTF 2.0-Erweiterungen
 *     [EXT_mesh_features](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_mesh_features)
 *     und
 *     [EXT_structural_metadata](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_structural_metadata).
 *     Die Eigenschaften werden in binären Tabellen gespeichert.
 * @conformanceEn *Features - glTF* implements support for [glTF
 *     2.0](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html) with the extensions
 *     [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization),
 *     [EXT_mesh_features](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_mesh_features),
 *     and
 *     [EXT_structural_metadata](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_structural_metadata).
 * @conformanceDe *Features - glTF* implementiert Unterstützung für [glTF
 *     2.0](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html) mit den Erweiterungen
 *     [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization),
 *     [EXT_mesh_features](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_mesh_features),
 *     und
 *     [EXT_structural_metadata](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_structural_metadata).
 * @limitationsEn The following restrictions apply:
 *     <p><code>
 * - Only CityGML buildings and building parts in LoD1 and LoD2 are supported.
 * - 3D Metadata:
 *   - Only properties of type SCALAR, STRING and ENUM are supported.
 *   - Arrays are not supported.
 *   - Offset and scale are not supported.
 *   - Default values are not supported.
 *     </code>
 * @limitationsDe Es gelten die folgenden Einschränkungen:
 *     <p><code>
 * Nur CityGML-Gebäude und Gebäudeteile in LoD1 und LoD2 werden unterstützt.
 * - 3D-Metadaten:
 * - Es werden nur Eigenschaften vom Typ SCALAR, STRING und ENUM unterstützt.
 * - Arrays werden nicht unterstützt.
 * - Offset und Skalierung werden nicht unterstützt.
 * - Standardwerte werden nicht unterstützt.
 *     </code>
 * @ref:cfg {@link de.ii.ogcapi.features.gltf.domain.GltfConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.gltf.domain.ImmutableGltfConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.features.gltf.infra.EndpointGltfSchema}
 * @ref:queryParameters {@link de.ii.ogcapi.features.gltf.app.QueryParameterClampToEllipsoid},
 *     {@link de.ii.ogcapi.features.gltf.app.QueryParameterFGltfSchema}
 * @ref:pathParameters {@link de.ii.ogcapi.features.gltf.app.PathParameterCollectionIdGltf}
 */
@AutoBind
@Singleton
public class GltfBuildingBlock implements ApiBuildingBlock {

  public static final int DEFAULT_MULTIPLICITY = 3;

  @Inject
  public GltfBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableGltfConfiguration.Builder()
        .enabled(false)
        .meshQuantization(true)
        .withNormals(true)
        .withOutline(false)
        .polygonOrientationNotGuaranteed(true)
        .withSurfaceType(false)
        .maxMultiplicity(DEFAULT_MULTIPLICITY)
        .build();
  }
}
