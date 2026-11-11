/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.features.domain.transform.FeatureEncoderSfFlat;
import de.ii.xtraplatform.features.domain.transform.FeatureSfFlat;
import de.ii.xtraplatform.features.domain.transform.PropertySfFlat;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBWriter;

/** Writes the lines of {@link FeatureRecordsFormat}, see {@link FeatureRecord}. */
class FeatureEncoderRecords extends FeatureEncoderSfFlat {

  private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);

  private final FeatureTransformationContext transformationContext;
  private final ObjectMapper mapper;
  private final GeometryFactory geometryFactory;
  private final WKBWriter wkbWriter;

  FeatureEncoderRecords(FeatureTransformationContext transformationContext) {
    super(transformationContext);
    this.transformationContext = transformationContext;
    this.mapper = new ObjectMapper();
    this.geometryFactory = new GeometryFactory();
    this.wkbWriter = new WKBWriter(2);
  }

  @Override
  public void onStart(ModifiableContext context) {
    if (transformationContext.isHitsOnly()) {
      Map<String, Object> hits = new LinkedHashMap<>();
      hits.put(FeatureRecord.NUMBER_MATCHED, context.metadata().getNumberMatched().orElse(-1));
      write(hits);
    }
  }

  @Override
  public void onFeature(FeatureSfFlat feature) {
    Map<String, List<Object>> values = new LinkedHashMap<>();
    feature.getProperties().forEach(property -> collect(property, values));

    Map<String, Object> record = new LinkedHashMap<>();
    record.put(
        FeatureRecord.COLLECTION,
        transformationContext.getCollectionIdForType(
            feature.getSchema().map(FeatureSchema::getName).orElse(null)));
    record.put(FeatureRecord.ID, feature.getIdValue());
    record.put(FeatureRecord.PROPERTIES, values);
    feature
        .getJtsGeometry(geometryFactory)
        .ifPresent(
            geometry ->
                record.put(
                    FeatureRecord.GEOMETRY,
                    Base64.getEncoder().encodeToString(wkbWriter.write(geometry))));

    write(record);
  }

  private void write(Map<String, Object> line) {
    try {
      push(mapper.writeValueAsBytes(line));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not write a feature record.", e);
    }
    push(NEWLINE);
  }

  // the values are keyed by the path of the property, values in arrays are collected under the
  // path of the array; geometries are written separately
  private static void collect(PropertySfFlat property, Map<String, List<Object>> values) {
    if (property.getSchema().filter(SchemaBase::isSpatial).isPresent()) {
      return;
    }
    switch (property.getType()) {
      case VALUE -> {
        Object value = typed(property);
        if (Objects.nonNull(value)) {
          String path =
              property
                  .getSchema()
                  .map(FeatureSchema::getFullPathAsString)
                  .orElse(property.getName());
          values.computeIfAbsent(path, key -> new ArrayList<>()).add(value);
        }
      }
      case OBJECT, ARRAY ->
          property.getNestedProperties().forEach(nested -> collect(nested, values));
      default -> {
        // nothing to collect
      }
    }
  }

  private static Object typed(PropertySfFlat property) {
    String value = property.getValue();
    if (Objects.isNull(value)) {
      return null;
    }
    // the type of the values of an array is the value type
    Type type =
        property
            .getSchema()
            .map(schema -> schema.getValueType().orElse(schema.getType()))
            .orElse(Type.STRING);
    try {
      return switch (type) {
        case INTEGER -> Long.parseLong(value);
        case FLOAT -> Double.parseDouble(value);
        case BOOLEAN ->
            "t".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
        default -> value;
      };
    } catch (NumberFormatException e) {
      return value;
    }
  }
}
