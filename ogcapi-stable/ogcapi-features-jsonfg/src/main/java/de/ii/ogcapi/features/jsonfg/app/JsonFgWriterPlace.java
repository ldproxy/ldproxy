/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.jsonfg.app;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterGeometryBase;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.CrsVariants;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import de.ii.xtraplatform.geometries.domain.transcode.json.GeometryEncoderJson;
import de.ii.xtraplatform.geometries.domain.transcode.json.JsonFgGeometryType;
import de.ii.xtraplatform.geometries.domain.transform.CoordinatesTransformer;
import de.ii.xtraplatform.geometries.domain.transform.ImmutableEastingShift;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class JsonFgWriterPlace extends GeoJsonWriterGeometryBase {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonFgWriterPlace.class);

  public static String JSON_KEY = "place";

  /**
   * The id of the {@code crs-original} profile (defined in the {@code PROFILE_CRS} building block,
   * referenced here by its literal value — stable modules must not depend on the profile module).
   * With the profile active, the position of a geometry property with a {@code crsVariants}
   * declaration is written to {@code place} from the variant property as recorded: with the stored
   * verbatim CRS identifier in {@code coordRefSys} and the coordinates in the convention of that
   * identifier (the read pipeline restores the {@code originalCrs}; the writer applies the inverse
   * false-easting shift). The default {@code place} behaviour is suppressed for such a feature;
   * features without a stored variant (positions in the storage CRS, or 1D positions that have no
   * representation in {@code place}) keep the standard behaviour.
   */
  static final String PROFILE_CRS_ORIGINAL = "crs-original";

  private boolean crsOriginal;
  private Map<String, VariantsGroups> groupsByCollection;
  private Map<String, String> stashedIdentifiers;
  private boolean placeWrittenFromVariant;

  @Inject
  JsonFgWriterPlace() {}

  @Override
  public JsonFgWriterPlace create() {
    return new JsonFgWriterPlace();
  }

  @Override
  public int getSortPriority() {
    return 32;
  }

  @Override
  protected String geomPropertyName(Optional<FeatureSchema> schema) {
    return JSON_KEY;
  }

  @Override
  protected boolean isEnabled(EncodingAwareContextGeoJson context) {
    return writeJsonFgExtensions;
  }

  @Override
  protected GeometryState geometryState() {
    return GeometryState.IN_PLACE;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    boolean jsonFgExtensions = writeJsonFgExtensions(context.encoding());
    this.crsOriginal =
        jsonFgExtensions
            && context.encoding().getProfiles().stream()
                .anyMatch(profile -> PROFILE_CRS_ORIGINAL.equals(profile.getId()));
    this.groupsByCollection = crsOriginal ? deriveGroups(context.encoding()) : Map.of();

    super.onStart(context, next);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    this.stashedIdentifiers = new HashMap<>();
    this.placeWrittenFromVariant = false;

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (crsOriginal && context.schema().isPresent()) {
      VariantsGroups groups = groupsByCollection.get(context.type());
      if (groups != null) {
        String path = context.schema().get().getFullPathAsString();
        if (groups.crsPropertyPaths.contains(path)) {
          stashedIdentifiers.put(path, context.value());
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onGeometry(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (crsOriginal && context.schema().isPresent() && context.geometry() != null) {
      VariantsGroups groups = groupsByCollection.get(context.type());
      String path = context.schema().get().getFullPathAsString();
      String crsPropertyPath =
          groups == null ? null : groups.crsPropertyPathByVariantPath.get(path);
      if (crsPropertyPath != null) {
        // a stored original position — written to "place" as recorded; the token is consumed so
        // the internal variant property does not surface anywhere else
        writeOriginalPlace(context, stashedIdentifiers.get(crsPropertyPath));
        return;
      }
    }

    super.onGeometry(context, next);
  }

  @Override
  protected Set<FeatureSchema> getProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry) {
    if (!writeJsonFgExtensions) {
      return Set.of();
    }

    if (placeWrittenFromVariant) {
      // the original position was already written from a variant property — suppress the default
      // place behaviour for the primary geometry (the derived copy in the storage CRS)
      return Set.of();
    }

    if (geometry.getType().isSimpleFeature()
        && (geometry.getType() != GeometryType.POLYHEDRAL_SURFACE
            || !((PolyhedralSurface) geometry).isClosed())
        && (OgcCrs.CRS84.equals(transformationContext.getTargetCrs())
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isPrimaryGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema,
      FeatureTransformationContext transformationContext,
      Geometry<?> geometry) {
    if (!writeJsonFgExtensions) {
      return Set.of();
    }

    if (geometry.getType().isSimpleFeature()
        && (geometry.getType() != GeometryType.POLYHEDRAL_SURFACE
            || !((PolyhedralSurface) geometry).isClosed())
        && (OgcCrs.CRS84.equals(transformationContext.getTargetCrs())
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isEmbeddedPrimaryGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected String getGeometryType(EncodingAwareContextGeoJson context, Geometry<?> geometry) {
    JsonFgGeometryType jsonFgGeometryType = JsonFgGeometryType.forGeometry(geometry);
    if (!jsonFgGeometryType.isSupported()) {
      //noinspection DataFlowIssue
      if (LOGGER.isWarnEnabled()
          && !context
              .encoding()
              .getState()
              .getUnsupportedGeometries()
              .contains(geometry.getType())) {
        LOGGER.warn(
            "Ignoring one or more JSON-FG geometries in 'place' since an unsupported geometry type was provided: '{}'. Writing a null geometry.",
            geometry.getType());
        //noinspection DataFlowIssue
        context.encoding().getState().addUnsupportedGeometries(geometry.getType());
      }
      return null;
    }

    return jsonFgGeometryType.toString();
  }

  /**
   * Writes {@code place} from a variant geometry property: the stored verbatim CRS identifier as
   * {@code coordRefSys} inside the geometry object, the coordinates in the convention of that
   * identifier — the geometry arrives in the {@code originalCrs} (restored by the read pipeline);
   * only the inverse of the false-easting shift applied on input remains to be applied here. No
   * coordinate rounding: the stored coordinates are reproduced as recorded.
   */
  private void writeOriginalPlace(EncodingAwareContextGeoJson context, String identifier)
      throws IOException {
    Geometry<?> geometry = context.geometry();

    double falseEastingDifference =
        context.schema().flatMap(FeatureSchema::getFalseEastingDifference).orElse(0.0);
    if (falseEastingDifference != 0) {
      geometry =
          geometry.accept(
              new CoordinatesTransformer(
                  ImmutableEastingShift.of(Optional.empty(), -falseEastingDifference)));
    }

    context.encoding().pauseBuffering();
    JsonGenerator json = context.encoding().getJson();
    json.writeFieldName(JSON_KEY);
    json.writeStartObject();
    if (identifier != null) {
      json.writeStringField("coordRefSys", identifier);
    }
    geometry.accept(new GeometryEncoderJson(new GeometryMembersGenerator(json), false, List.of()));
    json.writeEndObject();
    json.flush();
    context.encoding().continueBuffering();
    this.placeWrittenFromVariant = true;
  }

  /**
   * Passes everything through except the outermost object start/end, so the "type" and
   * "coordinates" members of {@code GeometryEncoderJson} land in an object the caller has already
   * opened (to inject additional members such as {@code coordRefSys}). Nested objects (e.g. the
   * member geometries of a geometry collection) are unaffected.
   */
  private static final class GeometryMembersGenerator extends JsonGeneratorDelegate {
    private int depth = 0;

    GeometryMembersGenerator(JsonGenerator delegate) {
      super(delegate, false);
    }

    @Override
    public void writeStartObject() throws IOException {
      if (depth++ == 0) {
        return;
      }
      super.writeStartObject();
    }

    @Override
    public void writeEndObject() throws IOException {
      if (--depth == 0) {
        return;
      }
      super.writeEndObject();
    }
  }

  /**
   * The position-variant groups of one collection: for routing, the full path of each variant
   * geometry property mapped to the full path of the group's {@code crsProperty}, plus the set of
   * {@code crsProperty} paths for stashing the identifier values.
   */
  private static final class VariantsGroups {
    final Map<String, String> crsPropertyPathByVariantPath = new LinkedHashMap<>();
    final Set<String> crsPropertyPaths = new java.util.HashSet<>();
  }

  private static Map<String, VariantsGroups> deriveGroups(
      FeatureTransformationContext transformationContext) {
    Map<String, VariantsGroups> result = new LinkedHashMap<>();
    transformationContext
        .getFeatureSchemas()
        .forEach(
            (collectionId, schema) ->
                schema.ifPresent(
                    featureSchema -> {
                      VariantsGroups groups = new VariantsGroups();
                      collectGroups(featureSchema, "", groups);
                      if (!groups.crsPropertyPathByVariantPath.isEmpty()) {
                        result.put(collectionId, groups);
                      }
                    }));
    return result;
  }

  private static void collectGroups(FeatureSchema schema, String parentPath, VariantsGroups out) {
    for (FeatureSchema child : schema.getProperties()) {
      String path = parentPath.isEmpty() ? child.getName() : parentPath + "." + child.getName();
      Optional<CrsVariants> variants = child.getCrsVariants();
      if (variants.isPresent() && variants.get().getCrsProperty().isPresent()) {
        String crsPropertyPath =
            parentPath.isEmpty()
                ? variants.get().getCrsProperty().get()
                : parentPath + "." + variants.get().getCrsProperty().get();
        out.crsPropertyPaths.add(crsPropertyPath);
        for (String name : variants.get().getGeometryProperties()) {
          out.crsPropertyPathByVariantPath.put(
              parentPath.isEmpty() ? name : parentPath + "." + name, crsPropertyPath);
        }
      }
      collectGroups(child, path, out);
    }
  }
}
