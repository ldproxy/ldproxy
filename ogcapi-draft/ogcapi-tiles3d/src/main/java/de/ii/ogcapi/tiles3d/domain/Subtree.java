/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.Query;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.tiles3d.domain.QueriesHandler3dTiles.QueryInputSubtreeOld;
import de.ii.xtraplatform.cql.domain.And;
import de.ii.xtraplatform.cql.domain.Bbox;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.SIntersects;
import de.ii.xtraplatform.cql.domain.SpatialLiteral;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles3d.domain.spec.Availability;
import de.ii.xtraplatform.tiles3d.domain.spec.Buffer;
import de.ii.xtraplatform.tiles3d.domain.spec.BufferView;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutableAvailability;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutableBuffer;
import de.ii.xtraplatform.tiles3d.domain.spec.ImmutableBufferView;
import de.ii.xtraplatform.tiles3d.domain.spec.MetadataEntity;
import de.ii.xtraplatform.tiles3d.domain.spec.PropertyTable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.immutables.value.Value;
import org.locationtech.jts.shape.fractal.MortonCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableSubtree.Builder.class)
public interface Subtree {

  Logger LOGGER = LoggerFactory.getLogger(Subtree.class);

  @SuppressWarnings("UnstableApiUsage")
  Funnel<Subtree> FUNNEL =
      (from, into) -> {
        from.getBuffers().forEach(v -> Buffer.FUNNEL.funnel(v, into));
        from.getBufferViews().forEach(v -> BufferView.FUNNEL.funnel(v, into));
        from.getPropertyTables().forEach(v -> PropertyTable.FUNNEL.funnel(v, into));
        Availability.FUNNEL.funnel(from.getTileAvailability(), into);
        from.getContentAvailability().forEach(v -> Availability.FUNNEL.funnel(v, into));
        Availability.FUNNEL.funnel(from.getChildSubtreeAvailability(), into);
        from.getTileMetadata().ifPresent(into::putInt);
        from.getContentMetadata().forEach(into::putInt);
        from.getSubtreeMetadata().ifPresent(v -> MetadataEntity.FUNNEL.funnel(v, into));
      };

  ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          // for debugging
          // .enable(SerializationFeature.INDENT_OUTPUT)
          .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

  byte[] MAGIC_SUBT = {0x73, 0x75, 0x62, 0x74};
  byte[] VERSION_1 = {0x01, 0x00, 0x00, 0x00};
  byte[] JSON_PADDING = {0x20};
  byte[] BIN_PADDING = {0x00};
  byte[] EMPTY = new byte[0];

  static Subtree of(
      ServicesContext servicesContext,
      FeaturesCoreQueriesHandler queriesHandler,
      QueryInputSubtreeOld queryInput,
      TileResourceDescriptor subtree) {
    int size = 0;
    for (int i = 0; i < queryInput.getSubtreeLevels(); i++) {
      size += MortonCode.size(i);
    }
    byte[] tileAvailability = new byte[(size - 1) / 8 + 1];
    Arrays.fill(tileAvailability, (byte) 0);
    byte[] contentAvailability = new byte[(size - 1) / 8 + 1];
    Arrays.fill(contentAvailability, (byte) 0);
    byte[] childSubtreeAvailability =
        new byte[(MortonCode.size(queryInput.getSubtreeLevels()) - 1) / 8 + 1];
    Arrays.fill(childSubtreeAvailability, (byte) 0);

    processZ(
        queryInput,
        servicesContext,
        queriesHandler,
        subtree.getLevel(),
        subtree.getX(),
        subtree.getY(),
        subtree.getLevel(),
        subtree.getX(),
        subtree.getY(),
        tileAvailability,
        contentAvailability,
        childSubtreeAvailability);

    return buildSubtree(
        queryInput, size, tileAvailability, contentAvailability, childSubtreeAvailability);
  }

