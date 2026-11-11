/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.features.search.domain.SearchQueriesHandler;
import de.ii.ogcapi.features.search.domain.StoredQueryResolution;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.processes.features.app.FeatureSelection.Selection;
import de.ii.ogcapi.processes.features.domain.ProcessesFeaturesConfiguration;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import org.locationtech.jts.geom.Point;

/** The step {@code feature-nearest:execute}, see {@link ProcessesFeaturesBuildingBlock}. */
@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class FeatureNearestProcessor extends FeatureProcessor {

  static final String KIND = "feature-nearest";

  static final String INPUT_POINT = "point";
  static final String INPUT_DISTANCE = "distance";
  static final String INPUT_LIMIT = "limit";
  static final String OUTPUT_FEATURES = "features";
  static final String MEMBER_DISTANCE = "distance";

  static final int DEFAULT_LIMIT = 10;
  static final int MAXIMUM_LIMIT = 100;

  private final FeatureSelection featureSelection;

  @Inject
  FeatureNearestProcessor(
      ResourceStore resourceStore,
      EntityRegistry entityRegistry,
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesQuery featuresQuery,
      FeaturesCoreQueriesHandler featuresHandler,
      SearchQueriesHandler searchHandler,
      StoredQueryResolution storedQueryResolution,
      CrsTransformerFactory crsTransformerFactory) {
    super(KIND, resourceStore, entityRegistry);
    this.featureSelection =
        new FeatureSelection(
            appContext,
            extensionRegistry,
            providers,
            featuresQuery,
            featuresHandler,
            searchHandler,
            storedQueryResolution,
            crsTransformerFactory);
  }

  /** A feature within the distance. */
  record Nearest(String collectionId, String featureId, double distance) {}

  @Override
  protected Map<String, Object> process(
      OgcApi api, ProcessesFeaturesConfiguration configuration, Map<String, Object> inputs)
      throws IOException {
    Selection selection =
        featureSelection.select(api, required(inputs, FeatureSelection.INPUT_SOURCE));
    CrsTransformer toNative = featureSelection.toNative(api);
    Point point = GeometryInputs.point(required(inputs, INPUT_POINT), toNative, INPUT_POINT);
    double distance = GeometryInputs.distance(required(inputs, INPUT_DISTANCE), INPUT_DISTANCE);
    int limit = limit(inputs.get(INPUT_LIMIT));

    // the nearest features, the farthest of them at the head
    Queue<Nearest> nearest =
        new PriorityQueue<>(Comparator.comparingDouble(Nearest::distance).reversed());
    int maximum = maximumFeatures(configuration);
    long read =
        featureSelection.read(
            selection,
            List.of(),
            true,
            Optional.of(point.buffer(distance, 1)),
            maximum,
            record -> {
              if (record.geometry().isEmpty() || Objects.isNull(record.featureId())) {
                return;
              }
              double d = record.geometry().get().distance(point);
              if (d <= distance) {
                nearest.add(new Nearest(record.collectionId(), record.featureId(), d));
                if (nearest.size() > limit) {
                  nearest.poll();
                }
              }
            });

    if (read > maximum) {
      throw tooManyFeatures(maximum);
    }

    return Map.of(OUTPUT_FEATURES, features(api, sorted(nearest)));
  }

  static List<Nearest> sorted(Collection<Nearest> nearest) {
    List<Nearest> sorted = new ArrayList<>(nearest);
    sorted.sort(
        Comparator.comparingDouble(Nearest::distance)
            .thenComparing(Nearest::collectionId)
            .thenComparing(Nearest::featureId));
    return sorted;
  }

  private Map<String, Object> features(OgcApi api, List<Nearest> nearest) throws IOException {
    List<Map<String, Object>> features = new ArrayList<>();
    for (Nearest feature : nearest) {
      Map<String, Object> geoJson =
          new LinkedHashMap<>(
              featureSelection.feature(api, feature.collectionId(), feature.featureId()));
      geoJson.put(
          MEMBER_DISTANCE,
          BigDecimal.valueOf(feature.distance()).setScale(1, RoundingMode.HALF_UP).doubleValue());
      features.add(geoJson);
    }
    Map<String, Object> collection = new LinkedHashMap<>();
    collection.put("type", "FeatureCollection");
    collection.put("features", features);
    return collection;
  }

  private static int limit(Object input) {
    if (Objects.isNull(input)) {
      return DEFAULT_LIMIT;
    }
    if (input instanceof Number number
        && number.intValue() >= 1
        && number.intValue() <= MAXIMUM_LIMIT) {
      return number.intValue();
    }
    throw new IllegalArgumentException(
        String.format("The input 'limit' must be an integer from 1 to %d.", MAXIMUM_LIMIT));
  }
}
