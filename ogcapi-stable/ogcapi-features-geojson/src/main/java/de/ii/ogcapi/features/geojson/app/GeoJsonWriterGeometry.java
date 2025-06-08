/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.domain.EncodingAwareContextGeoJson;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.geojson.domain.ProfileGeoJson;
import de.ii.xtraplatform.crs.domain.CoordinateTuple;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SchemaBase.Role;
import de.ii.xtraplatform.features.json.domain.GeoJsonGeometryType;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zahnen
 */
@Singleton
@AutoBind
public class GeoJsonWriterGeometry implements GeoJsonWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(GeoJsonWriterGeometry.class);

  private final CrsTransformerFactory crsTransformerFactory;

  @Inject
  public GeoJsonWriterGeometry(CrsTransformerFactory crsTransformerFactory) {
    this.crsTransformerFactory = crsTransformerFactory;
  }

  private boolean suppressPrimaryGeometry;
  private boolean forceDefaultCrs;
  private boolean geometryOpen;
  private boolean hasPrimaryGeometry;
  private boolean inPrimaryGeometry;
  private Deque<Boolean> hasEmbeddedPrimaryGeometry;
  private Deque<Boolean> isBufferingEmbeddedFeature;
  private final List<String> pos = new ArrayList<>();
  // TODO: move coordinate conversion to WGS 84 to the transformation pipeline,
  //       see https://github.com/interactive-instruments/ldproxy/issues/521
  private CrsTransformer crsTransformerGeometry;

  @Override
  public GeoJsonWriterGeometry create() {
    return new GeoJsonWriterGeometry(crsTransformerFactory);
  }

  @Override
  public int getSortPriority() {
    return 30;
  }

  @Override
  public void onStart(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    // FIXME #521
    suppressPrimaryGeometry =
        context.encoding().getProfiles().stream()
            .filter(profile -> profile instanceof ProfileGeoJson)
            .findFirst()
            .map(p -> ((ProfileGeoJson) p).suppressPrimaryGeometry())
            .orElse(false);
    forceDefaultCrs =
        context.encoding().getProfiles().stream()
            .filter(profile -> profile instanceof ProfileGeoJson)
            .findFirst()
            .map(p -> ((ProfileGeoJson) p).forceDefaultCrs())
            .orElse(false);
    crsTransformerGeometry = null;
    if (!suppressPrimaryGeometry && forceDefaultCrs) {
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

        context.encoding().getJson().writeStartObject();
        context
            .encoding()
            .getJson()
            .writeStringField(
                "type",
                GeoJsonGeometryType.forSimpleFeatureType(context.geometryType().get()).toString());
        context.encoding().getJson().writeFieldName("coordinates");

        this.geometryOpen = true;
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
    } else if (!suppressPrimaryGeometry || !inPrimaryGeometry) {
      startBufferingIfNecessary(context);
    }

    next.accept(context);
  }

  @Override
  public void onArrayEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("ARRAY END {}", context.schema().get().getFullPathAsString());
    }

    if (geometryOpen) {
      if (!pos.isEmpty()) {
        if (Objects.isNull(crsTransformerGeometry)) {
          // fallback
          for (String p : pos) context.encoding().getJson().writeRawValue(p);
        } else {
          CoordinateTuple coord =
              crsTransformerGeometry.transform(new CoordinateTuple(pos.get(0), pos.get(1)));
          context
              .encoding()
              .getJson()
              .writeRawValue(
                  BigDecimal.valueOf(coord.getX())
                      .setScale(7, RoundingMode.HALF_DOWN)
                      .toPlainString());
          context
              .encoding()
              .getJson()
              .writeRawValue(
                  BigDecimal.valueOf(coord.getY())
                      .setScale(7, RoundingMode.HALF_DOWN)
                      .toPlainString());
          if (pos.size() == 3) context.encoding().getJson().writeRawValue(pos.get(2));
        }
        pos.clear();
      }

      context.encoding().getJson().writeEndArray();
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("OBJECT END {}", context.schema().get().getFullPathAsString());
    }

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

    next.accept(context);
  }

  @Override
  public void onValue(
      EncodingAwareContextGeoJson context, Consumer<EncodingAwareContextGeoJson> next)
      throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("VALUE {} {}", context.schema().get().getFullPathAsString(), context.value());
    }

    if (geometryOpen) {
      if (inPrimaryGeometry && forceDefaultCrs) {
        // we buffer the whole coordinate in case we force WGS84 as the CRS in "geometry"
        pos.add(context.value());
      } else {
        context.encoding().getJson().writeRawValue(context.value());
      }
    } else if (!suppressPrimaryGeometry || !inPrimaryGeometry) {
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
}