  @SuppressWarnings("PMD.ExcessiveMethodLength")
  private static ImmutableSubtree buildSubtree(
      QueryInputSubtreeOld queryInput,
      int size,
      byte[] tileAvailability,
      byte[] contentAvailability,
      byte[] childSubtreeAvailability) {
    Boolean tileAvailabilityConstantValue = null;
    Boolean contentAvailabilityConstantValue = null;
    Boolean childSubtreeAvailabilityConstantValue = null;

    int tileAvailabilityCount =
        getAvailabilityCount(tileAvailability, 0, queryInput.getSubtreeLevels());
    if (tileAvailabilityCount == 0) {
      tileAvailabilityConstantValue = false;
      contentAvailabilityConstantValue = false;
      childSubtreeAvailabilityConstantValue = false;
    } else if (tileAvailabilityCount == size) {
      tileAvailabilityConstantValue = true;
    }

    int contentAvailabilityCount = 0;
    int childSubtreeAvailabilityCount = 0;

    if (tileAvailabilityCount > 0) {
      contentAvailabilityCount =
          getAvailabilityCount(contentAvailability, 0, queryInput.getSubtreeLevels());
      if (contentAvailabilityCount == 0) {
        contentAvailabilityConstantValue = false;
      } else if (contentAvailabilityCount == size) {
        contentAvailabilityConstantValue = true;
      }

      childSubtreeAvailabilityCount =
          getAvailabilityCount(childSubtreeAvailability, queryInput.getSubtreeLevels(), 1);
      if (childSubtreeAvailabilityCount == 0) {
        childSubtreeAvailabilityConstantValue = false;
      } else if (childSubtreeAvailabilityCount == MortonCode.size(queryInput.getSubtreeLevels())) {
        childSubtreeAvailabilityConstantValue = true;
      }
    }

    ImmutableSubtree.Builder builder = ImmutableSubtree.builder();

    int length =
        (Objects.nonNull(tileAvailabilityConstantValue) ? 0 : tileAvailability.length)
            + (Objects.nonNull(contentAvailabilityConstantValue) ? 0 : contentAvailability.length)
            + (Objects.nonNull(childSubtreeAvailabilityConstantValue)
                ? 0
                : childSubtreeAvailability.length);
    builder.addBuffers(ImmutableBuffer.builder().byteLength(length).build());

    int bitstream = 0;
    int byteOffset = 0;
    if (Objects.nonNull(tileAvailabilityConstantValue)) {
      builder.tileAvailability(
          ImmutableAvailability.builder().constant(tileAvailabilityConstantValue ? 1 : 0).build());
    } else {
      builder
          .tileAvailability(getAvailability(bitstream++, tileAvailabilityCount))
          .addBufferViews(getBufferView(tileAvailability, byteOffset));
      byteOffset += tileAvailability.length;
    }
    if (Objects.nonNull(contentAvailabilityConstantValue)) {
      builder.contentAvailability(
          ImmutableList.of(
              ImmutableAvailability.builder()
                  .constant(contentAvailabilityConstantValue ? 1 : 0)
                  .build()));
    } else {
      builder
          .contentAvailability(
              ImmutableList.of(getAvailability(bitstream++, contentAvailabilityCount)))
          .addBufferViews(getBufferView(contentAvailability, byteOffset));
      byteOffset += contentAvailability.length;
    }
    if (Objects.nonNull(childSubtreeAvailabilityConstantValue)) {
      builder.childSubtreeAvailability(
          ImmutableAvailability.builder()
              .constant(childSubtreeAvailabilityConstantValue ? 1 : 0)
              .build());
    } else {
      builder
          .childSubtreeAvailability(getAvailability(bitstream, childSubtreeAvailabilityCount))
          .addBufferViews(getBufferView(childSubtreeAvailability, byteOffset));
    }

    if (Objects.isNull(tileAvailabilityConstantValue)) {
      builder.tileAvailabilityBin(tileAvailability);
    }

    if (Objects.isNull(contentAvailabilityConstantValue)) {
      builder.contentAvailabilityBin(contentAvailability);
    }

    if (Objects.isNull(childSubtreeAvailabilityConstantValue)) {
      builder.childSubtreeAvailabilityBin(childSubtreeAvailability);
    }

    // Unsupported options:
    // List<PropertyTable> getPropertyTables();
    // Optional<Integer> getTileMetadata();
    // List<Integer> getContentMetadata();
    // MetadataEntity getSubtreeMetadata();

    logAvailability(
        tileAvailability,
        contentAvailability,
        childSubtreeAvailability,
        queryInput.getSubtreeLevels());

    return builder.build();
  }

  private static BufferView getBufferView(byte[] tileAvailability, int byteOffset) {
    return ImmutableBufferView.builder()
        .buffer(0)
        .byteOffset(byteOffset)
        .byteLength(tileAvailability.length)
        .build();
  }

  private static Availability getAvailability(int bitstream, int availabilityCount) {
    return ImmutableAvailability.builder()
        .bitstream(bitstream)
        .availabilityCount(availabilityCount)
        .build();
  }

