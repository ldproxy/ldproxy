/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app.encoder;

import de.ii.xtraplatform.features.domain.FeatureBase;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.LineString;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Modifiable
@Value.Style(set = "*")
public interface FeatureRoutes extends FeatureBase<PropertyRoutes, FeatureSchema> {

  @Override
  @Value.Default
  default String getName() {
    return getId()
        .flatMap(id -> Optional.ofNullable(id.getFirstValue()))
        .orElse(FeatureBase.super.getName());
  }

  @Value.Lazy
  default Optional<PropertyRoutes> getId() {
    return getProperties().stream()
        .filter(property -> property.getSchema().filter(SchemaBase::isId).isPresent())
        .findFirst();
  }

  @Value.Lazy
  default String getIdValue() {
    return getId().map(PropertyRoutes::getFirstValue).orElse(null);
  }

  @Value.Lazy
  default Optional<PropertyRoutes> getGeometryProperty() {
    return getProperties().stream()
        .filter(
            property ->
                property
                    .getSchema()
                    .filter(SchemaBase::isSpatial)
                    .filter(SchemaBase::isPrimaryGeometry)
                    .isPresent())
        .findFirst();
  }

  @Value.Lazy
  default Optional<LineString> getGeometry() {
    return getGeometryProperty()
        .map(PropertyRoutes::getGeometry)
        .filter(g -> g.getType() == GeometryType.LINE_STRING)
        .map(LineString.class::cast);
  }

  default Optional<PropertyRoutes> findPropertyByPath(String pathString) {
    return findPropertyByPath(PropertyRoutes.PATH_SPLITTER.splitToList(pathString));
  }

  default Optional<PropertyRoutes> findPropertyByPath(List<String> path) {
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
}
