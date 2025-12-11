/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.domain;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.gltf.domain.SchemaProperty.Type;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.tiles3d.domain.spec.Property.OffsetType;
import java.net.URI;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class FeatureTransformationContextGltf implements FeatureTransformationContext {

  public abstract boolean getClampToEllipsoid();

  public abstract CrsTransformer getCrsTransformerCrs84hToEcef();

  public abstract Optional<URI> getSchemaUri();

  public abstract GltfSchema getGltfSchema();

  public abstract GltfConfiguration getGltfConfiguration();

  @Value.Derived
  public Map<String, SchemaProperty> getProperties() {
    return getGltfSchema().getClasses().values().iterator().next().getProperties();
  }

  @Value.Derived
  public Map<String, SchemaEnum> getEnums() {
    return getGltfSchema().getEnums();
  }

  @Value.Derived
  public Map<String, OffsetType> getStringOffsetTypes() {
    return getGltfConfiguration().getProperties().entrySet().stream()
        .filter(p -> p.getValue().getType() == Type.STRING)
        .map(p -> new SimpleEntry<>(p.getKey(), p.getValue().getStringOffsetType().get()))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