  static Subtree of(byte[] subtreeBytes) throws IOException {
    if (!Arrays.equals(Arrays.copyOfRange(subtreeBytes, 0, 4), MAGIC_SUBT)) {
      throw new IllegalStateException(
          String.format(
              "Invalid 3D Tiles subtree, invalid magic number. Found: %s",
              Arrays.toString(Arrays.copyOfRange(subtreeBytes, 0, 4))));
    }

    final int version = littleEndianIntToInt(subtreeBytes, 4);
    if (version != 1) {
      throw new IllegalStateException(
          String.format(
              "Unsupported 3D Tiles subtree, only version 1 is supported. Found: %d", version));
    }

    final int jsonLength = littleEndianLongToInt(subtreeBytes, 8);
    final byte[] jsonContent = Arrays.copyOfRange(subtreeBytes, 24, 24 + jsonLength);
    Subtree subtreeWithEmptyBuffers = MAPPER.readValue(jsonContent, Subtree.class);

    int jsonPadding = (8 - jsonLength % 8) % 8;
    int bufferOffset = 24 + jsonLength + jsonPadding;

    ImmutableSubtree.Builder builder = ImmutableSubtree.builder().from(subtreeWithEmptyBuffers);

    subtreeWithEmptyBuffers
        .getTileAvailability()
        .getBitstream()
        .ifPresent(
            i ->
                builder.tileAvailabilityBin(
                    getBufferViewContent(subtreeBytes, subtreeWithEmptyBuffers, bufferOffset, i)));

    subtreeWithEmptyBuffers
        .getContentAvailability()
        .get(0)
        .getBitstream()
        .ifPresent(
            i ->
                builder.contentAvailabilityBin(
                    getBufferViewContent(subtreeBytes, subtreeWithEmptyBuffers, bufferOffset, i)));

    subtreeWithEmptyBuffers
        .getChildSubtreeAvailability()
        .getBitstream()
        .ifPresent(
            i ->
                builder.childSubtreeAvailabilityBin(
                    getBufferViewContent(subtreeBytes, subtreeWithEmptyBuffers, bufferOffset, i)));

    return builder.build();
  }

  private static byte[] getBufferViewContent(
      byte[] subtreeBytes, Subtree subtreeWithEmptyBuffers, int bufferOffset, Integer i) {
    BufferView bv = subtreeWithEmptyBuffers.getBufferViews().get(i);
    checkBuffer(bv);
    final int offset = bufferOffset + bv.getByteOffset();
    return Arrays.copyOfRange(subtreeBytes, offset, offset + bv.getByteLength());
  }

  private static void checkBuffer(BufferView bv) {
    if (bv.getBuffer() != 0) {
      throw new IllegalStateException(
          String.format(
              "Invalid 3D Tiles subtree, only subtrees with a single buffer are supported. Found index: %d",
              bv.getBuffer()));
    }
  }

  static byte[] getBinary(Subtree subtree) {

    byte[] json;
    try {
      json = MAPPER.writeValueAsBytes(subtree);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          String.format("Could not write 3D Tiles subtree. Reason: %s", e.getMessage()), e);
    }

    int bufferLength =
        subtree.getTileAvailabilityBin().length
            + subtree.getContentAvailabilityBin().length
            + subtree.getChildSubtreeAvailabilityBin().length;
    int jsonPadding = (8 - json.length % 8) % 8;
    int bufferPadding = (8 - bufferLength % 8) % 8;

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      outputStream.write(MAGIC_SUBT);
      outputStream.write(VERSION_1);

      outputStream.write(intToLittleEndianLong(json.length + jsonPadding));
      outputStream.write(intToLittleEndianLong(bufferLength + bufferPadding));

      outputStream.write(json);
      for (int i = 0; i < jsonPadding; i++) {
        outputStream.write(JSON_PADDING);
      }

      if (subtree.getTileAvailabilityBin().length > 0) {
        outputStream.write(subtree.getTileAvailabilityBin());
      }
      if (subtree.getContentAvailabilityBin().length > 0) {
        outputStream.write(subtree.getContentAvailabilityBin());
      }
      if (subtree.getChildSubtreeAvailabilityBin().length > 0) {
        outputStream.write(subtree.getChildSubtreeAvailabilityBin());
      }
      for (int i = 0; i < bufferPadding; i++) {
        outputStream.write(BIN_PADDING);
      }

