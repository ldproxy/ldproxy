/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.features.domain.PropertyBase;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryCollection;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.MultiPolygon;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import de.ii.xtraplatform.geometries.domain.transform.MinMaxDeriver;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableMeshSurfaceList.Builder.class)
interface MeshSurfaceList {

  String LOD_1_SOLID = "lod1Solid";
  String LOD_2_SOLID = "lod2Solid";
  String SURFACES = "surfaces";
  String SURFACE_TYPE = "surfaceType";
  String LOD_2_MULTI_SURFACE = "lod2MultiSurface";
  String CONSISTS_OF_BUILDING_PART = "consistsOfBuildingPart";

  static MeshSurfaceList of() {
    return ImmutableMeshSurfaceList.builder().build();
  }

  static MeshSurfaceList of(FeatureGltf building) {
    ImmutableMeshSurfaceList.Builder meshSurfaceBuilder = ImmutableMeshSurfaceList.builder();

    building
        .findPropertyByPath(CONSISTS_OF_BUILDING_PART)
        .map(PropertyBase::getNestedProperties)
        .ifPresentOrElse(
            buildingParts ->
                buildingParts.forEach(
                    buildingPart ->
                        collectSolidSurfaces(
                            meshSurfaceBuilder, buildingPart.getNestedProperties())),
            () -> collectSolidSurfaces(meshSurfaceBuilder, building.getProperties()));

    return meshSurfaceBuilder.build();
  }

  List<MeshSurface> getMeshSurfaces();

  @Value.Derived
  default boolean isEmpty() {
    return getMeshSurfaces().isEmpty();
  }

  @Value.Derived
  @Nullable
  default double[][] getMinMax() {
    // determine the bounding box; eventually we will translate vertices to the center of the
    // feature to have smaller values (glTF uses float)
    MinMaxDeriver visitor = new MinMaxDeriver();
    GeometryCollection geom =
        GeometryCollection.of(
            (List<Geometry<?>>)
                (List<?>) getMeshSurfaces().stream().map(MeshSurface::getGeometry).toList());
    return geom.accept(visitor);
  }

  private static void collectSolidSurfaces(
      ImmutableMeshSurfaceList.Builder meshSurfaceBuilder, List<PropertyGltf> properties) {
    properties.stream()
        .filter(p -> SURFACES.equals(p.getLastPathSegment()))
        .findFirst()
        .ifPresentOrElse(
            surfacesProperty -> addSurfaces(meshSurfaceBuilder, surfacesProperty),
            () -> addSolid(meshSurfaceBuilder, properties));
  }

  private static void addSolid(
      ImmutableMeshSurfaceList.Builder meshSurfaceBuilder, List<PropertyGltf> properties) {
    properties.stream()
        .filter(p -> LOD_2_SOLID.equals(p.getLastPathSegment()))
        .findFirst()
        .ifPresentOrElse(
            p -> addPolyhedralSurface(meshSurfaceBuilder, p),
            () ->
                properties.stream()
                    .filter(p -> LOD_1_SOLID.equals(p.getLastPathSegment()))
                    .findFirst()
                    .ifPresent(p -> addPolyhedralSurface(meshSurfaceBuilder, p)));
  }

  private static void addSurfaces(
      ImmutableMeshSurfaceList.Builder meshSurfaceBuilder, PropertyGltf surfacesProperty) {
    surfacesProperty.getNestedProperties().stream()
        .collect(
            Collectors.groupingBy(
                surface ->
                    surface.getNestedProperties().stream()
                        .filter(p -> SURFACE_TYPE.equals(p.getLastPathSegment()))
                        .findFirst()
                        .map(PropertyGltf::getFirstValue)
                        .map(String::toLowerCase)
                        .orElse("unknown")))
        .forEach(
            (key, value) ->
                meshSurfaceBuilder.addMeshSurfaces(
                    MeshSurface.of(
                        PolyhedralSurface.of(
                            value.stream()
                                .map(
                                    surface ->
                                        surface.getNestedProperties().stream()
                                            .filter(
                                                p ->
                                                    LOD_2_MULTI_SURFACE.equals(
                                                        p.getLastPathSegment()))
                                            .toList())
                                .flatMap(List::stream)
                                .map(PropertyGltf::getGeometry)
                                .filter(Objects::nonNull)
                                .filter(g -> g.getType() == GeometryType.MULTI_POLYGON)
                                .map(MultiPolygon.class::cast)
                                .map(MultiPolygon::getValue)
                                .flatMap(List::stream)
                                .toList()),
                        "unknown".equals(key) ? Optional.empty() : Optional.of(key))));
  }

  private static void addPolyhedralSurface(
      ImmutableMeshSurfaceList.Builder meshSurfaceBuilder, PropertyGltf geometryProperty) {
    Geometry<?> geometry = geometryProperty.getGeometry();
    if (geometry != null && geometry.getType() == GeometryType.POLYHEDRAL_SURFACE) {
      meshSurfaceBuilder.addMeshSurfaces(MeshSurface.of((PolyhedralSurface) geometry));
    }
  }
}
