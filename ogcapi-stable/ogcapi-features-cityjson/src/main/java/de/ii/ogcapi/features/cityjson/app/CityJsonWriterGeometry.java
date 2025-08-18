/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.app;

import com.fasterxml.jackson.databind.util.TokenBuffer;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriter;
import de.ii.ogcapi.features.cityjson.domain.EncodingAwareContextCityJson;
import de.ii.ogcapi.features.cityjson.domain.FeatureTransformationContextCityJson;
import de.ii.ogcapi.features.cityjson.domain.FeatureTransformationContextCityJson.StateCityJson.Section;
import de.ii.ogcapi.features.cityjson.domain.Vertices;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import de.ii.xtraplatform.geometries.domain.LineString;
import de.ii.xtraplatform.geometries.domain.MultiPolygon;
import de.ii.xtraplatform.geometries.domain.Polygon;
import de.ii.xtraplatform.geometries.domain.PolyhedralSurface;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class CityJsonWriterGeometry implements CityJsonWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CityJsonWriterGeometry.class);

  private final CrsTransformerFactory crsTransformerFactory;

  private final List<String> currentLod2Solid = new ArrayList<>();
  private final List<String> currentSemanticSurfacePolygons = new ArrayList<>();
  private List<Integer> semanticSurfaceValues = new ArrayList<>();
  private final List<String> semanticSurfaceTypes = new ArrayList<>();
  private int currentSemanticSurfaceId = -1;

  @Inject
  public CityJsonWriterGeometry(CrsTransformerFactory crsTransformerFactory) {
    this.crsTransformerFactory = crsTransformerFactory;
  }

  @Override
  public CityJsonWriterGeometry create() {
    return new CityJsonWriterGeometry(crsTransformerFactory);
  }

  @Override
  public int getSortPriority() {
    return 30;
  }

  @Override
  public void onStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    assert context.encoding().getGeometryPrecision().size() >= 3;
    double xScale = 1.0 / Math.pow(10, context.encoding().getGeometryPrecision().get(0));
    double yScale = 1.0 / Math.pow(10, context.encoding().getGeometryPrecision().get(1));
    double zScale = 1.0 / Math.pow(10, context.encoding().getGeometryPrecision().get(2));
    ArrayList<Double> currentScale = new ArrayList<>(3);
    currentScale.add(xScale);
    currentScale.add(yScale);
    currentScale.add(zScale);
    context.getState().setCurrentScale(currentScale);

    EpsgCrs crs = context.encoding().getCrs();
    Optional<BoundingBox> bbox =
        crs.equals(OgcCrs.CRS84h) || crs.equals(OgcCrs.CRS84)
            ? context.encoding().getApi().getSpatialExtent(context.encoding().getCollectionId())
            : context
                .encoding()
                .getApi()
                .getSpatialExtent(context.encoding().getCollectionId(), crs);
    if (bbox.isEmpty() && LOGGER.isErrorEnabled()) {
      LOGGER.error(
          "CityJSON: the bounding box is empty and cannot be used to compute the translation vector. The bbox in WGS84: {}",
          context.encoding().getApi().getSpatialExtent(context.encoding().getCollectionId()));
    }
    ArrayList<Double> currentTranslate = new ArrayList<>(3);
    currentTranslate.add(bbox.map(b -> (b.getXmin() + b.getXmax()) / 2.0).orElse(0.0));
    currentTranslate.add(bbox.map(b -> (b.getYmin() + b.getYmax()) / 2.0).orElse(0.0));
    currentTranslate.add(
        bbox.map(
                b ->
                    (Objects.requireNonNullElse(b.getZmin(), 0.0)
                            + Objects.requireNonNullElse(b.getZmax(), 0.0))
                        / 2.0)
            .orElse(0.0));
    context.getState().setCurrentTranslate(currentTranslate);

    context.encoding().getJson().writeFieldName(CityJsonWriter.TRANSFORM);
    context.encoding().getJson().writeStartObject();
    context.encoding().getJson().writeFieldName(CityJsonWriter.SCALE);
    context
        .encoding()
        .getJson()
        .writeArray(currentScale.stream().mapToDouble(Double::doubleValue).toArray(), 0, 3);
    context.encoding().getJson().writeFieldName(CityJsonWriter.TRANSLATE);
    context
        .encoding()
        .getJson()
        .writeArray(currentTranslate.stream().mapToDouble(Double::doubleValue).toArray(), 0, 3);
    context.encoding().getJson().writeEndObject();

    if (!context.encoding().getTextSequences()) {
      context.getState().setCurrentVertices(new Vertices());
    }

    next.accept(context);
  }

  @Override
  public void onEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    if (!context.encoding().getTextSequences()) {
      writeVertices(context);
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    context.encoding().startGeometry();

    if (context.encoding().getTextSequences()) {
      context.getState().setCurrentVertices(new Vertices());
    }

    // next chain for extensions
    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    if (context.getState().inSection() == Section.WAITING_FOR_SURFACES) {
      // No surfaces found, close geometry object
      TokenBuffer buffer = context.getState().getGeometryBuffer().get();
      buffer.writeEndObject();
    }

    context.encoding().stopAndFlushGeometry();

    next.accept(context);
  }

  @Override
  public void onFeatureEndEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.encoding().getTextSequences()) {
      writeVertices(context);
    }

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent() && context.schema().get().isObject()) {
      if (context.getState().inBuildingPart()
          && CityJsonWriter.CONSISTS_OF_BUILDING_PART.equals(context.schema().get().getName())) {
        context.encoding().startGeometry();
      } else if (context.getState().inSection()
          == FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES) {
        currentSemanticSurfacePolygons.clear();
      }
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);

    if (context.schema().isPresent() && context.schema().get().isObject()) {
      if (context.getState().inBuildingPart()
          && CONSISTS_OF_BUILDING_PART.equals(context.schema().get().getName())) {
        context.encoding().stopAndFlushGeometry();
      } else if (context.getState().inSection()
          == FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES) {
        for (int i = 0; i < currentLod2Solid.size(); i++) {
          for (String currentSemanticSurfacePolygon : currentSemanticSurfacePolygons) {
            if (currentSemanticSurfacePolygon.equals(currentLod2Solid.get(i))) {
              semanticSurfaceValues.set(i, currentSemanticSurfaceId);
            }
          }
        }
        currentSemanticSurfaceId++;
      }
    }
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    if (context.schema().isPresent() && context.schema().get().isArray()) {
      FeatureSchema schema = context.schema().get();

      if (CityJsonWriter.SURFACES.equals(schema.getName())) {
        if (context.getState().inSection()
            == FeatureTransformationContextCityJson.StateCityJson.Section.WAITING_FOR_SURFACES) {
          context
              .encoding()
              .changeSection(
                  FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES);
          currentSemanticSurfaceId = 0;
          semanticSurfaceTypes.clear();
          semanticSurfaceValues = new ArrayList<>(currentLod2Solid.size());
          currentLod2Solid.forEach(s -> semanticSurfaceValues.add(null));
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("LoD 2 solid: {}", currentLod2Solid);
          }
        } else {
          context
              .encoding()
              .changeSection(
                  FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES_IGNORE);
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);

    if (context.schema().isPresent() && context.schema().get().isArray()) {

      if (context.getState().inSection()
              == FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES
          && context.getState().getGeometryBuffer().isPresent()) {
        TokenBuffer buffer = context.getState().getGeometryBuffer().get();
        buffer.writeFieldName(CityJsonWriter.SEMANTICS);
        buffer.writeStartObject();

        buffer.writeFieldName(CityJsonWriter.SURFACES);
        buffer.writeStartArray();
        for (String semanticSurfaceType : semanticSurfaceTypes) {
          buffer.writeStartObject();
          buffer.writeStringField(TYPE, semanticSurfaceType);
          buffer.writeEndObject();
        }
        buffer.writeEndArray();

        buffer.writeFieldName(CityJsonWriter.VALUES);
        buffer.writeStartArray();
        buffer.writeStartArray();
        for (Integer semanticSurfaceValue : semanticSurfaceValues) {
          if (Objects.nonNull(semanticSurfaceValue)) {
            buffer.writeNumber(semanticSurfaceValue);
          } else {
            buffer.writeNull();
          }
        }
        buffer.writeEndArray();
        buffer.writeEndArray();

        // close semantics object
        buffer.writeEndObject();

        // close geometry object
        buffer.writeEndObject();
        context
            .encoding()
            .changeSection(FeatureTransformationContextCityJson.StateCityJson.Section.IN_BUILDING);
      }
    }
  }

  @Override
  public void onGeometry(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    Geometry<?> geometry = context.geometry();
    checkGeometry(geometry);
    @SuppressWarnings("DataFlowIssue")
    int dimension = geometry.getAxes().size();
    StringBuilder builder = new StringBuilder();

    if (context.schema().isPresent()
        && context.getState().inSection()
            != FeatureTransformationContextCityJson.StateCityJson.Section.IN_ADDRESS) {
      FeatureSchema schema = context.schema().get();
      if (context.getState().inSection()
          == FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES) {

        if (geometry.getType() == GeometryType.POLYGON) {
          geometry = MultiPolygon.of(List.of((Polygon) geometry));
        }

        MultiPolygon surfaces = (MultiPolygon) geometry;
        for (Polygon patch : surfaces.getValue()) {
          builder.setLength(0);
          boolean innerRing = false;
          for (LineString ring : patch.getValue()) {
            if (innerRing) {
              builder.append('_');
            }
            double[] coordinates = ring.getValue().getCoordinates();
            for (int i = 0; i < ring.getNumPoints(); i++) {
              if (!builder.isEmpty()) {
                builder.append('_');
              }
              builder.append(addVertex(context, coordinates, i * dimension));
            }
            innerRing = true;
          }
          currentSemanticSurfacePolygons.add(builder.toString());
        }

      } else if (context.getState().inSection()
              == FeatureTransformationContextCityJson.StateCityJson.Section.IN_BUILDING
          && schema.getName().matches("^lod[12]Solid$")
          && geometry.getType() == GeometryType.POLYHEDRAL_SURFACE) {

        String lod = "lod2Solid".equals(schema.getName()) ? "2" : "1";
        currentLod2Solid.clear();

        TokenBuffer buffer = context.getState().getGeometryBuffer().get();
        buffer.writeStartObject();
        buffer.writeStringField(TYPE, CityJsonWriter.SOLID);
        buffer.writeStringField(CityJsonWriter.LOD, lod);
        buffer.writeFieldName(CityJsonWriter.BOUNDARIES);

        PolyhedralSurface solid = (PolyhedralSurface) geometry;
        context.getState().getGeometryBuffer().get().writeStartArray();
        context.getState().getGeometryBuffer().get().writeStartArray();
        for (Polygon patch : solid.getValue()) {
          context.getState().getGeometryBuffer().get().writeStartArray();
          builder.setLength(0);
          boolean innerRing = false;
          for (LineString ring : patch.getValue()) {
            if (innerRing) {
              builder.append('_');
            }
            context.getState().getGeometryBuffer().get().writeStartArray();
            double[] coordinates = ring.getValue().getCoordinates();
            for (int i = 0; i < ring.getNumPoints(); i++) {
              int index = addVertex(context, coordinates, i * dimension);
              context.getState().getGeometryBuffer().get().writeNumber(index);
              if (!builder.isEmpty()) {
                builder.append('_');
              }
              builder.append(index);
            }
            context.getState().getGeometryBuffer().get().writeEndArray();
            innerRing = true;
          }
          context.getState().getGeometryBuffer().get().writeEndArray();
          currentLod2Solid.add(builder.toString());
        }
        context.getState().getGeometryBuffer().get().writeEndArray();
        context.getState().getGeometryBuffer().get().writeEndArray();

        // keep geometry object open for semantic surface information
        context
            .encoding()
            .changeSection(
                FeatureTransformationContextCityJson.StateCityJson.Section.WAITING_FOR_SURFACES);
      }
    }
  }

  @Override
  public void onValue(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {

    if (context.getState().inSection()
        == FeatureTransformationContextCityJson.StateCityJson.Section.IN_SURFACES) {
      if (context.schema().isPresent()
          && CityJsonWriter.SURFACE_TYPE.equals(context.schema().get().getName())) {
        switch (Objects.requireNonNull(context.value())) {
          case CityJsonWriter.WALL_SURFACE:
          case "wall":
            semanticSurfaceTypes.add(CityJsonWriter.WALL_SURFACE);
            break;
          case CityJsonWriter.ROOF_SURFACE:
          case "roof":
            semanticSurfaceTypes.add(CityJsonWriter.ROOF_SURFACE);
            break;
          case CityJsonWriter.GROUND_SURFACE:
          case "ground":
            semanticSurfaceTypes.add(CityJsonWriter.GROUND_SURFACE);
            break;
          case CityJsonWriter.CLOSURE_SURFACE:
          case "closure":
            semanticSurfaceTypes.add(CityJsonWriter.CLOSURE_SURFACE);
            break;
          case CityJsonWriter.OUTER_CEILING_SURFACE:
          case "outer_ceiling":
            semanticSurfaceTypes.add(CityJsonWriter.OUTER_CEILING_SURFACE);
            break;
          case CityJsonWriter.OUTER_FLOOR_SURFACE:
          case "outer_floor":
            semanticSurfaceTypes.add(CityJsonWriter.OUTER_FLOOR_SURFACE);
            break;
          case CityJsonWriter.WINDOW:
          case "window":
            semanticSurfaceTypes.add(CityJsonWriter.WINDOW);
            break;
          case CityJsonWriter.DOOR:
          case "door":
            semanticSurfaceTypes.add(CityJsonWriter.DOOR);
            break;
          case CityJsonWriter.INTERIOR_WALL_SURFACE:
          case "interior_wall":
            semanticSurfaceTypes.add(CityJsonWriter.INTERIOR_WALL_SURFACE);
            break;
          case CityJsonWriter.CEILING_SURFACE:
          case "ceiling":
            semanticSurfaceTypes.add(CityJsonWriter.CEILING_SURFACE);
            break;
          case CityJsonWriter.FLOOR_SURFACE:
          case "floor":
            semanticSurfaceTypes.add(CityJsonWriter.FLOOR_SURFACE);
            break;
          default:
            throw new IllegalStateException(
                String.format("Unknown semantic surface type: %s", context.value()));
        }
      }
    }

    next.accept(context);
  }

  private void writeVertices(EncodingAwareContextCityJson context) throws IOException {
    Vertices vertices = context.getState().getCurrentVertices().orElse(null);
    if (Objects.nonNull(vertices)) {
      vertices.lock();
      context.encoding().getJson().writeFieldName(CityJsonWriter.VERTICES);
      context.encoding().getJson().writeStartArray();
      int size = vertices.getSize();
      for (int i = 0; i < size; i++) {
        context.encoding().getJson().writeArray(vertices.getVertex(i), 0, 3);
      }
      context.encoding().getJson().writeEndArray();

      LOGGER.trace("Vertices: {}", size);
    } else {
      context.encoding().getJson().writeFieldName(CityJsonWriter.VERTICES);
      context.encoding().getJson().writeStartArray();
      context.encoding().getJson().writeEndArray();

      LOGGER.trace("No Vertices");
    }
  }
}
