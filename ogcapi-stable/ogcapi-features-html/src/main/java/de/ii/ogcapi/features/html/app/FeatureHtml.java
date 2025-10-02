/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.app;

import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.features.domain.FeatureBase;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.PropertyBase;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.PositionList;
import de.ii.xtraplatform.geometries.domain.transform.FirstCoordinates;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.immutables.value.Value;

@Value.Modifiable
@Value.Style(set = "*")
public interface FeatureHtml extends FeatureBase<PropertyHtml, FeatureSchema> {

  FirstCoordinates FIRST_COORDINATES = new FirstCoordinates();

  Optional<String> getItemType();

  FeatureHtml itemType(String itemType);

  @Value.Default
  default boolean inCollection() {
    return false;
  }

  FeatureHtml inCollection(boolean inCollection);

  @Value.Lazy
  default String getSchemaOrgItemType() {
    return getItemType()
        .filter(itemType -> itemType.startsWith("http://schema.org/"))
        .map(itemType -> itemType.substring(18))
        .orElse(null);
  }

  @Override
  @Value.Default
  default String getName() {
    return getId()
        .flatMap(id -> Optional.ofNullable(id.getFirstValue()))
        .orElse(FeatureBase.super.getName());
  }

  @Value.Lazy
  default Optional<PropertyHtml> getId() {
    return getProperties().stream()
        .filter(property -> property.getSchema().filter(SchemaBase::isId).isPresent())
        .findFirst();
  }

  @Value.Lazy
  default String getIdValue() {
    return getId().map(PropertyHtml::getFirstValue).orElse(null);
  }

  @Value.Lazy
  default boolean hasObjects() {
    return getProperties().stream()
        .anyMatch(
            property ->
                !property.isValue()
                    && property.getSchema().filter(SchemaBase::isSpatial).isEmpty());
  }

  @Value.Lazy
  default boolean hasGeometry() {
    return getProperties().stream()
            .anyMatch(property -> property.getSchema().filter(SchemaBase::isSpatial).isPresent())
        || getProperties().stream().anyMatch(PropertyHtml::hasGeometry);
  }

  @Value.Lazy
  default Optional<Geometry<?>> getGeometry() {
    return getProperties().stream()
        .filter(
            property ->
                property
                    .getSchema()
                    .filter(SchemaBase::isSpatial)
                    .filter(SchemaBase::isPrimaryGeometry)
                    .isPresent())
        .findFirst()
        .map(PropertyBase::getGeometry);
  }

  @Value.Lazy
  default String getGeoAsString() {
    Optional<Geometry<?>> geometry = getGeometry();
    return geometry
        .flatMap(
            geo -> {
              GeometryType geometryType = geo.getType();
              PositionList coordinates = geo.accept(FIRST_COORDINATES);
              if (!coordinates.isEmpty()) {

                if (coordinates.getNumPositions() == 1) {
                  return Optional.of(
                      String.format(
                          "{ \"@type\": \"GeoCoordinates\", \"latitude\": \"%f\", \"longitude\": \"%f\" }",
                          coordinates.getCoordinates()[1], coordinates.getCoordinates()[0]));
                } else {
                  String geomType =
                      (geometryType.getGeometryDimension().filter(dim -> dim == 2).isPresent())
                          ? "polygon"
                          : "line";
                  int dimension = coordinates.getAxes().size();
                  String coords =
                      IntStream.range(0, coordinates.getNumPositions())
                          .mapToObj(
                              i ->
                                  coordinates.getCoordinates()[i * dimension + 1]
                                      + " "
                                      + coordinates.getCoordinates()[i * dimension])
                          .collect(Collectors.joining(" "));
                  return Optional.of(
                      String.format(
                          "{ \"@type\": \"GeoShape\", \"%s\": \"%s\" }", geomType, coords));
                }
              }

              return Optional.empty();
            })
        .orElse(null);
  }

  default Optional<PropertyHtml> findPropertyByPath(String pathString) {
    return findPropertyByPath(PropertyHtml.PATH_SPLITTER.splitToList(pathString));
  }

  default Optional<PropertyHtml> findPropertyByPath(List<String> path) {
    return getProperties().stream()
        .filter(property -> Objects.equals(property.getPropertyPath(), path))
        .findFirst()
        .or(
            () ->
                getProperties().stream()
                    .filter(
                        property -> property.getSchema().filter(SchemaBase::isSpatial).isEmpty())
                    .map(property -> property.findPropertyByPath(path))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst());
  }

  default List<PropertyHtml> findPropertiesByPath(String pathString) {
    return findPropertiesByPath(PropertyHtml.PATH_SPLITTER.splitToList(pathString));
  }

  default List<PropertyHtml> findPropertiesByPath(List<String> path) {
    List<PropertyHtml> properties =
        getProperties().stream()
            .map(
                property -> {
                  switch (PropertyHtml.pathCompatible(property.getPropertyPath(), path)) {
                    case SUB_PATH:
                      return property.findPropertiesByPath(path);
                    case EQUAL:
                      return ImmutableList.of(property);
                  }
                  return ImmutableList.<PropertyHtml>of();
                })
            .flatMap(Collection::stream)
            .collect(Collectors.toUnmodifiableList());
    if (properties.isEmpty())
      properties =
          getProperties().stream()
              .filter(property -> property.getSchema().filter(SchemaBase::isSpatial).isEmpty())
              .map(property -> property.findPropertyByPath(path))
              .filter(Optional::isPresent)
              .map(Optional::get)
              .collect(Collectors.toUnmodifiableList());
    return properties;
  }
}
