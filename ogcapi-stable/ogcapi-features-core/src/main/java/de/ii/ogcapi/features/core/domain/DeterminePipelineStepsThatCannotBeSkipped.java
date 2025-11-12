/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureStream.PipelineSteps;
import de.ii.xtraplatform.features.domain.Query;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.features.domain.SchemaVisitorTopDown;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DeterminePipelineStepsThatCannotBeSkipped
    implements SchemaVisitorTopDown<FeatureSchema, Set<PipelineSteps>> {

  private final FeatureFormatExtension outputFormat;
  private final EpsgCrs nativeCrs;
  private final EpsgCrs targetCrs;
  private final Query query;
  private final FeatureTypeConfigurationOgcApi collectionData;
  private final List<Profile> profiles;
  private final boolean sendResponseAsStream;

  public DeterminePipelineStepsThatCannotBeSkipped(
      FeatureFormatExtension outputFormat,
      EpsgCrs nativeCrs,
      EpsgCrs targetCrs,
      Query query,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      boolean sendResponseAsStream) {
    this.outputFormat = outputFormat;
    this.nativeCrs = nativeCrs;
    this.targetCrs = targetCrs;
    this.query = query;
    this.collectionData = collectionData;
    this.profiles = profiles;
    this.sendResponseAsStream = sendResponseAsStream;
  }

  @Override
  public Set<PipelineSteps> visit(
      FeatureSchema schema,
      List<FeatureSchema> parents,
      List<Set<PipelineSteps>> visitedProperties) {
    ImmutableSet.Builder<PipelineSteps> steps = ImmutableSet.builder();

    if (parents.isEmpty()) {
      // at the root level: aggregate information from properties and test global settings

      // determine property transformations
      Optional<PropertyTransformations> t =
          outputFormat.getPropertyTransformations(collectionData, Optional.of(schema), profiles);

      // coordinate processing is needed if a target CRS differs from the native CRS or geometries
      // are simplified
      if (!targetCrs.equals(nativeCrs)
          || (query.getMaxAllowableOffset() > 0)
          || (!(OgcCrs.CRS84.equals(nativeCrs) || OgcCrs.CRS84h.equals(nativeCrs))
              && outputFormat.supportsSecondaryGeometry()
              && schema.isSecondaryGeometry())) {
        steps.add(PipelineSteps.COORDINATES);
      }

      // metadata processing (extents, etag) is needed only if the response is not sent as a stream
      if (!sendResponseAsStream) {
        steps.add(PipelineSteps.METADATA, PipelineSteps.ETAG);
      }

      // aggregate information from visited properties
      visitedProperties.forEach(steps::addAll);

      // post-process special cases
      Set<PipelineSteps> intermediateResult = steps.build();

      // if null values are not removed, cleaning is not needed
      if (intermediateResult.contains(PipelineSteps.CLEAN)
          && t.get()
              .hasTransformation(
                  PropertyTransformations.WILDCARD, pt -> !pt.getRemoveNullValues().orElse(true))) {
        steps = ImmutableSet.builder();
        intermediateResult.stream().filter(s -> s != PipelineSteps.CLEAN).forEach(steps::add);
      }

      // mapping is also needed, if there property transformations are applied (the ones with a
      // wildcard are handled otherwise: nulls are removed in the CLEAN step and flattening is
      // already handled by including MAPPING for any objects or arrays)
      if (!intermediateResult.contains(PipelineSteps.MAPPING)) {
        if (t.map(PropertyTransformations::getTransformations)
                .filter(
                    map ->
                        map.entrySet().stream()
                            .anyMatch(
                                entry -> !PropertyTransformations.WILDCARD.equals(entry.getKey())))
                .isPresent()
            || outputFormat.requiresPropertiesInSequence(schema)) {
          steps.add(PipelineSteps.MAPPING);
        }
      }

    } else {
      // at property level: determine needed steps based on schema information

      // mapping is needed for any complex schema: concat/coalesce/merge, an array/object, or use of
      // a sub-decoder
      if (!schema.getConcat().isEmpty()
          || !schema.getCoalesce().isEmpty()
          || !schema.getMerge().isEmpty()
          || schema.isArray()
          || schema.isObject()
          || schema
              .getSourcePath()
              .filter(sourcePath -> sourcePath.matches(".+?\\[[^=\\]]+].+"))
              .isPresent()) {
        steps.add(PipelineSteps.MAPPING);
      }

      // geometry processing is needed for geometries with constraints that require special handling
      // to upgrade the geometry type
      if (schema.getType() == Type.GEOMETRY
          && schema
              .getConstraints()
              .filter(constraints -> constraints.isClosed() || constraints.isComposite())
              .isPresent()) {
        steps.add(PipelineSteps.GEOMETRY);
      }

      // unless all properties are required, cleaning maybe needed to remove null values
      if (schema.getConstraints().filter(SchemaConstraints::isRequired).isEmpty()) {
        steps.add(PipelineSteps.CLEAN);
      }
    }

    return steps.build();
  }
}
