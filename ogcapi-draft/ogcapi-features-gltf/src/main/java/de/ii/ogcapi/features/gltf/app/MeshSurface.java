/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableMeshSurface.Builder.class)
interface MeshSurface {

  PolyhedralSurface getGeometry();

  Optional<String> getSurfaceType();

  static MeshSurface of(PolyhedralSurface geometry) {
    return ImmutableMeshSurface.builder().geometry(geometry).build();
  }

  static MeshSurface of(PolyhedralSurface geometry, String surfaceType) {
    return ImmutableMeshSurface.builder().geometry(geometry).surfaceType(surfaceType).build();
  }

  static MeshSurface of(PolyhedralSurface geometry, Optional<String> surfaceType) {
    return ImmutableMeshSurface.builder().geometry(geometry).surfaceType(surfaceType).build();
  }
}
