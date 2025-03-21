/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Modifiable
public class StateGltf {

  @Value.Default
  Map<String, ByteArrayOutputStream> getBuffers() {
    return new HashMap<>();
  }

  @Value.Default
  int getNextFeatureId() {
    return 0;
  }

  @Value.Default
  List<Double> getVertices() {
    return new ArrayList<>();
  }

  @Value.Default
  List<Double> getNormals() {
    return new ArrayList<>();
  }

  @Value.Default
  List<Integer> getIndices() {
    return new ArrayList<>();
  }

  @Value.Default
  List<Integer> getFeatureIds() {
    return new ArrayList<>();
  }

  @Value.Default
  List<Integer> getOutline() {
    return new ArrayList<>();
  }

  @Value.Default
  int getIndexCount() {
    return 0;
  }

  @Value.Default
  int getSurfaceCount() {
    return 0;
  }

  @Value.Default
  Map<String, Integer> getCurrentBufferViewOffsets() {
    return new HashMap<>();
  }

  @Value.Default
  int getNextAccessorId() {
    return 0;
  }

  @Value.Default
  int getNextMeshId() {
    return 0;
  }

  @Value.Default
  int getNextNodeId() {
    return 0;
  }
}
