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
import de.ii.ogcapi.features.gml.domain.SrsNameMapping;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml.Options;
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
                context.encoding().getSrsNameMappings()));
  }

  private static Function<EpsgCrs, String> buildSrsNameMapper(
      GmlConfiguration.SrsNameStyle style, List<SrsNameMapping> mappings) {
    if (style != GmlConfiguration.SrsNameStyle.TEMPLATE || mappings.isEmpty()) {
      return EpsgCrs::toUriString;
    }
    Map<EpsgCrs, String> table = new HashMap<>();
    for (SrsNameMapping m : mappings) {
      table.put(m.getCrs(), m.getValue());
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

    String elementNameProperty = schema.getName();
    context.encoding().writeStartElement(elementNameProperty);
    context.geometry().accept(encoder);
    context.encoding().writeEndElement();
  }
}
