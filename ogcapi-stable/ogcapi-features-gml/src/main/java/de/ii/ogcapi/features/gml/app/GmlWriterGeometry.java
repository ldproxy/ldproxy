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
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml.Options;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

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
    encoder =
        new GeometryEncoderGml(
            context.encoding().getWriter(),
            context.encoding().getGmlVersion(),
            options.build(),
            Optional.of(context.encoding().getGmlPrefix()),
            context.encoding().getGmlIdPrefix(),
            context.encoding().getGeometryPrecision());
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
    context.encoding().write("<");
    context.encoding().write(elementNameProperty);
    context.encoding().write(">");

    context.geometry().accept(encoder);

    context.encoding().write("</");
    context.encoding().write(elementNameProperty);
    context.encoding().write(">");
  }
}
