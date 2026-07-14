/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.ogcapi.features.gml.domain.PositionVariants;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml.Options;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.transform.CoordinatesTransformer;
import de.ii.xtraplatform.geometries.domain.transform.ImmutableCrsTransform;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes the CRS-specific position variants of a geometry property (see {@code
 * GmlConfiguration#getPositionVariants()}) as a single position element and suppresses the variant
 * and helper properties as elements of their own. Runs before {@link GmlWriterGeometry} and {@link
 * GmlWriterProperties} in the writer chain.
 *
 * <p>Exactly one of the group's properties carries the position: the base geometry property (a
 * position in the native CRS — for a foreign-CRS position it holds the derived native copy and is
 * replaced by the variant), one of the variant geometry properties (2D/3D position in a foreign
 * CRS, written with the stored verbatim srsName), or the vertical property (a 1D position, written
 * as a {@code gml:Point} with {@code srsDimension="1"}). Emission anchors on the base geometry
 * token when present (native and foreign 2D/3D rows), or on the vertical property's token (1D rows,
 * where the base geometry column is NULL); the provider schema must therefore declare the srsName
 * and variant properties <em>before</em> the base geometry property, and all group members adjacent
 * to it so the position element keeps its place in the element sequence.
 */
@Singleton
@AutoBind
public class GmlWriterPositionVariants implements GmlWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(GmlWriterPositionVariants.class);

  private final CrsTransformerFactory crsTransformerFactory;

  private String stashedSrsName;
  private Geometry<?> stashedVariant;
  private EpsgCrs stashedVariantCrs;
  private boolean positionWritten;

  @Inject
  public GmlWriterPositionVariants(CrsTransformerFactory crsTransformerFactory) {
    this.crsTransformerFactory = crsTransformerFactory;
  }

  @Override
  public GmlWriterPositionVariants create() {
    return new GmlWriterPositionVariants(crsTransformerFactory);
  }

  @Override
  public int getSortPriority() {
    return 25;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next) throws IOException {
    this.stashedSrsName = null;
    this.stashedVariant = null;
    this.stashedVariantCrs = null;
    this.positionWritten = false;

    next.accept(context);
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    if (stashedVariant != null && !positionWritten && LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "A position variant was not written: the base geometry property did not follow the"
              + " variant properties for the current feature. Check the property order in the"
              + " provider schema.");
    }

    next.accept(context);
  }

  @Override
  public void onValue(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    Map<String, PositionVariants> positionVariants = context.encoding().getPositionVariants();
    if (!positionVariants.isEmpty() && context.schema().isPresent()) {
      String path = context.schema().get().getFullPathAsString();
      for (Map.Entry<String, PositionVariants> entry : positionVariants.entrySet()) {
        String parentPrefix = parentPrefix(entry.getKey());
        PositionVariants variants = entry.getValue();
        if (matches(variants.getSrsNameProperty(), parentPrefix, path)) {
          this.stashedSrsName = context.value();
          return;
        }
        if (matches(variants.getVerticalProperty(), parentPrefix, path)) {
          // 1D row — the base geometry column is NULL, so this token anchors the position element
          writeVerticalPosition(context, elementName(entry.getKey()), context.value());
          return;
        }
      }
    }

    next.accept(context);
  }

  @Override
  public void onGeometry(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    Map<String, PositionVariants> positionVariants = context.encoding().getPositionVariants();
    if (!positionVariants.isEmpty() && context.schema().isPresent()) {
      String path = context.schema().get().getFullPathAsString();
      for (Map.Entry<String, PositionVariants> entry : positionVariants.entrySet()) {
        String parentPrefix = parentPrefix(entry.getKey());
        PositionVariants variants = entry.getValue();
        boolean isVariant =
            variants.getVariantProperties().values().stream()
                .anyMatch(name -> matches(Optional.of(name), parentPrefix, path));
        if (isVariant) {
          // foreign 2D/3D row — the original position replaces the derived native copy held by
          // the base property; anchor here when the srsName is already known
          this.stashedVariant = context.geometry();
          this.stashedVariantCrs = context.schema().flatMap(SchemaBase::getCrs).orElse(null);
          if (stashedSrsName != null) {
            writeVariantPosition(context, elementName(entry.getKey()));
          }
          return;
        }
        if (path.equals(entry.getKey())) {
          if (positionWritten) {
            // the original position was already written from a variant or vertical property —
            // suppress the derived native copy
            return;
          }
          if (stashedVariant != null && stashedSrsName != null) {
            writeVariantPosition(context, elementName(entry.getKey()));
            return;
          }
          // native row — the base property carries the original position
          break;
        }
      }
    }

    next.accept(context);
  }

  private void writeVerticalPosition(
      EncodingAwareContextGml context, String elementName, String value) throws IOException {
    String gmlPrefix = context.encoding().getGmlPrefix();
    context.encoding().writeStartElement(elementName);
    context.encoding().writeStartElement(gmlPrefix + ":Point");
    if (stashedSrsName != null) {
      context.encoding().writeAttribute("srsName", stashedSrsName);
    }
    context.encoding().writeAttribute("srsDimension", "1");
    context.encoding().writeStartElement(gmlPrefix + ":pos");
    context.encoding().writeCharacters(value);
    context.encoding().writeEndElement();
    context.encoding().writeEndElement();
    context.encoding().writeEndElement();
    this.positionWritten = true;
  }

  private void writeVariantPosition(EncodingAwareContextGml context, String elementName)
      throws IOException {
    String srsName = stashedSrsName;
    Geometry<?> geometry = stashedVariant;
    // the wire form of this srsName may use a different false easting than the CRS the
    // coordinates are stored in (e.g. Gauss-Krüger without the zone prefix) — subtract the
    // configured difference to reproduce the wire form
    double falseEastingDifference =
        context.encoding().getSrsNameMappings().stream()
            .filter(m -> m.getValue().equals(srsName))
            .map(de.ii.ogcapi.features.gml.domain.SrsNameMapping::getFalseEastingDifference)
            .findFirst()
            .orElse(0.0);
    if (falseEastingDifference != 0) {
      geometry =
          geometry.accept(
              new CoordinatesTransformer(
                  de.ii.xtraplatform.geometries.domain.transform.ImmutableEastingShift.of(
                      Optional.empty(), -falseEastingDifference)));
    }
    // the column stores axis-forced coordinates (e.g. lon/lat for PostGIS); the wire form uses
    // the authority axis order, so the force is inverted before serializing
    if (stashedVariantCrs != null
        && stashedVariantCrs.getForceAxisOrder() == EpsgCrs.Force.LON_LAT) {
      Optional<de.ii.xtraplatform.crs.domain.CrsTransformer> transformer =
          crsTransformerFactory.getTransformer(
              stashedVariantCrs, EpsgCrs.of(stashedVariantCrs.getCode()));
      if (transformer.isPresent()) {
        geometry =
            geometry.accept(
                new CoordinatesTransformer(
                    ImmutableCrsTransform.of(Optional.empty(), transformer.get())));
      }
    }
    ImmutableSet.Builder<Options> options =
        ImmutableSet.<Options>builder().add(Options.WITH_SRS_NAME);
    if (context.encoding().getGmlIdOnGeometries()) {
      options.add(Options.WITH_GML_ID);
    }
    if (context.encoding().getUseSurfaceAndCurve()) {
      options.add(Options.USE_SURFACE_RING_CURVE);
    }
    GeometryEncoderGml encoder =
        new GeometryEncoderGml(
            context.encoding().getWriter(),
            context.encoding().getGmlVersion(),
            options.build(),
            Optional.of(context.encoding().getGmlPrefix()),
            context.encoding().getGmlIdPrefix(),
            // no coordinate rounding: the stored variant coordinates are reproduced verbatim,
            // and the request-CRS-based precision (e.g. metre-based) does not apply to them
            java.util.List.of(),
            crs -> srsName);

    context.encoding().writeStartElement(elementName);
    geometry.accept(encoder);
    context.encoding().writeEndElement();
    this.positionWritten = true;
    this.stashedVariant = null;
    this.stashedVariantCrs = null;
  }

  private static String parentPrefix(String basePath) {
    int lastDot = basePath.lastIndexOf('.');
    return lastDot < 0 ? "" : basePath.substring(0, lastDot + 1);
  }

  private static String elementName(String basePath) {
    int lastDot = basePath.lastIndexOf('.');
    return lastDot < 0 ? basePath : basePath.substring(lastDot + 1);
  }

  private static boolean matches(Optional<String> propertyName, String parentPrefix, String path) {
    return propertyName.isPresent() && path.equals(parentPrefix + propertyName.get());
  }
}
