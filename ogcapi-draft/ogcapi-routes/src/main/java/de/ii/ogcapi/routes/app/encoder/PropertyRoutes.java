/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app.encoder;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.PropertyBase;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Modifiable
@Value.Style(set = "*")
public interface PropertyRoutes extends PropertyBase<PropertyRoutes, FeatureSchema> {

  @Value.Default
  default String getName() {
    return isFlattened()
        ? getSchema().map(FeatureSchema::getName).orElse("")
        : getSchema()
            .flatMap(FeatureSchema::getLabel)
            .orElse(getSchema().map(FeatureSchema::getName).orElse(""));
  }

  @Value.Lazy
  default boolean hasValues() {
    return isValue()
        || (isArray() && getNestedProperties().stream().anyMatch(PropertyBase::isValue));
  }

  @Value.Lazy
  default List<PropertyRoutes> getValues() {
    return isValue()
        ? ImmutableList.of(this)
        : isArray()
            ? getNestedProperties().stream()
                .filter(PropertyBase::isValue)
                .collect(Collectors.toList())
            : ImmutableList.of();
  }

  @Value.Lazy
  default String getFirstValue() {
    return hasValues() ? getValues().get(0).getValue() : null;
  }

  Splitter PATH_SPLITTER = Splitter.on('.').omitEmptyStrings();

  default Optional<PropertyRoutes> findPropertyByPath(String pathString) {
    return findPropertyByPath(PATH_SPLITTER.splitToList(pathString));
  }

  default Optional<PropertyRoutes> findPropertyByPath(List<String> path) {
    return getNestedProperties().stream()
        .filter(property -> Objects.equals(property.getPropertyPath(), path))
        .findFirst()
        .or(
            () ->
                getNestedProperties().stream()
                    .filter(
                        property -> property.getSchema().filter(SchemaBase::isSpatial).isEmpty())
                    .map(property -> property.findPropertyByPath(path))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst());
  }
}
