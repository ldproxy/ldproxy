/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import static de.ii.xtraplatform.features.gml.domain.GmlVersion.GML21;
import static de.ii.xtraplatform.features.gml.domain.GmlVersion.GML31;
import static de.ii.xtraplatform.features.gml.domain.GmlVersion.GML32;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml.Options;
import de.ii.xtraplatform.features.gml.domain.GmlVersion;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@SuppressWarnings("ConstantConditions")
@Singleton
@AutoBind
public class GmlWriterGeometry implements GmlWriter {

  private static final String POINT = "gml:Point";
  private static final String MULTI_POINT = "gml:MultiPoint";
  private static final String LINE_STRING = "gml:LineString";
  private static final String MULTI_CURVE = "gml:MultiCurve";
  private static final String MULTI_LINE_STRING = "gml21:MultiLineString";
  private static final String POLYGON = "gml:Polygon";
  private static final String MULTI_SURFACE = "gml:MultiSurface";
  private static final String MULTI_POLYGON = "gml21:MultiPolygon";
  private static final String MULTI_GEOMETRY = "gml:MultiGeometry";
  private static final String SOLID = "gml:Solid";
  private static final String COMPOSITE_SURFACE = "gml:CompositeSurface";
  private static final String COMPOSITE_CURVE = "gml:CompositeCurve";
  private static final String POS = "gml:pos";
  private static final String POS_LIST = "gml:posList";
  private static final String POINT_MEMBER = "gml:pointMember";
  private static final String CURVE_MEMBER = "gml:curveMember";
  private static final String LINEAR_RING = "gml:LinearRing";
  private static final String EXTERIOR = "gml:exterior";
  private static final String INTERIOR = "gml:interior";
  private static final String SURFACE_MEMBER = "gml:surfaceMember";
  private static final Map<GmlVersion, Map<GeometryType, String>> GEOMETRY_ELEMENT =
      ImmutableMap.of(
          GML32,
          ImmutableMap.of(
              GeometryType.POINT, POINT,
              GeometryType.MULTI_POINT, MULTI_POINT,
              GeometryType.LINE_STRING, LINE_STRING,
              GeometryType.MULTI_LINE_STRING, MULTI_CURVE,
              GeometryType.POLYGON, POLYGON,
              GeometryType.MULTI_POLYGON, MULTI_SURFACE,
              GeometryType.GEOMETRY_COLLECTION, MULTI_GEOMETRY),
          GML31,
          ImmutableMap.of(
              GeometryType.POINT, "gml31" + POINT.substring(3),
              GeometryType.MULTI_POINT, "gml31" + MULTI_POINT.substring(3),
              GeometryType.LINE_STRING, "gml31" + LINE_STRING.substring(3),
              GeometryType.MULTI_LINE_STRING, "gml31" + MULTI_CURVE.substring(3),
              GeometryType.POLYGON, "gml31" + POLYGON.substring(3),
              GeometryType.MULTI_POLYGON, "gml31" + MULTI_SURFACE.substring(3),
              GeometryType.GEOMETRY_COLLECTION, "gml31" + MULTI_GEOMETRY.substring(3)),
          GML21,
          ImmutableMap.of(
              GeometryType.POINT, "gml21" + POINT.substring(3),
              GeometryType.MULTI_POINT, "gml21" + MULTI_POINT.substring(3),
              GeometryType.LINE_STRING, "gml21" + LINE_STRING.substring(3),
              GeometryType.MULTI_LINE_STRING, MULTI_LINE_STRING.substring(3),
              GeometryType.POLYGON, "gml21" + POLYGON.substring(3),
              GeometryType.MULTI_POLYGON, MULTI_POLYGON.substring(3),
              GeometryType.GEOMETRY_COLLECTION, "gml21" + MULTI_GEOMETRY.substring(3)));
  private static final String OUTER_BOUNDARY_IS = "gml21:outerBoundaryIs";
  private static final String INNER_BOUNDARY_IS = "gml21:innerBoundaryIs";
  private static final String LINE_STRING_MEMBER = "gml21:lineStringMember";
  private static final String COORDINATES = "gml21:coordinates";
  private static final String POLYGON_MEMBER = "gml21:polygonMember";

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
    encoder =
        new GeometryEncoderGml(
            context.encoding().getWriter(),
            Set.of(Options.WITH_SRS_NAME),
            Optional.of("gml"),
            Optional.empty(),
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
