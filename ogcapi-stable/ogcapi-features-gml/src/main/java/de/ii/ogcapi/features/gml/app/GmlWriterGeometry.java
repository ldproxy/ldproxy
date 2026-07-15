/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml.Options;
import de.ii.xtraplatform.geometries.domain.CompoundCurve;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.SingleCurve;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("ConstantConditions")
@Singleton
@AutoBind
public class GmlWriterGeometry implements GmlWriter {

  private GeometryEncoderGml encoder;

  @Inject
  public GmlWriterGeometry() {}

  @Override
  public GmlWriterGeometry create() {
    return new GmlWriterGeometry();
  }

  @Override
  public int getSortPriority() {
    return 30;
  }

  @Override
  public void onStart(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    // TODO add support for AdV CityGML profile LoD1 and LoD2
    ImmutableSet.Builder<GeometryEncoderGml.Options> options =
        ImmutableSet.<GeometryEncoderGml.Options>builder().add(Options.WITH_SRS_NAME);
    if (context.encoding().getGmlIdOnGeometries()) {
      options.add(Options.WITH_GML_ID);
    }
    if (context.encoding().getSrsDimension()) {
      options.add(Options.WITH_SRS_DIMENSION);
    }
    if (context.encoding().getUseSurfaceAndCurve()) {
      options.add(Options.USE_SURFACE_RING_CURVE);
    }
    encoder =
        new GeometryEncoderGml(
            context.encoding().getWriter(),
            context.encoding().getGmlVersion(),
            options.build(),
            Optional.of(context.encoding().getGmlPrefix()),
            context.encoding().getGmlIdPrefix(),
            context.encoding().getGeometryPrecision(),
            buildSrsNameMapper(
                context.encoding().getSrsNameStyle().orElse(GmlConfiguration.SrsNameStyle.OGC),
                context.encoding().getAlternativeCrss()));
  }

  private static Function<EpsgCrs, String> buildSrsNameMapper(
      GmlConfiguration.SrsNameStyle style, List<EpsgCrs> alternativeCrss) {
    if (style != GmlConfiguration.SrsNameStyle.TEMPLATE || alternativeCrss.isEmpty()) {
      return EpsgCrs::toUriString;
    }
    Map<EpsgCrs, String> table = new HashMap<>();
    for (EpsgCrs crs : alternativeCrss) {
      crs.getAlternativeUri().ifPresent(uri -> table.put(crs, uri));
    }
    return crs -> {
      String mapped = table.get(crs);
      return mapped != null ? mapped : crs.toUriString();
    };
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().getState().setDeferredSolidGeometry(false);
    context.encoding().getState().setDeferredPolygonId(0);

    next.accept(context);
  }

  @Override
  public void onGeometry(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    FeatureSchema schema = context.schema().orElseThrow();

    // internal geometry properties are never encoded as regular elements; the position-variant
    // group is intercepted earlier by GmlWriterPositionVariants, this covers internal geometries
    // outside a variants group
    if (schema.isInternal()) {
      return;
    }

    String elementNameProperty = schema.getName();
    context.encoding().writeStartElement(elementNameProperty);
    forceCompositeCurve(context.geometry(), context.encoding().getForceCompositeCurve())
        .accept(encoder);
    context.encoding().writeEndElement();
  }

  /**
   * Some GML application schemas require {@code gml:CompositeCurve} for a feature type's geometry
   * even when it has a single component. Box a lone curve into a {@link CompoundCurve} so the
   * encoder emits a {@code gml:CompositeCurve}; leave any other geometry untouched.
   */
  static Geometry<?> forceCompositeCurve(Geometry<?> geometry, boolean force) {
    if (force && geometry instanceof SingleCurve) {
      return CompoundCurve.of(List.of((SingleCurve) geometry), geometry.getCrs());
    }
    return geometry;
  }
}
