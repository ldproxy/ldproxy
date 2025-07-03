/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.FeatureTransformationContextGeoJson.GeometryState;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterGeometryBase;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.json.domain.GeoJsonGeometryType;
import java.util.Set;
import java.util.stream.Collectors;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class GeoJsonWriterGeometry extends GeoJsonWriterGeometryBase {

  @Inject
  public GeoJsonWriterGeometry() {}

  /* FIXME
  public GeoJsonWriterGeometry(CrsTransformerFactory crsTransformerFactory) {
    this.crsTransformerFactory = crsTransformerFactory;
  }

  private boolean suppressPrimaryGeometry;
  private boolean geometryOpen;
  private boolean hasPrimaryGeometry;
  private boolean inPrimaryGeometry;
  private Deque<Boolean> hasEmbeddedPrimaryGeometry;
  private Deque<Boolean> isBufferingEmbeddedFeature;
  private boolean skippingUnsupportedGeometry;
  private Set<SimpleFeatureGeometry> unsupportedGeometries;
  private final List<String> pos = new ArrayList<>();
  // TODO: move coordinate conversion to WGS 84 to the transformation pipeline,
  //       see https://github.com/interactive-instruments/ldproxy/issues/521
  private CrsTransformer crsTransformerGeometry;
  */

  @Override
  public GeoJsonWriterGeometry create() {
    return new GeoJsonWriterGeometry();
  }

  @Override
  public int getSortPriority() {
    return 32;
  }

  @Override
  protected String geomPropertyName() {
    return "geometry";
  }

  @Override
  protected boolean isEnabled(EncodingAwareContextGeoJson context) {
    return true;
  }

  @Override
  protected boolean writeNull() {
    return true;
  }

  @Override
  protected GeometryState geometryState() {
    return GeometryState.IN_GEOMETRY;
  }

  /* FIXME
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    unsupportedGeometries = new HashSet<>();
    suppressPrimaryGeometry = context.encoding().getSuppressPrimaryGeometry();
    crsTransformerGeometry = null;
    if (!suppressPrimaryGeometry && context.encoding().getForceDefaultCrs()) {
      EpsgCrs sourceCrs = context.encoding().getTargetCrs();
      EpsgCrs targetCrs = context.encoding().getDefaultCrs();
      if (!Objects.equals(sourceCrs, targetCrs)) {
        crsTransformerGeometry =
            crsTransformerFactory.getTransformer(sourceCrs, targetCrs).orElse(null);
      }
    }

    next.accept(context);
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("FEATURE {}", context.schema().get().getFullPathAsString());
    }
    hasPrimaryGeometry = false;
    inPrimaryGeometry = false;
    skippingUnsupportedGeometry = false;
    hasEmbeddedPrimaryGeometry = new ArrayDeque<>();
    isBufferingEmbeddedFeature = new ArrayDeque<>();

    next.accept(context);
  }

  @Override
  public void onObjectStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("OBJECT {}", context.schema().get().getFullPathAsString());
    }
    if (context.schema().filter(SchemaBase::isSpatial).isPresent()
        && context.geometryType().isPresent()) {
      if (suppressPrimaryGeometry
          && ((!inEmbeddedFeature() && context.schema().get().isPrimaryGeometry())
              || (inEmbeddedFeature() && context.schema().get().isEmbeddedPrimaryGeometry()))) {
        inPrimaryGeometry = true;
      } else {
        if ((!inEmbeddedFeature() && context.schema().get().isPrimaryGeometry())
            || (inEmbeddedFeature() && context.schema().get().isEmbeddedPrimaryGeometry())) {
          if (inEmbeddedFeature()) {
            hasEmbeddedPrimaryGeometry.removeLast();
            hasEmbeddedPrimaryGeometry.addLast(true);
            isBufferingEmbeddedFeature.removeLast();
            isBufferingEmbeddedFeature.addLast(false);
            context.encoding().stopBufferingEmbeddedFeature();
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace("BUFFER STOP EMBEDDED {}", context.schema().get().getFullPathAsString());
            }
          } else {
            hasPrimaryGeometry = true;
            context.encoding().stopBuffering();
            if (LOGGER.isTraceEnabled()) {
              LOGGER.trace("BUFFER STOP {}", context.schema().get().getFullPathAsString());
            }
          }
          inPrimaryGeometry = true;

          context.encoding().getJson().writeFieldName("geometry");
        } else {
          context.encoding().getJson().writeFieldName(context.schema().get().getName());
        }

        SimpleFeatureGeometry sfGeometryType = context.geometryType().get();
        GeoJsonGeometryType geoJsonGeometryType =
            GeoJsonGeometryType.forSimpleFeatureType(sfGeometryType);
        if (!geoJsonGeometryType.isSupported()) {
          if (LOGGER.isWarnEnabled() && !this.unsupportedGeometries.contains(sfGeometryType)) {
            LOGGER.warn(
                "Ignoring one or more GeoJSON geometries since an unsupported geometry type was provided: '{}'. Writing a null geometry.",
                sfGeometryType);
            this.unsupportedGeometries.add(sfGeometryType);
          }
          context.encoding().getJson().writeNull();
          if (inEmbeddedFeature()) {
            context.encoding().flushBufferEmbeddedFeature();
          } else {
            context.encoding().flushBuffer();
          }
          this.skippingUnsupportedGeometry = true;
        } else {
          context.encoding().getJson().writeStartObject();
          context.encoding().getJson().writeStringField("type", geoJsonGeometryType.toString());
          context.encoding().getJson().writeFieldName("coordinates");

          this.geometryOpen = true;
        }
      }
    } else if (context
        .schema()
        .flatMap(FeatureSchema::getRole)
        .filter(r -> r == Role.EMBEDDED_FEATURE)
        .isPresent()) {
      this.hasEmbeddedPrimaryGeometry.addLast(false);
      this.isBufferingEmbeddedFeature.addLast(false);
    }

    next.accept(context);
  }

  private boolean inEmbeddedFeature() {
    return !hasEmbeddedPrimaryGeometry.isEmpty();
  }

  @Override
  public void onArrayStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("ARRAY {}", context.schema().get().getFullPathAsString());
    }

    if (geometryOpen) {
      context.encoding().getJson().writeStartArray();
    } else if (!skippingUnsupportedGeometry && (!suppressPrimaryGeometry || !inPrimaryGeometry)) {
      startBufferingIfNecessary(context);
    }

    next.accept(context);
  }
  */

  @Override
  protected Set<FeatureSchema> getProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext) {
    if (writeJsonFgExtensions
        && !(transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return writeSecondaryGeometry
          ? schema.getAllNestedProperties().stream()
              .filter(SchemaBase::isSecondaryGeometry)
              .filter(SchemaBase::isSimpleFeatureGeometry)
              .collect(Collectors.toSet())
          : Set.of();
    }

    return schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isPrimaryGeometry)
        .filter(SchemaBase::isSimpleFeatureGeometry)
        .collect(Collectors.toSet());
  }

  @Override
  protected Set<FeatureSchema> getEmbeddedFeatureProperty(
      FeatureSchema schema, FeatureTransformationContext transformationContext) {
    if (writeJsonFgExtensions
        && !(transformationContext.getTargetCrs().equals(OgcCrs.CRS84)
            || transformationContext.getTargetCrs().equals(OgcCrs.CRS84h))) {
      return schema.getAllNestedProperties().stream()
          .filter(SchemaBase::isEmbeddedSecondaryGeometry)
          .filter(SchemaBase::isSimpleFeatureGeometry)
          .collect(Collectors.toSet());
    }

    Set<FeatureSchema> set =
        schema.getAllNestedProperties().stream()
            .filter(SchemaBase::isEmbeddedPrimaryGeometry)
            .filter(SchemaBase::isSimpleFeatureGeometry)
            .collect(Collectors.toSet());
    if (set.isEmpty()) {
      set =
          schema.getAllNestedProperties().stream()
              .filter(SchemaBase::isEmbeddedSecondaryGeometry)
              .filter(SchemaBase::isSimpleFeatureGeometry)
              .collect(Collectors.toSet());
    }
    return set;
  }

    /* FIXME
    if (context.schema().filter(SchemaBase::isSpatial).isPresent() && geometryOpen) {

      boolean stopBuffering = context.schema().get().isPrimaryGeometry();
      boolean stopBufferingEmbeddedFeature =
          inEmbeddedFeature() && context.schema().get().isEmbeddedPrimaryGeometry();

      geometryOpen = false;
      inPrimaryGeometry = false;

      // close geometry object
      context.encoding().getJson().writeEndObject();

      if (stopBuffering) {
        context.encoding().flushBuffer();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("BUFFER FLUSH {}", context.schema().get().getFullPathAsString());
        }
      } else if (stopBufferingEmbeddedFeature) {
        context.encoding().flushBufferEmbeddedFeature();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("BUFFER FLUSH EMBEDDED {}", context.schema().get().getFullPathAsString());
        }
      }
    } else if (context.schema().filter(SchemaBase::isSpatial).isPresent()
        && skippingUnsupportedGeometry) {
      skippingUnsupportedGeometry = false;
    } else if (inEmbeddedFeature()
        && context
            .schema()
            .flatMap(FeatureSchema::getRole)
            .filter(r -> r == Role.EMBEDDED_FEATURE)
            .isPresent()) {

      isBufferingEmbeddedFeature.pollLast();

      // write null geometry if none was written for this feature
      if (Boolean.FALSE.equals(hasEmbeddedPrimaryGeometry.pollLast())) {
        context.encoding().stopBufferingEmbeddedFeature();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace(
              "BUFFER STOP EMBEDDED NO GEOMETRY {}", context.schema().get().getFullPathAsString());
        }

        // null geometry
        context.encoding().getJson().writeFieldName("geometry");
        context.encoding().getJson().writeNull();

        context.encoding().flushBufferEmbeddedFeature();
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("BUFFER FLUSH EMBEDDED {}", context.schema().get().getFullPathAsString());
        }
      }
    }
    return set;
  }
    */

  @Override
  protected String getGeometryType(EncodingAwareContextGeoJson context) {
    return GeoJsonGeometryType.forSimpleFeatureType(context.geometryType().get()).toString();
  }
  /* FIXME
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("VALUE {} {}", context.schema().get().getFullPathAsString(), context.value());
    }

    if (geometryOpen) {
      if (inPrimaryGeometry && context.encoding().getForceDefaultCrs()) {
        // we buffer the whole coordinate in case we force WGS84 as the CRS in "geometry"
        pos.add(context.value());
      } else {
        context.encoding().getJson().writeRawValue(context.value());
      }
    } else if (!skippingUnsupportedGeometry && (!suppressPrimaryGeometry || !inPrimaryGeometry)) {
      startBufferingIfNecessary(context);
    }

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("FEATURE END {}", context.schema().get().getFullPathAsString());
    }

    // write null geometry if none was written for this feature
    if (!hasPrimaryGeometry) {
      context.encoding().stopBuffering();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("BUFFER STOP {}", context.schema().get().getFullPathAsString());
      }

      // null geometry
      context.encoding().getJson().writeFieldName("geometry");
      context.encoding().getJson().writeNull();

      context.encoding().flushBuffer();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("BUFFER FLUSH {}", context.schema().get().getFullPathAsString());
      }
    }

    next.accept(context);
  }

  private void startBufferingIfNecessary(EncodingAwareContextGeoJson context) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("BUFFER CHECK {}", context.schema().get().getFullPathAsString());
    }

    if (!geometryOpen
        && ((!hasPrimaryGeometry
                && !inEmbeddedFeature()
                && !context.encoding().getState().isBuffering())
            || (inEmbeddedFeature()
                && Boolean.FALSE.equals(hasEmbeddedPrimaryGeometry.peekLast())
                && Boolean.FALSE.equals(isBufferingEmbeddedFeature.peekLast())))) {
      // buffer properties until primary geometry arrives
      try {
        if (inEmbeddedFeature()) {
          context.encoding().startBufferingEmbeddedFeature();
          isBufferingEmbeddedFeature.removeLast();
          isBufferingEmbeddedFeature.addLast(true);
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("BUFFER START EMBEDDED {}", context.schema().get().getFullPathAsString());
          }
        } else {
          context.encoding().startBuffering();
          if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("BUFFER START {}", context.schema().get().getFullPathAsString());
          }
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
   */
}
