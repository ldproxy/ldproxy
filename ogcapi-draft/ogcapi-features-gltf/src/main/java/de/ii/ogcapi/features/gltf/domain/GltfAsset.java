/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.hash.Funnel;
import de.ii.xtraplatform.tiles3d.domain.spec.Buffer;
import de.ii.xtraplatform.tiles3d.domain.spec.BufferView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableGltfAsset.Builder.class)
public interface GltfAsset {

  ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          // for debugging
          // .enable(SerializationFeature.INDENT_OUTPUT)
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  byte[] MAGIC_GLTF = {0x67, 0x6c, 0x54, 0x46};
  byte[] VERSION_2 = {0x02, 0x00, 0x00, 0x00};
  byte[] JSON = {0x4a, 0x53, 0x4f, 0x4e};
  byte[] BIN = {0x42, 0x49, 0x4e, 0x00};
  byte[] JSON_PADDING = {0x20};
  byte[] BIN_PADDING = {0x00};
  double MAX_SHORT = 32_767.0;
  double MAX_BYTE = 127.0;

  @SuppressWarnings("UnstableApiUsage")
  Funnel<GltfAsset> FUNNEL =
      (from, into) -> {
        AssetMetadata.FUNNEL.funnel(from.getAsset(), into);
        from.getScene().ifPresent(into::putInt);
        from.getScenes().forEach(v -> Scene.FUNNEL.funnel(v, into));
        from.getNodes().forEach(v -> Node.FUNNEL.funnel(v, into));
        from.getMeshes().forEach(v -> Mesh.FUNNEL.funnel(v, into));
        from.getAccessors().forEach(v -> Accessor.FUNNEL.funnel(v, into));
        from.getBuffers().forEach(v -> Buffer.FUNNEL.funnel(v, into));
        from.getBufferViews().forEach(v -> BufferView.FUNNEL.funnel(v, into));
        from.getMaterials().forEach(v -> Material.FUNNEL.funnel(v, into));
        from.getExtensions().forEach((key, value) -> into.putString(key, StandardCharsets.UTF_8));
        from.getExtras().forEach((key, value) -> into.putString(key, StandardCharsets.UTF_8));
        from.getExtensionsRequired().forEach(v -> into.putString(v, StandardCharsets.UTF_8));
        from.getExtensionsUsed().forEach(v -> into.putString(v, StandardCharsets.UTF_8));
      };

  static GltfAsset of(byte[] glbBytes) throws IOException {
    if (!Arrays.equals(Arrays.copyOfRange(glbBytes, 0, 4), MAGIC_GLTF)) {
      throw new IllegalStateException(
          String.format(
              "Invalid glTF binary file, invalid magic number. Found: %s",
              Arrays.toString(Arrays.copyOfRange(glbBytes, 0, 4))));
    }

    final int version = littleEndianIntToInt(glbBytes, 4);
    if (version != 2) {
      throw new IllegalStateException(
          String.format(
              "Unsupported glTF version, only version 2 is supported. Found: %d", version));
    }

    final int jsonLength = littleEndianIntToInt(glbBytes, 8);
    final byte[] jsonContent = Arrays.copyOfRange(glbBytes, 20, 20 + jsonLength);
    return MAPPER.readValue(jsonContent, GltfAsset.class);
  }

  private static int littleEndianIntToInt(byte[] array, int offset) {
    ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 4; i++) {
      bb.put(array[offset + i]);
    }
    bb.rewind();
    return bb.getInt();
  }

  AssetMetadata getAsset();

  Optional<Integer> getScene();

  List<Scene> getScenes();

  List<Node> getNodes();

  List<Mesh> getMeshes();

  List<Accessor> getAccessors();

  List<Buffer> getBuffers();

  List<BufferView> getBufferViews();

  List<Material> getMaterials();

  Map<String, Object> getExtensions();

  Map<String, Object> getExtras();

  List<String> getExtensionsUsed();

  List<String> getExtensionsRequired();

  default void writeGltfBinary(List<ByteArrayOutputStream> buffers, OutputStream outputStream) {
    byte[] json;
    try {
      json = MAPPER.writeValueAsBytes(this);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          String.format("Could not write glTF asset. Reason: %s", e.getMessage()), e);
    }

    // EXT_structural_metadata requires 8-byte padding
    int bufferLength = buffers.stream().map(ByteArrayOutputStream::size).reduce(0, Integer::sum);
    int jsonPadding = (8 - json.length % 8) % 8;
    int bufferPadding = (8 - bufferLength % 8) % 8;
    int totalLength = 12 + 8 + json.length + jsonPadding + 8 + bufferLength + bufferPadding;
    try {
      outputStream.write(MAGIC_GLTF);
      outputStream.write(VERSION_2);
      outputStream.write(intToLittleEndianInt(totalLength));

      outputStream.write(intToLittleEndianInt(json.length + jsonPadding));
      outputStream.write(JSON);
      outputStream.write(json);
      for (int i = 0; i < jsonPadding; i++) {
        outputStream.write(JSON_PADDING);
      }

      outputStream.write(intToLittleEndianInt(bufferLength + bufferPadding));
      outputStream.write(BIN);
      for (ByteArrayOutputStream b : buffers) {
        outputStream.write(b.toByteArray());
      }
      for (int i = 0; i < bufferPadding; i++) {
        outputStream.write(BIN_PADDING);
      }

      outputStream.flush();
    } catch (Exception e) {
      throw new IllegalStateException("Could not write glTF output", e);
    }
  }

  static byte[] intToLittleEndianInt(int v) {
    ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(v);
    return bb.array();
  }

  static byte[] longToLittleEndianLong(long v) {
    ByteBuffer bb = ByteBuffer.allocate(8);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putLong(v);
    return bb.array();
  }

  static byte[] intToLittleEndianShort(int v) {
    ByteBuffer bb = ByteBuffer.allocate(2);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putShort((short) v);
    return bb.array();
  }

  static byte[] intToLittleEndianFloat(int v) {
    ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putFloat((float) v);
    return bb.array();
  }

  static byte[] intToLittleEndianByte(int v) {
    ByteBuffer bb = ByteBuffer.allocate(1);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.put((byte) v);
    return bb.array();
  }

  static byte[] doubleToLittleEndianShort(double v) {
    ByteBuffer bb = ByteBuffer.allocate(2);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putShort((short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(v))));
    return bb.array();
  }

  static byte[] doubleToLittleEndianByte(double v) {
    ByteBuffer bb = ByteBuffer.allocate(1);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.put((byte) Math.max(Byte.MIN_VALUE, Math.min(Byte.MAX_VALUE, Math.round(v))));
    return bb.array();
  }

  static byte[] doubleToLittleEndianFloat(double v) {
    ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putFloat((float) v);
    return bb.array();
  }

  static byte[] doubleToLittleEndianDouble(double v) {
    ByteBuffer bb = ByteBuffer.allocate(8);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putDouble(v);
    return bb.array();
  }
}
