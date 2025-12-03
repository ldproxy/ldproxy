/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.gltf.domain.FeatureTransformationContextGltf;
import de.ii.ogcapi.features.gltf.domain.GltfAsset;
import de.ii.ogcapi.features.gltf.domain.GltfConfiguration;
import de.ii.ogcapi.features.gltf.domain.ImmutableAccessor;
import de.ii.ogcapi.features.gltf.domain.ImmutableAssetMetadata;
import de.ii.ogcapi.features.gltf.domain.ImmutableAttributes;
import de.ii.ogcapi.features.gltf.domain.ImmutableGltfAsset;
import de.ii.ogcapi.features.gltf.domain.ImmutableMaterial;
import de.ii.ogcapi.features.gltf.domain.ImmutableMesh;
import de.ii.ogcapi.features.gltf.domain.ImmutableNode;
import de.ii.ogcapi.features.gltf.domain.ImmutablePbrMetallicRoughness;
import de.ii.ogcapi.features.gltf.domain.ImmutablePrimitive;
import de.ii.ogcapi.features.gltf.domain.ImmutableScene;
import de.ii.ogcapi.features.gltf.domain.SchemaEnum;
import de.ii.ogcapi.features.gltf.domain.SchemaProperty;
import de.ii.ogcapi.features.gltf.domain.SchemaProperty.ComponentType;
import de.ii.ogcapi.features.gltf.domain.SchemaProperty.Type;
import de.ii.ogcapi.features.gltf.domain.TriangleMesh;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.xtraplatform.base.domain.LogContext.MARKER;
import de.ii.xtraplatform.features.domain.FeatureObjectEncoder;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutableBuffer;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutableBufferView;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutableProperty;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutablePropertyTable;
import de.ii.xtraplatform.tiles3d.domain.spec.Property.OffsetType;
import de.ii.xtraplatform.tiles3d.domain.spec.PropertyTable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureEncoderGltf extends FeatureObjectEncoder<PropertyGltf, FeatureGltf> {

  private static final String KHR_MESH_QUANTIZATION = "KHR_mesh_quantization";
  private static final String EXT_STRUCTURAL_METADATA = "EXT_structural_metadata";
  private static final String EXT_MESH_FEATURES = "EXT_mesh_features";
  private static final int INITIAL_SIZE = 1_024;
  private static final int WARNING_THRESHOLD_FEATURES_PER_FILE = 5_000;
  private static final int MATERIAL = 0;
  private static final String FEATURE_ID = "_featureId";
  private static final String INDICES = "_indices";
  private static final String VERTICES = "_vertices";
  private static final String NORMALS = "_normals";
  private static final String OUTLINE = "_outline";
  private static final String PROPERTY_PREFIX = "_p_";
  private static final int BUFFER_VIEW_NORMALS = 2;
  private static final int BUFFER_VIEW_INDICES = 0;
  private static final int BUFFER_VIEW_VERTICES = 1;
  private static final String STRING_OFFSET = "_string_offset_";

  public static final Map<String, Byte> SURFACE_TYPE_ENUMS =
      ImmutableMap.<String, Byte>builder()
          .put("roof", (byte) 0)
          .put("ground", (byte) 1)
          .put("wall", (byte) 2)
          .put("closure", (byte) 3)
          .put("outer ceiling", (byte) 4)
          .put("outer floor", (byte) 5)
          .put("window", (byte) 6)
          .put("door", (byte) 7)
          .put("interior wall", (byte) 8)
          .put("ceiling", (byte) 9)
          .put("floor", (byte) 10)
          .put("unknown", (byte) 255)
          .build();

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderGltf.class);

  private static final String GML_ID = "gml_id";
  private static final String ID = "id";
  static final String SURFACE_TYPE = "surfaceType";

  private static final int ARRAY_BUFFER = 34_962;
  private static final int ELEMENT_ARRAY_BUFFER = 34_963;

  private static final int BYTE = 5120;
  private static final int UNSIGNED_BYTE = 5121;
  private static final int SHORT = 5122;
  private static final int UNSIGNED_SHORT = 5123;
  private static final int UNSIGNED_INT = 5125;
  private static final int FLOAT = 5126;
  private static final int TRIANGLES = 4;
  public static final String CESIUM_PRIMITIVE_OUTLINE = "CESIUM_primitive_outline";

  private final FeatureTransformationContextGltf transformationContext;
  private final OutputStream outputStream;
  private final ModifiableStateGltf state;

  private ImmutableGltfAsset.Builder builder;
  private List<Integer> nodes;
  private final long transformerStart = System.nanoTime();
  private long processingStart;
  private OptionalLong featuresFetched;
  private OptionalLong featuresMatched;
  private boolean withSurfaceType;
  private int featureCount;
  private long featuresDuration;

  public FeatureEncoderGltf(FeatureTransformationContextGltf transformationContext) {
    super();
    this.transformationContext = transformationContext;
    this.outputStream = new OutputStreamToByteConsumer(this::push);
    this.state = ModifiableStateGltf.create();
  }

  @Override
  public FeatureGltf createFeature() {
    return ModifiableFeatureGltf.create();
  }

  @Override
  public PropertyGltf createProperty() {
    return ModifiablePropertyGltf.create();
  }

  @Override
  public void onStart(ModifiableContext context) {
    this.processingStart = System.nanoTime();
    if (transformationContext.isFeatureCollection()) {
      featuresFetched = context.metadata().getNumberReturned();
      featuresMatched = context.metadata().getNumberMatched();
      if (featuresFetched.orElse(0L) > WARNING_THRESHOLD_FEATURES_PER_FILE) {
        LOGGER.warn("Fetching a large number of features for a tile: {}", featuresFetched);
      }
      if (LOGGER.isTraceEnabled()) {
        context
            .metadata()
            .getNumberMatched()
            .ifPresent(num -> LOGGER.trace("numberMatched {}", num));
        context
            .metadata()
            .getNumberReturned()
            .ifPresent(num -> LOGGER.trace("numberReturned {}", num));
      }
    } else {
      featuresFetched = OptionalLong.empty();
      featuresMatched = OptionalLong.empty();
    }
    this.withSurfaceType =
        transformationContext
            .getApiData()
            .getExtension(GltfConfiguration.class, transformationContext.getCollectionId())
            .map(GltfConfiguration::includeSurfaceType)
            .orElse(false);

    initNewModel();
  }

  @Override
  public void onFeature(FeatureGltf feature) {
    long featureStart = System.nanoTime();

    String fid =
        feature
            .findPropertyByPath(GML_ID)
            .map(PropertyGltf::getFirstValue)
            .orElse(
                feature.findPropertyByPath(ID).map(PropertyGltf::getFirstValue).orElse("feature"));

    // a mesh surface is a set of polygon patches with an optional semantic surface type;
    // split the solid (in case of a building) or the solids (in case of building parts)
    // into mesh surfaces
    MeshSurfaceList surfaces = MeshSurfaceList.of(feature);

    if (!surfaces.isEmpty()) {
      try {
        boolean added =
            addMultiPolygons(
                builder, transformationContext, state, feature, fid, surfaces, withSurfaceType);
        if (added) {
          if (state.getIndices().size() > Integer.MAX_VALUE * 0.9) {
            // flush node, if the indices count is close to the maximum value
            flushNode(builder, transformationContext, state);
            int nextNodeId = state.getNextNodeId();
            nodes.add(nextNodeId++);
            state.setNextNodeId(nextNodeId);
          }
          this.featureCount++;
        }
      } catch (Exception e) {
        LOGGER.error(
            "Error while processing solid geometries of feature '{}', the feature is ignored: {}",
            fid,
            e.getMessage());
        if (LOGGER.isDebugEnabled(MARKER.STACKTRACE)) {
          LOGGER.debug("Stacktrace: ", e);
        }
      }
    }

    this.featuresDuration += System.nanoTime() - featureStart;
  }

  @Override
  public void onEnd(ModifiableContext context) {
    long writingStart = System.nanoTime();

    if (!state.getIndices().isEmpty()) {
      try {
        flushNode(builder, transformationContext, state);
        int nextNodeId = state.getNextNodeId();
        nodes.add(nextNodeId++);
        state.setNextNodeId(nextNodeId);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    finalizeModel();

    if (LOGGER.isDebugEnabled()) {
      long transformerDuration = toMilliseconds(System.nanoTime() - transformerStart);
      long processingDuration = toMilliseconds(System.nanoTime() - processingStart);
      long writingDuration = toMilliseconds(System.nanoTime() - writingStart);
      String text =
          String.format(
              "glTF features fetched: %d, returned: %d, total duration: %dms, processing: %dms, feature processing: %dms, average feature processing: %dms, writing: %dms.",
              featuresFetched.orElse(1L),
              featureCount,
              transformerDuration,
              processingDuration,
              toMilliseconds(featuresDuration),
              featureCount == 0 ? 0 : toMilliseconds(featuresDuration / featureCount),
              writingDuration);
      if (processingDuration > 500) {
        LOGGER.debug(text);
      } else if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(text);
      }
    }
  }

  private static void writeProperty(
      ByteArrayOutputStream buffer,
      ByteArrayOutputStream offsetBuffer,
      String propertyName,
      SchemaProperty property,
      String value,
      Map<String, SchemaEnum> enums,
      OffsetType offsetType)
      throws IOException {
    boolean present = Objects.nonNull(value) && !value.isBlank();
    String effectiveValue =
        present
                && (!enums.containsKey(propertyName)
                    || enums.get(propertyName).getValues().stream()
                        .anyMatch(v -> Integer.parseInt(value) == v.getValue()))
            ? value
            : property.getNoData().orElse(property.getType() == Type.STRING ? "" : "0");
    try {
      switch (property.getType()) {
        case ENUM:
          writeNumber(
              buffer,
              enums.get(property.getEnumType().orElseThrow()).getValueType(),
              effectiveValue);
          break;
        case SCALAR:
          writeNumber(buffer, property.getComponentType().orElseThrow(), effectiveValue);
          break;
        case STRING:
          if (!effectiveValue.isEmpty()) {
            buffer.write(effectiveValue.getBytes(StandardCharsets.UTF_8));
          }
          writeStringOffset(offsetBuffer, propertyName, buffer.size(), offsetType);
          break;
        default:
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "The glTF type of the feature property is currently not supported and is ignored: {}",
                property.getType().toString());
          }
          break;
      }
    } catch (NumberFormatException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Could not parse numeric attribute value '{}' as a number, using '0'.", effectiveValue);
      }
      writeProperty(buffer, offsetBuffer, propertyName, property, "0", enums, offsetType);
    }
  }

  private static void writeNumber(
      ByteArrayOutputStream buffer, ComponentType componentType, String value) throws IOException {
    switch (componentType) {
      case INT8:
      case UINT8:
        buffer.write(Byte.parseByte(value));
        break;
      case INT16:
      case UINT16:
        buffer.write(GltfAsset.intToLittleEndianShort(Integer.parseInt(value)));
        break;
      case INT32:
      case UINT32:
        buffer.write(GltfAsset.intToLittleEndianInt(Integer.parseInt(value)));
        break;
      case FLOAT32:
        buffer.write(GltfAsset.doubleToLittleEndianFloat(Double.parseDouble(value)));
        break;
      case INT64:
      case UINT64:
        buffer.write(GltfAsset.longToLittleEndianLong(Long.parseLong(value)));
        break;
      case FLOAT64:
        buffer.write(GltfAsset.doubleToLittleEndianDouble(Double.parseDouble(value)));
        break;
    }
  }

  private long toMilliseconds(long nanoseconds) {
    return nanoseconds / 1_000_000;
  }

  private void initNewModel() {
    ImmutableAssetMetadata.Builder metadataBuilder =
        ImmutableAssetMetadata.builder()
            .copyright(
                transformationContext
                    .getApiData()
                    .getMetadata()
                    .flatMap(ApiMetadata::getAttribution));
    featuresFetched.ifPresent(n -> metadataBuilder.putExtras("numberReturned", n));
    featuresMatched.ifPresent(n -> metadataBuilder.putExtras("numberMatched", n));

    builder = ImmutableGltfAsset.builder().asset(metadataBuilder.build());

    nodes = new ArrayList<>(8);

    Map<String, ByteArrayOutputStream> buffers = new HashMap<>();
    Map<String, Integer> currentBufferViewOffsets = new HashMap<>();

    buffers.put(INDICES, new ByteArrayOutputStream(getByteStrideIndices() * INITIAL_SIZE));
    currentBufferViewOffsets.put(INDICES, 0);
    buffers.put(VERTICES, new ByteArrayOutputStream(getByteStrideVertices() * INITIAL_SIZE));
    currentBufferViewOffsets.put(VERTICES, 0);
    if (transformationContext.getGltfConfiguration().writeNormals()) {
      buffers.put(NORMALS, new ByteArrayOutputStream(getByteStrideNormals() * INITIAL_SIZE));
      currentBufferViewOffsets.put(NORMALS, 0);
    }
    buffers.put(FEATURE_ID, new ByteArrayOutputStream(getByteStrideFeatureId() * INITIAL_SIZE));
    currentBufferViewOffsets.put(FEATURE_ID, 0);
    if (transformationContext.getGltfConfiguration().writeOutline()) {
      buffers.put(OUTLINE, new ByteArrayOutputStream(getByteStrideOutline() * INITIAL_SIZE));
      currentBufferViewOffsets.put(OUTLINE, 0);
    }
    transformationContext
        .getProperties()
        .forEach(
            (key, value) -> {
              buffers.put(
                  PROPERTY_PREFIX + key,
                  new ByteArrayOutputStream(
                      getInitialBufferSize(value.getType(), value.getComponentType())));
              currentBufferViewOffsets.put(PROPERTY_PREFIX + key, 0);
              if (value.getType() == Type.STRING) {
                ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream(
                        getInitialBufferSize(Type.SCALAR, Optional.of(ComponentType.UINT8)));
                writeStringOffset(
                    buffer, key, 0, transformationContext.getStringOffsetTypes().get(key));
                buffers.put(PROPERTY_PREFIX + key + STRING_OFFSET, buffer);
                currentBufferViewOffsets.put(PROPERTY_PREFIX + key + STRING_OFFSET, buffer.size());
              }
            });

    state.putAllBuffers(buffers);
    state.putAllCurrentBufferViewOffsets(currentBufferViewOffsets);
  }

  private static void writeStringOffset(
      ByteArrayOutputStream buffer, String propertyName, int offset, OffsetType offsetType) {
    try {
      switch (offsetType) {
        case UINT8:
          buffer.write((byte) offset);
          break;
        case UINT32:
          buffer.write(GltfAsset.intToLittleEndianInt(offset));
          break;
        case UINT16:
        default:
          buffer.write(GltfAsset.intToLittleEndianShort(offset));
          break;
      }
    } catch (IOException e) {
      throw new IllegalStateException(
          String.format("Cannot write to string offset buffer for property '%s'.", propertyName),
          e);
    }
  }

  private int getInitialBufferSize(Type type, Optional<ComponentType> componentType) {
    switch (type) {
      case SCALAR:
        switch (componentType.orElseThrow()) {
          case INT8:
          case UINT8:
            return INITIAL_SIZE;
          case INT16:
          case UINT16:
            return 2 * INITIAL_SIZE;
          case INT32:
          case UINT32:
          case FLOAT32:
            return 4 * INITIAL_SIZE;
          case INT64:
          case UINT64:
          case FLOAT64:
            return 8 * INITIAL_SIZE;
        }
      case BOOLEAN:
        return INITIAL_SIZE;
      case STRING:
      default:
        return 8 * INITIAL_SIZE;
    }
  }

  @SuppressWarnings("PMD.UseCollectionIsEmpty")
  private void finalizeModel() {

    Map<String, ByteArrayOutputStream> buffers = state.getBuffers();
    Map<String, Integer> bufferViews = new HashMap<>();

    int offset = 0;
    int size = buffers.get(INDICES).size();
    builder.addBufferViews(
        ImmutableBufferView.builder()
            .buffer(0)
            .byteLength(size)
            .byteOffset(offset)
            .target(ELEMENT_ARRAY_BUFFER)
            .build());
    offset += size;

    size = buffers.get(VERTICES).size();
    builder.addBufferViews(
        ImmutableBufferView.builder()
            .buffer(0)
            .byteLength(size)
            .byteOffset(offset)
            .byteStride(getByteStrideVertices())
            .target(ARRAY_BUFFER)
            .build());
    offset += size;

    int nextBufferViewId = 2;
    if (transformationContext.getGltfConfiguration().writeNormals()) {
      size = buffers.get(NORMALS).size();
      builder.addBufferViews(
          ImmutableBufferView.builder()
              .buffer(0)
              .byteLength(size)
              .byteOffset(offset)
              .byteStride(getByteStrideNormals())
              .target(ARRAY_BUFFER)
              .build());
      offset += size;
      nextBufferViewId++;
    }

    if (!transformationContext.getProperties().isEmpty()) {
      size = buffers.get(FEATURE_ID).size();
      builder.addBufferViews(
          ImmutableBufferView.builder()
              .buffer(0)
              .byteLength(size)
              .byteOffset(offset)
              // each *element* must align to 4-byte boundaries; UNSIGNED_INT is not allowed
              .byteStride(getByteStrideFeatureId())
              .build());
      offset += size;
      nextBufferViewId++;
    }

    if (transformationContext.getGltfConfiguration().writeOutline()) {
      size = buffers.get(OUTLINE).size();
      builder.addBufferViews(
          ImmutableBufferView.builder()
              .buffer(0)
              .byteLength(size)
              .byteOffset(offset)
              .target(ELEMENT_ARRAY_BUFFER)
              .build());
      offset += size;
      nextBufferViewId++;
    }

    for (Map.Entry<String, ByteArrayOutputStream> entry : buffers.entrySet()) {
      String bufferName = entry.getKey();
      if (bufferName.startsWith(PROPERTY_PREFIX) && !bufferName.endsWith(STRING_OFFSET)) {
        ByteArrayOutputStream buffer = entry.getValue();
        size = buffer.size();
        if (size > 0) {
          builder.addBufferViews(
              ImmutableBufferView.builder()
                  .buffer(0)
                  .byteLength(size)
                  .byteOffset(offset)
                  .target(ELEMENT_ARRAY_BUFFER)
                  .build());
          offset += size;
          bufferViews.put(bufferName, nextBufferViewId++);
          ByteArrayOutputStream stringOffsetBuffer = buffers.get(bufferName + STRING_OFFSET);
          if (Objects.nonNull(stringOffsetBuffer)) {
            size = stringOffsetBuffer.size();
            builder.addBufferViews(
                ImmutableBufferView.builder()
                    .buffer(0)
                    .byteLength(size)
                    .byteOffset(offset)
                    .target(ELEMENT_ARRAY_BUFFER)
                    .build());
            offset += size;
            bufferViews.put(bufferName + STRING_OFFSET, nextBufferViewId++);
          }
        }
      }
    }

    if (offset > 0) {
      builder.addBuffers(ImmutableBuffer.builder().byteLength(offset).build());
      builder.addNodes(
          ImmutableNode.builder()
              .children(nodes)
              // z-up (CRS) => y-up (glTF uses y-up)
              .addMatrix(1d, 0d, 0d, 0d, 0d, 0d, -1d, 0d, 0d, 1d, 0d, 0d, 0d, 0d, 0d, 1d)
              .build());
      builder.addScenes(
          ImmutableScene.builder().nodes(ImmutableList.of(state.getNextNodeId())).build());
    }

    if (transformationContext.getGltfConfiguration().useMeshQuantization()) {
      builder.addExtensionsUsed(KHR_MESH_QUANTIZATION);
      builder.addExtensionsRequired(KHR_MESH_QUANTIZATION);
    }

    if (transformationContext.getGltfConfiguration().writeOutline()) {
      builder.addExtensionsUsed(CESIUM_PRIMITIVE_OUTLINE);
    }

    builder.addMaterials(
        ImmutableMaterial.builder()
            .pbrMetallicRoughness(
                ImmutablePbrMetallicRoughness.builder()
                    .baseColorFactor(ImmutableList.of(0.5f, 0.5f, 0.5f, 1.0f))
                    .metallicFactor(0.2f)
                    .roughnessFactor(1.0f)
                    .build())
            .doubleSided(
                transformationContext.getGltfConfiguration().polygonOrientationIsNotGuaranteed())
            .build());

    ImmutablePropertyTable.Builder propertyTableBuilder =
        ImmutablePropertyTable.builder()
            .clazz(transformationContext.getCollectionId())
            .count(state.getNextFeatureId());

    transformationContext
        .getProperties()
        .forEach(
            (propertyName, propertyDefinition) -> {
              if (buffers.get(PROPERTY_PREFIX + propertyName).size() > 0) {
                propertyTableBuilder.putProperties(
                    propertyName,
                    ImmutableProperty.builder()
                        .values(bufferViews.get(PROPERTY_PREFIX + propertyName))
                        .stringOffsets(
                            Optional.ofNullable(
                                bufferViews.get(PROPERTY_PREFIX + propertyName + STRING_OFFSET)))
                        .stringOffsetType(
                            Optional.ofNullable(
                                transformationContext.getStringOffsetTypes().get(propertyName)))
                        .build());
              }
            });

    PropertyTable propertyTable = propertyTableBuilder.build();

    if (transformationContext.getSchemaUri().isPresent() && propertyTable.getCount() > 0) {
      builder
          .putExtensions(
              EXT_STRUCTURAL_METADATA,
              ImmutableMap.of(
                  "schemaUri",
                  transformationContext.getSchemaUri().get().toString(),
                  "propertyTables",
                  ImmutableList.of(propertyTable)))
          .addExtensionsUsed(EXT_STRUCTURAL_METADATA, EXT_MESH_FEATURES);
    }

    Builder<ByteArrayOutputStream> bufferList =
        ImmutableList.<ByteArrayOutputStream>builder()
            .add(buffers.get(INDICES))
            .add(buffers.get(VERTICES));
    if (transformationContext.getGltfConfiguration().writeNormals()) {
      bufferList.add(buffers.get(NORMALS));
    }
    if (!transformationContext.getProperties().isEmpty()) {
      bufferList.add(buffers.get(FEATURE_ID));
    }
    if (transformationContext.getGltfConfiguration().writeOutline()) {
      bufferList.add(buffers.get(OUTLINE));
    }
    bufferList.addAll(
        bufferViews.entrySet().stream()
            .sorted(Comparator.comparingInt(Entry::getValue))
            .map(entry -> buffers.get(entry.getKey()))
            .collect(Collectors.toUnmodifiableList()));

    builder.build().writeGltfBinary(bufferList.build(), outputStream);
  }

  private int getByteStrideIndices() {
    return 2;
  }

  private int getByteStrideVertices() {
    return (transformationContext.getGltfConfiguration().useMeshQuantization() ? 2 : 3) * 4;
  }

  private int getByteStrideNormals() {
    return (transformationContext.getGltfConfiguration().useMeshQuantization() ? 1 : 3) * 4;
  }

  private int getByteStrideOutline() {
    return getByteStrideIndices();
  }

  private int getByteStrideFeatureId() {
    return 4;
  }

  private static boolean addMultiPolygons(
      ImmutableGltfAsset.Builder builder,
      FeatureTransformationContextGltf context,
      ModifiableStateGltf state,
      FeatureGltf feature,
      String featureName,
      MeshSurfaceList surfaces,
      boolean withSurfaceType) {

    double[][] minMax = surfaces.getMinMax();

    List<Double> vertices = new ArrayList<>();
    List<Double> normals = new ArrayList<>();
    List<Integer> indices = new ArrayList<>();
    List<Integer> featureIds = new ArrayList<>();
    List<Integer> outline = new ArrayList<>();
    int indexCount = state.getIndexCount();
    int surfaceCount = state.getSurfaceCount();

    Map<String, ByteArrayOutputStream> buffers = state.getBuffers();

    for (MeshSurface surface : surfaces.getMeshSurfaces()) {
      if (surface.getSurfaceType().filter(t -> t.toLowerCase().startsWith("closure")).isPresent()) {
        // closure surface are virtual surfaces that are not relevant for visualization; skip them
        continue;
      }

      TriangleMesh triangleMesh =
          TriangleMesh.of(
              surface.getGeometry(),
              minMax[0][2],
              context.getClampToEllipsoid(),
              context.getGltfConfiguration().writeNormals(),
              context.getGltfConfiguration().writeOutline(),
              indexCount,
              Optional.of(context.getCrsTransformerCrs84hToEcef()),
              featureName);

      int vertexCountSurface = triangleMesh.getVertices().size() / 3;
      if (vertexCountSurface < 3) {
        continue;
      }

      indices.addAll(triangleMesh.getIndices());
      indexCount += vertexCountSurface;

      vertices.addAll(triangleMesh.getVertices());
      normals.addAll(triangleMesh.getNormals());
      outline.addAll(triangleMesh.getOutlineIndices());

      final int nextFeatureId = state.getNextFeatureId() + 1;
      IntStream.range(0, vertexCountSurface).forEach(i -> featureIds.add(nextFeatureId - 1));
      state.setNextFeatureId(nextFeatureId);

      if (withSurfaceType && context.getProperties().containsKey(SURFACE_TYPE)) {
        ByteArrayOutputStream buffer = buffers.get(PROPERTY_PREFIX + SURFACE_TYPE);
        buffer.write(SURFACE_TYPE_ENUMS.get(surface.getSurfaceType().orElse("unknown")));
      }

      context
          .getProperties()
          .forEach(
              (key, value) -> {
                try {
                  if (!SURFACE_TYPE.equals(key)) {
                    writeProperty(
                        buffers.get(PROPERTY_PREFIX + key),
                        buffers.get(PROPERTY_PREFIX + key + STRING_OFFSET),
                        key,
                        value,
                        feature
                            .findPropertyByPath(key)
                            .map(PropertyGltf::getFirstValue)
                            .orElse(null),
                        context.getGltfSchema().getEnums(),
                        context.getStringOffsetTypes().get(key));
                  }
                } catch (IOException e) {
                  throw new IllegalStateException(e);
                }
              });

      surfaceCount++;
    }

    // glTF output
    if (indices.isEmpty()) {
      return false;
    } else {
      if (LOGGER.isTraceEnabled()) {
        for (int i = 0; i < indices.size() / 3; i++) {
          Integer p0 = indices.get(i * 3);
          Integer p1 = indices.get(i * 3 + 1);
          Integer p2 = indices.get(i * 3 + 2);
          boolean withNormals = context.getGltfConfiguration().writeNormals();

          LOGGER.trace("Indices: {},{},{}", p0, p1, p2);
          LOGGER.trace(
              "Triangle: ({},{},{}) ({},{},{}) ({},{},{}) - ({},{},{})",
              vertices.get(p0 * 3),
              vertices.get(p0 * 3 + 1),
              vertices.get(p0 * 3 + 2),
              vertices.get(p1 * 3),
              vertices.get(p1 * 3 + 1),
              vertices.get(p1 * 3 + 2),
              vertices.get(p2 * 3),
              vertices.get(p2 * 3 + 1),
              vertices.get(p2 * 3 + 2),
              withNormals ? normals.get(p0 * 3) : "-",
              withNormals ? normals.get(p0 * 3 + 1) : "-",
              withNormals ? normals.get(p0 * 3 + 2) : "-");
        }
      }

      state.addAllVertices(vertices);
      state.addAllNormals(normals);
      state.addAllIndices(indices);
      state.addAllFeatureIds(featureIds);
      state.addAllOutline(outline);
      state.setIndexCount(indexCount);
      state.setSurfaceCount(surfaceCount);

      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace(
            "Geometry processing of feature '{}' is complete. Vertices: {}, Indices: {}",
            featureName,
            vertices.size(),
            indices.size());
      }
    }

    return true;
  }

  private static void flushNode(
      ImmutableGltfAsset.Builder builder,
      FeatureTransformationContextGltf context,
      ModifiableStateGltf state)
      throws IOException {

    Map<String, ByteArrayOutputStream> buffers = state.getBuffers();
    Map<String, Integer> currentBufferViewOffsets = state.getCurrentBufferViewOffsets();
    List<Double> vertices = state.getVertices();
    List<Double> normals = state.getNormals();
    List<Integer> indices = state.getIndices();
    List<Integer> featureIds = state.getFeatureIds();
    List<Integer> outline = state.getOutline();
    int surfaceCount = state.getSurfaceCount();

    if (indices.isEmpty()) {
      return;
    }

    int componentType;
    if (indices.size() <= Byte.MAX_VALUE - Byte.MIN_VALUE) {
      componentType = UNSIGNED_BYTE;
    } else if (indices.size() <= Short.MAX_VALUE - Short.MIN_VALUE) {
      componentType = UNSIGNED_SHORT;
    } else {
      componentType = UNSIGNED_INT;
    }

    // write indices and add accessor
    ByteArrayOutputStream buffer = buffers.get(INDICES);
    switch (componentType) {
      case UNSIGNED_BYTE:
        for (int v : indices) {
          buffer.write(GltfAsset.intToLittleEndianByte(v));
        }
        break;

      case UNSIGNED_SHORT:
        for (int v : indices) {
          buffer.write(GltfAsset.intToLittleEndianShort(v));
        }
        break;

      case UNSIGNED_INT:
      default:
        for (int v : indices) {
          buffer.write(GltfAsset.intToLittleEndianInt(v));
        }
        break;
    }

    ImmutableAttributes.Builder attributesBuilder = ImmutableAttributes.builder();
    builder.addAccessors(
        ImmutableAccessor.builder()
            .bufferView(BUFFER_VIEW_INDICES)
            .byteOffset(currentBufferViewOffsets.get(INDICES))
            .componentType(componentType)
            .addMax(indices.stream().max(Comparator.naturalOrder()).orElseThrow())
            .addMin(indices.stream().min(Comparator.naturalOrder()).orElseThrow())
            .count(indices.size())
            .type("SCALAR")
            .build());

    // pad for alignment, all offsets must be divisible by 4
    while (buffers.get(INDICES).size() % 4 > 0) {
      buffers.get(INDICES).write(GltfAsset.BIN_PADDING);
    }

    int nextAccessorId = state.getNextAccessorId();
    int accessorIdIndices = nextAccessorId;
    state.setNextAccessorId(++nextAccessorId);

    currentBufferViewOffsets.put(INDICES, buffer.size());

    // prepare vertices and add accessor

    // vertices are ECEF coordinates, compute center and translate the center as the origin
    double[] min = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
    double[] max = new double[] {Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE};
    for (int n = 0; n < vertices.size() / 3; n++) {
      min[0] = Math.min(min[0], vertices.get(n * 3));
      min[1] = Math.min(min[1], vertices.get(n * 3 + 1));
      min[2] = Math.min(min[2], vertices.get(n * 3 + 2));
      max[0] = Math.max(max[0], vertices.get(n * 3));
      max[1] = Math.max(max[1], vertices.get(n * 3 + 1));
      max[2] = Math.max(max[2], vertices.get(n * 3 + 2));
    }
    double[] origin =
        new double[] {(min[0] + max[0]) / 2.0, (min[1] + max[1]) / 2.0, (min[2] + max[2]) / 2.0};

    for (int n = 0; n < vertices.size() / 3; n++) {
      vertices.set(n * 3, vertices.get(n * 3) - origin[0]);
      vertices.set(n * 3 + 1, vertices.get(n * 3 + 1) - origin[1]);
      vertices.set(n * 3 + 2, vertices.get(n * 3 + 2) - origin[2]);
    }

    final double[] scale;
    final boolean quantizeMesh = context.getGltfConfiguration().useMeshQuantization();
    if (quantizeMesh) {
      // scale vertices to SHORT
      double[] maxAbs = {0d, 0d, 0d};
      for (int n = 0; n < vertices.size(); n++) {
        if (Math.abs(vertices.get(n)) > maxAbs[n % 3]) {
          maxAbs[n % 3] = Math.abs(vertices.get(n));
        }
      }
      scale = IntStream.range(0, 3).mapToDouble(n -> maxAbs[n] / GltfAsset.MAX_SHORT).toArray();

      for (int n = 0; n < vertices.size() / 3; n++) {
        vertices.set(n * 3, vertices.get(n * 3) / scale[0]);
        vertices.set(n * 3 + 1, vertices.get(n * 3 + 1) / scale[1]);
        vertices.set(n * 3 + 2, vertices.get(n * 3 + 2) / scale[2]);
      }

      buffer = buffers.get(VERTICES);
      for (int n = 0; n < vertices.size() / 3; n++) {
        buffer.write(GltfAsset.doubleToLittleEndianShort(vertices.get(n * 3)));
        buffer.write(GltfAsset.doubleToLittleEndianShort(vertices.get(n * 3 + 1)));
        buffer.write(GltfAsset.doubleToLittleEndianShort(vertices.get(n * 3 + 2)));
        // 3 shorts are 6 bytes, add 2 bytes to be aligned with 4-byte boundaries
        buffer.write(GltfAsset.BIN_PADDING);
        buffer.write(GltfAsset.BIN_PADDING);
      }
    } else {
      scale = new double[] {1d, 1d, 1d};

      for (Double v : vertices) {
        buffers.get(VERTICES).write(GltfAsset.doubleToLittleEndianFloat(v));
      }
    }

    final List<Double> verticesMin = getMin(vertices);
    final List<Double> verticesMax = getMax(vertices);
    builder.addAccessors(
        ImmutableAccessor.builder()
            .bufferView(BUFFER_VIEW_VERTICES)
            .byteOffset(currentBufferViewOffsets.get(VERTICES))
            .componentType(quantizeMesh ? SHORT : FLOAT)
            .normalized(false)
            .max(
                quantizeMesh
                    ? ImmutableList.of(
                        Math.round(verticesMax.get(0)),
                        Math.round(verticesMax.get(1)),
                        Math.round(verticesMax.get(2)))
                    : verticesMax)
            .min(
                quantizeMesh
                    ? ImmutableList.of(
                        Math.round(verticesMin.get(0)),
                        Math.round(verticesMin.get(1)),
                        Math.round(verticesMin.get(2)))
                    : verticesMin)
            .count(vertices.size() / 3)
            .type("VEC3")
            .build());
    attributesBuilder.position(nextAccessorId++);
    state.setNextAccessorId(nextAccessorId);
    currentBufferViewOffsets.put(VERTICES, buffers.get(VERTICES).size());

    int nextBufferView = BUFFER_VIEW_NORMALS;
    if (context.getGltfConfiguration().writeNormals()) {
      // write normals and add accessor
      buffer = buffers.get(NORMALS);
      if (quantizeMesh) {
        // scale normals to BYTE
        for (int n = 0; n < normals.size() / 3; n++) {
          normals.set(n * 3, normals.get(n * 3) * GltfAsset.MAX_BYTE);
          normals.set(n * 3 + 1, normals.get(n * 3 + 1) * GltfAsset.MAX_BYTE);
          normals.set(n * 3 + 2, normals.get(n * 3 + 2) * GltfAsset.MAX_BYTE);
        }

        for (int n = 0; n < normals.size() / 3; n++) {
          buffer.write(GltfAsset.doubleToLittleEndianByte(normals.get(n * 3)));
          buffer.write(GltfAsset.doubleToLittleEndianByte(normals.get(n * 3 + 1)));
          buffer.write(GltfAsset.doubleToLittleEndianByte(normals.get(n * 3 + 2)));
          // 3 bytes, add 1 byte to be aligned with 4-byte boundaries
          buffer.write(GltfAsset.BIN_PADDING);
        }
      } else {
        for (Double v : normals) {
          buffer.write(GltfAsset.doubleToLittleEndianFloat(v));
        }
      }

      final List<Double> normalsMin = getMin(normals);
      final List<Double> normalsMax = getMax(normals);
      builder.addAccessors(
          ImmutableAccessor.builder()
              .bufferView(nextBufferView++)
              .byteOffset(currentBufferViewOffsets.get(NORMALS))
              .componentType(quantizeMesh ? BYTE : FLOAT)
              .normalized(quantizeMesh)
              .max(
                  quantizeMesh
                      ? ImmutableList.of(
                          Math.round(normalsMax.get(0)),
                          Math.round(normalsMax.get(1)),
                          Math.round(normalsMax.get(2)))
                      : normalsMax)
              .min(
                  quantizeMesh
                      ? ImmutableList.of(
                          Math.round(normalsMin.get(0)),
                          Math.round(normalsMin.get(1)),
                          Math.round(normalsMin.get(2)))
                      : normalsMin)
              .count(normals.size() / 3)
              .type("VEC3")
              .build());
      attributesBuilder.normal(nextAccessorId++);
      state.setNextAccessorId(nextAccessorId);
      currentBufferViewOffsets.put(NORMALS, buffers.get(NORMALS).size());
    }

    if (!context.getProperties().isEmpty()) {
      // write feature ids and create accessor
      buffer = buffers.get(FEATURE_ID);

      final int nextFeatureId = state.getNextFeatureId();
      if (nextFeatureId <= Byte.MAX_VALUE - Byte.MIN_VALUE) {
        componentType = UNSIGNED_BYTE;
      } else if (nextFeatureId <= Short.MAX_VALUE - Short.MIN_VALUE) {
        componentType = UNSIGNED_SHORT;
      } else {
        componentType = UNSIGNED_INT;
      }

      // write indices and add accessor
      switch (componentType) {
        case UNSIGNED_BYTE:
          for (int i = 0; i < vertices.size() / 3; i++) {
            buffer.write(GltfAsset.intToLittleEndianByte(featureIds.get(i)));
            buffer.write(GltfAsset.BIN_PADDING);
            buffer.write(GltfAsset.BIN_PADDING);
            buffer.write(GltfAsset.BIN_PADDING);
          }
          break;

        case UNSIGNED_SHORT:
          for (int i = 0; i < vertices.size() / 3; i++) {
            buffer.write(GltfAsset.intToLittleEndianShort(featureIds.get(i)));
            buffer.write(GltfAsset.BIN_PADDING);
            buffer.write(GltfAsset.BIN_PADDING);
          }
          break;

        case UNSIGNED_INT:
        default:
          for (int i = 0; i < vertices.size() / 3; i++) {
            buffer.write(GltfAsset.intToLittleEndianInt(featureIds.get(i)));
          }
          break;
      }

      builder.addAccessors(
          ImmutableAccessor.builder()
              .bufferView(nextBufferView++)
              .byteOffset(currentBufferViewOffsets.get(FEATURE_ID))
              .componentType(componentType)
              .count(featureIds.size())
              .type("SCALAR")
              .build());
      attributesBuilder.featureId0(nextAccessorId++);
      state.setNextAccessorId(nextAccessorId);
      currentBufferViewOffsets.put(FEATURE_ID, buffer.size());
    }

    Integer accessorIdOutline = null;
    if (context.getGltfConfiguration().writeOutline()) {
      // write outline edges and add accessor
      if (indices.size() <= Short.MAX_VALUE - Short.MIN_VALUE) {
        componentType = UNSIGNED_SHORT;
      } else {
        componentType = UNSIGNED_INT;
      }

      // write indices and add accessor
      buffer = buffers.get(OUTLINE);
      switch (componentType) {
        case UNSIGNED_SHORT:
          for (int v : outline) {
            buffer.write(GltfAsset.intToLittleEndianShort(v));
          }
          break;

        case UNSIGNED_INT:
        default:
          for (int v : outline) {
            buffer.write(GltfAsset.intToLittleEndianInt(v));
          }
          break;
      }

      builder.addAccessors(
          ImmutableAccessor.builder()
              .bufferView(nextBufferView++)
              .byteOffset(currentBufferViewOffsets.get(OUTLINE))
              .componentType(componentType)
              .addMax(outline.stream().max(Comparator.naturalOrder()).orElseThrow())
              .addMin(outline.stream().min(Comparator.naturalOrder()).orElseThrow())
              .count(outline.size())
              .type("SCALAR")
              .build());

      // pad for alignment, all offsets must be divisible by 4
      while (buffers.get(OUTLINE).size() % 4 > 0) {
        buffers.get(OUTLINE).write(GltfAsset.BIN_PADDING);
      }

      accessorIdOutline = nextAccessorId++;
      state.setNextAccessorId(nextAccessorId);
      currentBufferViewOffsets.put(OUTLINE, buffers.get(OUTLINE).size());
    }

    // add mesh and node for the feature
    Builder<Map<String, Object>> featureIdsBuilder = ImmutableList.builder();
    if (!context.getProperties().isEmpty()) {
      featureIdsBuilder.add(
          ImmutableMap.of("featureCount", surfaceCount, "attribute", 0, "propertyTable", 0));
    }
    List<Map<String, Object>> meshFeatures = featureIdsBuilder.build();
    ImmutableMap.Builder<String, Map<String, Object>> extensionsBuilder =
        new ImmutableMap.Builder<>();
    if (!meshFeatures.isEmpty()) {
      extensionsBuilder.put(EXT_MESH_FEATURES, ImmutableMap.of("featureIds", meshFeatures));
    }
    if (Objects.nonNull(accessorIdOutline)) {
      extensionsBuilder.put(
          CESIUM_PRIMITIVE_OUTLINE, ImmutableMap.of("indices", accessorIdOutline));
    }
    builder.addMeshes(
        ImmutableMesh.builder()
            .addPrimitives(
                ImmutablePrimitive.builder()
                    .attributes(attributesBuilder.build())
                    .mode(TRIANGLES)
                    .indices(accessorIdIndices)
                    .material(MATERIAL)
                    .extensions(extensionsBuilder.build())
                    .build())
            .build());

    int nextMeshId = state.getNextMeshId();
    builder.addNodes(
        ImmutableNode.builder()
            .mesh(nextMeshId++)
            .matrix(
                ImmutableList.of(
                    scale[0], 0d, 0d, 0d, 0d, scale[1], 0d, 0d, 0d, 0d, scale[2], 0d, origin[0],
                    origin[1], origin[2], 1d))
            .build());
    state.setNextMeshId(nextMeshId);

    state.setVertices(new ArrayList<>());
    state.setNormals(new ArrayList<>());
    state.setIndices(new ArrayList<>());
    state.setFeatureIds(new ArrayList<>());
    state.setOutline(new ArrayList<>());
    state.setVertices(new ArrayList<>());
    state.setIndexCount(0);
    state.setSurfaceCount(0);
  }

  private static List<Double> getMin(List<Double> vertices) {
    ImmutableList.Builder<Double> builder = ImmutableList.builder();
    IntStream.range(0, 3)
        .forEach(
            axis ->
                IntStream.range(0, vertices.size())
                    .filter(n -> n % 3 == axis)
                    .mapToObj(vertices::get)
                    .min(Comparator.naturalOrder())
                    .ifPresentOrElse(
                        builder::add,
                        () -> {
                          throw new IllegalStateException(
                              String.format(
                                  "glTF generation: Cannot compute minimum for axis %d for vertices: %s",
                                  axis, vertices));
                        }));
    return builder.build();
  }

  private static List<Double> getMax(List<Double> vertices) {
    ImmutableList.Builder<Double> builder = ImmutableList.builder();
    IntStream.range(0, 3)
        .forEach(
            axis ->
                IntStream.range(0, vertices.size())
                    .filter(n -> n % 3 == axis)
                    .mapToObj(vertices::get)
                    .max(Comparator.naturalOrder())
                    .ifPresentOrElse(
                        builder::add,
                        () -> {
                          throw new IllegalStateException(
                              String.format(
                                  "glTF generation: Cannot compute maximum for axis %d for vertices: %s",
                                  axis, vertices));
                        }));
    return builder.build();
  }
}