      outputStream.flush();
    } catch (Exception e) {
      throw new IllegalStateException("Could not write 3D Tiles Subtree output", e);
    }

    return outputStream.toByteArray();
  }

  private static void processZ(
      QueryInputSubtreeOld queryInput,
      ServicesContext servicesContext,
      FeaturesCoreQueriesHandler queriesHandler,
      int baseLevel,
      int xBase,
      int yBase,
      int level,
      int x0,
      int y0,
      byte[] tileAvailability,
      byte[] contentAvailability,
      byte[] childSubtreeAvailability) {

    if (level > queryInput.getMaxLevel() || level - baseLevel > queryInput.getSubtreeLevels()) {
      return;
    }

    Optional<Cql2Expression> additionalFilter;
    int i0 = MortonCode.encode(x0 - xBase, y0 - yBase);
    for (int i = 0; i < ((level - baseLevel) == 0 ? 1 : 4); i++) {
      int x1 = x0 + (i % 2);
      int y1 = y0 + (i / 2);

      BoundingBox bbox =
          TileResourceDescriptor.subtreeOf(
                  queryInput.getApi(), queryInput.getCollectionId(), level, x1, y1)
              .computeBbox();
      int relativeLevel = level - queryInput.getFirstLevelWithContent();
      additionalFilter =
          relativeLevel >= 0 && queryInput.getTileFilters().size() > relativeLevel
              ? Optional.of(queryInput.getTileFilters().get(relativeLevel))
              : Optional.empty();
      boolean hasData =
          hasData(servicesContext, queriesHandler, queryInput, bbox, additionalFilter);
      if (hasData) {
        if (level - baseLevel < queryInput.getSubtreeLevels()) {
          setAvailability(tileAvailability, 0, level - baseLevel, i0 + i);
          if (relativeLevel >= 0) {
            if (queryInput.getContentFilters().isEmpty()) {
              setAvailability(contentAvailability, 0, level - baseLevel, i0 + i);
            } else {
              additionalFilter =
                  queryInput.getContentFilters().size() > relativeLevel
                      ? Optional.of(queryInput.getContentFilters().get(relativeLevel))
                      : Optional.empty();
              hasData =
                  hasData(servicesContext, queriesHandler, queryInput, bbox, additionalFilter);
              if (hasData) {
                setAvailability(contentAvailability, 0, level - baseLevel, i0 + i);
              }
            }
          }

          processZ(
              queryInput,
              servicesContext,
              queriesHandler,
              baseLevel,
              xBase * 2,
              yBase * 2,
              level + 1,
              x1 * 2,
              y1 * 2,
              tileAvailability,
              contentAvailability,
              childSubtreeAvailability);
        } else {
          setAvailability(
              childSubtreeAvailability,
              queryInput.getSubtreeLevels(),
              queryInput.getSubtreeLevels(),
              i0 + i);
        }
      }
    }
  }

  private static boolean hasData(
      ServicesContext servicesContext,
      FeaturesCoreQueriesHandler queriesHandler,
      QueryInputSubtreeOld queryInput,
      BoundingBox bbox,
      Optional<Cql2Expression> additionalFilter) {
    Bbox bbox2 =
        Bbox.of(bbox.getXmin(), bbox.getYmin(), bbox.getXmax(), bbox.getYmax(), bbox.getEpsgCrs());

    Cql2Expression filter =
        SIntersects.of(Property.of(queryInput.getGeometryProperty()), SpatialLiteral.of(bbox2));

    if (additionalFilter.isPresent()) {
      filter = And.of(filter, additionalFilter.get());
    }

    FeatureQuery query =
        ImmutableFeatureQuery.builder()
            .type(queryInput.getFeatureType())
            .hitsOnly(true)
            .limit(1)
            .filter(filter)
            .build();

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInputHits =
        new ImmutableQueryInputFeatures.Builder()
            .collectionId(queryInput.getCollectionId())
            .query(query)
            .featureProvider(queryInput.getFeatureProvider())
            .defaultCrs(OgcCrs.CRS84h)
            .defaultPageSize(1)
            .sendResponseAsStream(false)
            .build();

    ApiRequestContext requestContext =
        new ImmutableStaticRequestContext.Builder()
            .webContext(servicesContext)
            .mediaType(
                new ImmutableApiMediaType.Builder()
                    .type(new MediaType("model", "gltf-binary"))
                    .label("glTF-Binary")
                    .parameter("glb")
                    .build())
            .alternateMediaTypes(ImmutableList.of())
            .language(Locale.ENGLISH)
            .api(queryInput.getApi())
            .requestUri(
                queryInput
                    .getServicesUri()
                    .resolve(
                        String.join(
                            "/",
                            ImmutableList.of(
                                "collections", queryInput.getCollectionId(), "items"))))
            .queryParameterSet(QueryParameterSet.of())
            .build();

    return getNumberMatched(queriesHandler, queryInputHits, requestContext) != 0L;
  }

  private static long getNumberMatched(
      FeaturesCoreQueriesHandler queriesHandler,
      FeaturesCoreQueriesHandler.QueryInputFeatures queryInputHits,
      ApiRequestContext requestContext) {
    try (Response response =
        queriesHandler.handle(Query.FEATURES, queryInputHits, requestContext)) {
      return Long.parseLong(
          Objects.requireNonNullElse(response.getHeaderString("OGC-NumberMatched"), "0"));
    }
  }

  private static void setAvailability(
      byte[] availability, int startLevel, int level, int idxLevel) {
    int idx = idxLevel;
    // add shift from previous levels
    for (int i = startLevel; i < level; i++) {
      idx += MortonCode.size(i);
    }
    int byteIndex = idx / 8;
    int bitIndex = idx % 8;
    availability[byteIndex] |= 1 << bitIndex;
  }

  private static void logAvailability(
      byte[] tileAvailability,
      byte[] contentAvailability,
      byte[] childSubtreeAvailability,
      int subtreeLevels) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Tile Availability: {}", getAvailabilityString(tileAvailability, 0, subtreeLevels - 1));
      LOGGER.trace(
          "Content Availability: {}",
          getAvailabilityString(contentAvailability, 0, subtreeLevels - 1));
      LOGGER.trace(
          "Child Subtree Availability: {}",
          getAvailabilityString(childSubtreeAvailability, subtreeLevels, subtreeLevels));
    }
  }

  private static boolean getAvailability(
      byte[] availability, int startLevel, int level, int idxLevel) {
    int idx = idxLevel;
    // add shift from previous levels
    for (int i = startLevel; i < level; i++) {
      idx += MortonCode.size(i);
    }
    int byteIndex = idx / 8;
    int bitIndex = idx % 8;
    int bitValue = (availability[byteIndex] >> bitIndex) & 1;
    return bitValue == 1;
  }

  private static String getAvailabilityString(byte[] availability, int minLevel, int maxLevel) {
    StringBuilder s = new StringBuilder();
    for (int level = minLevel; level <= maxLevel; level++) {
      for (int i = 0; i < MortonCode.size(level); i++) {
        s.append(getAvailability(availability, minLevel, level, i) ? "1" : "0");
        if (i % 4 == 3) {
          s.append(' ');
        }
      }
      s.append(" / ");
    }
    return s.toString();
  }

  private static int getAvailabilityCount(byte[] availability, int minLevel, int levels) {
    int count = 0;
    for (int i = 0; i < levels; i++) {
      for (int j = 0; j < MortonCode.size(minLevel + i); j++) {
        if (getAvailability(availability, minLevel, minLevel + i, j)) {
          count++;
        }
      }
    }
    return count;
  }

  private static byte[] intToLittleEndianLong(int v) {
    ByteBuffer bb = ByteBuffer.allocate(8);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    bb.putInt(v);
    return bb.array();
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

  private static int littleEndianLongToInt(byte[] array, int offset) {
    ByteBuffer bb = ByteBuffer.allocate(8);
    bb.order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < 8; i++) {
      bb.put(array[offset + i]);
    }
    bb.rewind();
    return (int) bb.getLong();
  }

  List<Buffer> getBuffers();

  List<BufferView> getBufferViews();

  List<PropertyTable> getPropertyTables();

  Availability getTileAvailability();

  List<Availability> getContentAvailability();

  Availability getChildSubtreeAvailability();

  Optional<Integer> getTileMetadata();

  List<Integer> getContentMetadata();

  Optional<MetadataEntity> getSubtreeMetadata();

  @JsonIgnore
  @Value.Default
  default byte[] getTileAvailabilityBin() {
    return EMPTY;
  }

  @JsonIgnore
  @Value.Default
  default byte[] getContentAvailabilityBin() {
    return EMPTY;
  }

  @JsonIgnore
  @Value.Default
  default byte[] getChildSubtreeAvailabilityBin() {
    return EMPTY;
  }
}
