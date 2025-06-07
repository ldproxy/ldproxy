/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.fasterxml.jackson.core.JsonGenerator;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.jsonfg.domain.JsonFgGeometryType;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class JsonFgWriterGeometryDimension implements GeoJsonWriter {

  public static String JSON_KEY = "geometryDimension";

  @Inject
  JsonFgWriterGeometryDimension() {}

  @Override
  public JsonFgWriterGeometryDimension create() {
    return new JsonFgWriterGeometryDimension();
  }

  @Override
  public int getSortPriority() {
    return 160;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (context.encoding().isFeatureCollection()) {
      Map<String, Optional<Integer>> collectionMap = getCollectionMap(context.encoding());
      List<Optional<Integer>> dims =
          collectionMap.values().stream().distinct().collect(Collectors.toUnmodifiableList());
      if (dims.size() == 1) {
        writeGeometryDimension(context.encoding(), dims.get(0));
      }
    }

    // next chain for extensions
    next.accept(context);
  }

  private Map<String, Optional<Integer>> getCollectionMap(
      FeatureTransformationContextGeoJson transformationContext) {
    ImmutableMap.Builder<String, Optional<Integer>> builder = ImmutableMap.builder();
    transformationContext
        .getFeatureSchemas()
        .forEach(
            (collectionId, schema) ->
                builder.put(
                    collectionId,
                    writeJsonFgExtensions(transformationContext)
                        ? getGeometryDimension(schema)
                        : Optional.empty()));
    return builder.build();
  }

  private void writeGeometryDimension(
      FeatureTransformationContextGeoJson transformationContext,
      Optional<Integer> geometryDimension)
      throws IOException {
    if (geometryDimension.isPresent()) {
      JsonGenerator json = transformationContext.getJson();
      json.writeNumberField(JSON_KEY, geometryDimension.get());
    }
  }

  public static Optional<Integer> getGeometryDimension(Optional<FeatureSchema> schema) {
    return schema.flatMap(
        s ->
            s.getProperties().stream()
                .filter(p -> p.isPrimaryGeometry() || p.isSecondaryGeometry())
                .map(
                    p ->
                        JsonFgGeometryType.getGeometryDimension(
                            p.getGeometryType().orElse(SimpleFeatureGeometry.NONE),
                            p.getConstraints().map(SchemaConstraints::isComposite).orElse(false),
                            p.getConstraints().map(SchemaConstraints::isClosed).orElse(false)))
                .flatMap(Optional::stream)
                .max(Comparator.naturalOrder()));
  }
}
