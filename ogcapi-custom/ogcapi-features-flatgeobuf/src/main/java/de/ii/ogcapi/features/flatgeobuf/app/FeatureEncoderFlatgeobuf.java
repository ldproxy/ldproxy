/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.flatgeobuf.app;

import com.google.flatbuffers.FlatBufferBuilder;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.features.domain.transform.FeatureEncoderSfFlat;
import de.ii.xtraplatform.features.domain.transform.FeatureSfFlat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.Constants;
import org.wololo.flatgeobuf.GeometryConversions;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.Feature;
import org.wololo.flatgeobuf.generated.GeometryType;

public class FeatureEncoderFlatgeobuf extends FeatureEncoderSfFlat {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderFlatgeobuf.class);

  private final String collectionId;
  private final FeatureSchema featureSchema;
  private final GeometryFactory geometryFactory;
  private final int srid;
  private final boolean is3d;
  private final FlatBufferBuilder builder;
  private HeaderMeta headerMeta;

  public FeatureEncoderFlatgeobuf(EncodingContextFlatgeobuf encodingContext) {
    super(encodingContext);
    this.srid =
        encodingContext
            .getCrsTransformer()
            .map(CrsTransformer::getTargetCrs)
            .map(EpsgCrs::getCode)
            .orElse(4326);
    this.is3d = encodingContext.getIs3d();
    this.featureSchema = encodingContext.getSchema();
    this.collectionId = encodingContext.getCollectionId();
    this.geometryFactory = new GeometryFactory();
    this.builder = new FlatBufferBuilder(16 * 1024); // 16kB
  }

  @Override
  public void onStart(ModifiableContext context) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Start generating Flatgeobuf file for collection {}.", collectionId);
    }
    this.processingStart = System.nanoTime();

    try {
      push(Constants.MAGIC_BYTES);
      headerMeta =
          getHeader(collectionId, context.metadata().getNumberReturned().orElse(0)); // 0 = unknown
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      HeaderMeta.write(headerMeta, baos, builder);
      push(baos.toByteArray());
      builder.clear();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not write to Flatgeobuf output stream: " + e.getMessage(), e);
    }
  }

  @Override
  public void onFeature(FeatureSfFlat feature) {
    long startFeature = System.nanoTime();

    try {
      Geometry currentGeometry = feature.getJtsGeometry(geometryFactory).orElse(null);

      // fix invalid source geometries
      if (Objects.nonNull(currentGeometry) && !currentGeometry.isValid()) {
        currentGeometry = new GeometryFixer(currentGeometry).getResult();
      }

      final int propertiesOffset = addProperties(feature.getPropertiesAsMap());

      // promote primitives to multi, if this is the specified geometry type;
      // data from some sources (e.g., Shapefile) supports to mix primitives and aggregates
      if (currentGeometry instanceof Polygon
          && headerMeta.geometryType == GeometryType.MultiPolygon) {
        Polygon[] array = new Polygon[1];
        array[0] = (Polygon) currentGeometry;
        currentGeometry = currentGeometry.getFactory().createMultiPolygon(array);
      } else if (currentGeometry instanceof LineString
          && headerMeta.geometryType == GeometryType.MultiLineString) {
        LineString[] array = new LineString[1];
        array[0] = (LineString) currentGeometry;
        currentGeometry = currentGeometry.getFactory().createMultiLineString(array);
      } else if (currentGeometry instanceof Point
          && headerMeta.geometryType == GeometryType.MultiPoint) {
        Point[] array = new Point[1];
        array[0] = (Point) currentGeometry;
        currentGeometry = currentGeometry.getFactory().createMultiPoint(array);
      }
      final int geometryOffset;
      geometryOffset =
          Objects.nonNull(currentGeometry)
              ? GeometryConversions.serialize(builder, currentGeometry, headerMeta.geometryType)
              : 0;
      final int featureOffset = Feature.createFeature(builder, geometryOffset, propertiesOffset, 0);
      builder.finishSizePrefixed(featureOffset);
      push(builder.sizedByteArray());
      builder.clear();
      written++;

    } catch (Exception e) {
      LOGGER.error(
          "Error while processing feature {} in collection {}. The feature is skipped. Error: {}",
          feature.getIdValue(),
          collectionId,
          e.getMessage());
      if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
        LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace:", e);
      }
    }

    featureDuration += System.nanoTime() - startFeature;
  }

  @Override
  public void onEnd(ModifiableContext context) {
    if (LOGGER.isTraceEnabled()) {
      long transformerDuration = (System.nanoTime() - transformerStart) / 1000000;
      long processingDuration = (System.nanoTime() - processingStart) / 1000000;
      LOGGER.trace(
          String.format(
              "Collection %s, features returned: %d, written: %d, total duration: %dms, processing: %dms, feature processing: %dms.",
              collectionId,
              context.metadata().getNumberReturned().orElse(0),
              written,
              transformerDuration,
              processingDuration,
              featureDuration / 1000000));
    }
  }

  private HeaderMeta getHeader(String name, long featureCount) {
    List<ColumnMeta> columns = new ArrayList<>();

    HeaderMeta headerMeta = new HeaderMeta();
    headerMeta.name = name;
    headerMeta.featuresCount = featureCount;

    byte geometryType = GeometryType.Unknown;
    for (FeatureSchema schema : featureSchema.getProperties()) {
      if (schema.getType() == SchemaBase.Type.GEOMETRY) {
        // Flatgeobuf can handle only one geometry; ignore all geometries except the primary
        // geometry
        if (schema.isPrimaryGeometry()) {
          geometryType =
              schema
                  .getGeometryType()
                  .map(
                      t ->
                          (byte)
                              switch (t) {
                                case POINT -> GeometryConversions.toGeometryType(Point.class);
                                case MULTI_POINT -> GeometryConversions.toGeometryType(
                                    MultiPoint.class);
                                case LINE_STRING -> GeometryConversions.toGeometryType(
                                    LineString.class);
                                case MULTI_LINE_STRING -> GeometryConversions.toGeometryType(
                                    MultiLineString.class);
                                case POLYGON -> GeometryConversions.toGeometryType(Polygon.class);
                                case MULTI_POLYGON -> GeometryConversions.toGeometryType(
                                    MultiPolygon.class);
                                case GEOMETRY_COLLECTION -> GeometryConversions.toGeometryType(
                                    GeometryCollection.class);
                                default -> GeometryType.Unknown;
                              })
                  .orElse((byte) GeometryType.Unknown);
          headerMeta.srid = this.srid;
          headerMeta.hasZ = this.is3d;
        }
      } else {
        ColumnMeta column = createColumn(schema.getName(), schema);
        if (Objects.nonNull(column)) columns.add(column);
      }
    }

    headerMeta.columns = columns;
    headerMeta.geometryType = geometryType;

    return headerMeta;
  }

  private ColumnMeta createColumn(String name, FeatureSchema schema) {
    ColumnMeta column = new ColumnMeta();
    column.name = name;

    if (!allProperties && !properties.contains(schema.getFullPathAsString())) return null;

    switch (schema.getType()) {
      case BOOLEAN:
        column.type = ColumnType.Bool;
        break;
      case INTEGER:
        column.type = ColumnType.Int;
        break;
      case FLOAT:
        column.type = ColumnType.Double;
        break;
      case DATE:
      case DATETIME:
        column.type = ColumnType.DateTime;
        break;
        // Flatgeobuf can handle only primitives; map objects and arrays to strings
      case OBJECT:
      case OBJECT_ARRAY:
      case VALUE_ARRAY:
      case FEATURE_REF_ARRAY:
      case STRING:
      case FEATURE_REF:
        column.type = ColumnType.String;
        break;
      default:
        LOGGER.warn(
            "Property '{}' with unsupported type '{}' mapped to String in FlatGeobuf output.",
            column.name,
            schema.getType().toString());
        column.type = ColumnType.String;
    }
    schema.getLabel().ifPresent(s -> column.title = s);
    schema.getDescription().ifPresent(s -> column.description = s);
    schema
        .getConstraints()
        .flatMap(SchemaConstraints::getRequired)
        .ifPresent(b -> column.nullable = !b);
    schema.getRole().ifPresent(r -> column.unique = r.equals(SchemaBase.Role.ID));
    return column;
  }

  private int addProperties(SortedMap<String, Object> properties) {

    int size = 1024; // 1kB
    boolean done = false;
    ByteBuffer propBuffer = ByteBuffer.allocate(size);
    propBuffer.order(ByteOrder.LITTLE_ENDIAN);
    while (!done) {
      try {
        for (short i = 0; i < headerMeta.columns.size(); i++) {
          ColumnMeta column = headerMeta.columns.get(i);
          byte type = column.type;

          Object value = properties.get(column.name);
          if (value == null) continue;

          switch (type) {
            case ColumnType.Bool:
              if (value instanceof Boolean) {
                propBuffer.putShort(i);
                propBuffer.put((byte) ((Boolean) value ? 1 : 0));
              } else {
                LOGGER.warn(
                    "Property '{}' with invalid value '{}' for type '{}' skipped in FlatGeobuf output.",
                    column.name,
                    value,
                    type);
              }
              break;

            case ColumnType.Int:
              if (value instanceof Long) {
                propBuffer.putShort(i);
                propBuffer.putInt(((Long) value).intValue());
              } else {
                LOGGER.warn(
                    "Property '{}' with invalid value '{}' for type '{}' skipped in FlatGeobuf output.",
                    column.name,
                    value,
                    type);
              }
              break;

            case ColumnType.Double:
              if (value instanceof Double) {
                propBuffer.putShort(i);
                propBuffer.putDouble((Double) value);
              } else {
                LOGGER.warn(
                    "Property '{}' with invalid value '{}' for type '{}' skipped in FlatGeobuf output.",
                    column.name,
                    value,
                    type);
              }
              break;

            case ColumnType.String:
            case ColumnType.DateTime:
              propBuffer.putShort(i);
              byte[] stringBytes = value.toString().getBytes(StandardCharsets.UTF_8);
              propBuffer.putInt(stringBytes.length);
              propBuffer.put(stringBytes);
              break;

            default:
              LOGGER.warn(
                  "Property '{}' with unknown type '{}' skipped in FlatGeobuf output.",
                  column.name,
                  type);
          }
        }
        done = true;
      } catch (BufferOverflowException ex) {
        // increase properties buffer until it is large enough
        size *= 2;
        propBuffer = ByteBuffer.allocate(size);
        propBuffer.order(ByteOrder.LITTLE_ENDIAN);
      }
    }

    int propertiesOffset = 0;
    if (propBuffer.position() > 0) {
      propBuffer.flip();
      propertiesOffset = Feature.createPropertiesVector(builder, propBuffer);
    }
    return propertiesOffset;
  }
}
