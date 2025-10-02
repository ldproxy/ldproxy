/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

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
public interface PropertyGltf extends PropertyBase<PropertyGltf, FeatureSchema> {

  Splitter PATH_SPLITTER = Splitter.on('.').omitEmptyStrings();

  Optional<String> getItemType();

  PropertyGltf itemType(String itemType);

  Optional<String> getItemProp();

  PropertyGltf itemProp(String itemProp);

  @Override
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
    return isValue() || isArray() && getNestedProperties().stream().anyMatch(PropertyBase::isValue);
  }

  @Value.Lazy
  default List<PropertyGltf> getValues() {
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

  @Value.Lazy
  default List<PropertyGltf> getObjects() {
    return getNestedProperties().stream()
        .filter(
            property ->
                !property.isValue() && property.getSchema().filter(SchemaBase::isSpatial).isEmpty())
        .collect(Collectors.toList());
  }

  @Value.Lazy
  default boolean isInArray() {
    return getParent().isPresent() && getParent().get().isArray();
  }

  @Value.Lazy
  @Override
  default boolean isObject() {
    return PropertyBase.super.isObject() && getSchema().filter(SchemaBase::isSpatial).isEmpty();
  }

  @Value.Lazy
  default boolean isNull() {
    return Objects.isNull(getValue());
  }

  @Value.Lazy
  default String getLastPathSegment() {
    return getPropertyPath().get(getPropertyPath().size() - 1);
  }

  default Optional<PropertyGltf> findPropertyByPath(List<String> path) {
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
