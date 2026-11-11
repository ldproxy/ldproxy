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
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/** The step {@code feature-aggregate:execute}, see {@link ProcessesFeaturesBuildingBlock}. */
@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class FeatureAggregateProcessor extends FeatureProcessor {

  static final String KIND = "feature-aggregate";

  private final FeatureSelection featureSelection;

  @Inject
  FeatureAggregateProcessor(
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

  @Override
  protected Map<String, Object> process(
      OgcApi api, ProcessesFeaturesConfiguration configuration, Map<String, Object> inputs)
      throws IOException {
    Aggregation aggregation = Aggregation.of(inputs);
    Selection selection =
        featureSelection.select(api, required(inputs, FeatureSelection.INPUT_SOURCE));

    Set<String> known = featureSelection.properties(selection);
    List<String> unknown =
        aggregation.properties().stream()
            .filter(property -> !Aggregation.isVirtual(property) && !known.contains(property))
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Unknown properties: %s. The properties are: %s.",
              String.join(", ", unknown), String.join(", ", known)));
    }

    Optional<Geometry> clip = Optional.empty();
    if (Objects.nonNull(inputs.get(GeometryInputs.INPUT_CLIP))) {
      clip =
          Optional.of(
              GeometryInputs.clip(
                  inputs.get(GeometryInputs.INPUT_CLIP), featureSelection.toNative(api)));
    } else if (aggregation.needsGeometry()) {
      // areas and lengths need a projected coordinate reference system
      featureSelection.toNative(api);
    }
    Optional<Geometry> clipGeometry = clip;
    Optional<PreparedGeometry> clipArea = clip.map(PreparedGeometryFactory::prepare);

    Aggregator aggregator = new Aggregator(aggregation, selection.isMultiCollection());
    int maximum = maximumFeatures(configuration);
    long read =
        featureSelection.read(
            selection,
            aggregation.properties(),
            aggregation.needsGeometry() || clip.isPresent(),
            clip,
            maximum,
            record -> add(aggregator, aggregation, record, clipGeometry, clipArea));

    if (read > maximum) {
      throw tooManyFeatures(maximum);
    }

    return aggregator.result();
  }

  // a feature outside of the clip area is left out, the virtual properties are computed only when
  // an aggregate or a group key uses them
  private static void add(
      Aggregator aggregator,
      Aggregation aggregation,
      FeatureRecord record,
      Optional<Geometry> clip,
      Optional<PreparedGeometry> clipArea) {
    Optional<Geometry> geometry = record.geometry();
    if (clipArea.isPresent()
        && (geometry.isEmpty() || !clipArea.get().intersects(geometry.get()))) {
      return;
    }
    Map<String, Double> virtualValues =
        aggregation.needsGeometry() && geometry.isPresent()
            ? GeometryInputs.virtualValues(geometry.get(), clip)
            : Map.of();
    aggregator.add(record, virtualValues);
  }
}
