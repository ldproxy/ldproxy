/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.app;

import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.GeometryCollection;
import de.ii.xtraplatform.geometries.domain.transform.ClampToEllipsoid;
import de.ii.xtraplatform.geometries.domain.transform.MinMaxDeriver;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class CesiumDataFeatures {

  public final List<FeatureHtml> features;
  public final boolean clampToEllipsoid;
  public final String encodedUrl;
  private final Optional<BoundingBox> collectionExtent;
  private Double minLon = null;
  private Double maxLon = null;
  private Double minLat = null;
  private Double maxLat = null;
  private Double minHeight = null;
  private Double maxHeight = null;

  /**
   * @param features the features of the page
   * @param url the URL of the glTF representation of the features
   * @param collectionExtent the spatial extent of the collection in CRS84, used when the features
   *     have no coordinates (no features or no geometries)
   */
  public CesiumDataFeatures(
      List<FeatureHtml> features, String url, Optional<BoundingBox> collectionExtent) {
    this.features = features;
    this.clampToEllipsoid = true; // TODO make configurable
    this.encodedUrl = URLEncoder.encode(url, StandardCharsets.UTF_8);
    this.collectionExtent = collectionExtent;
  }

  public boolean fromFeatures() {
    return true;
  }

  public String getFeatureExtent() {
    determineBbox();
    return "{minLon:"
        + minLon
        + ",minLat:"
        + minLat
        + ",maxLon:"
        + maxLon
        + ",maxLat:"
        + maxLat
        + "}";
  }

  public String getEncodedRegion() {
    determineBbox();
    return URLEncoder.encode(
        Math.toRadians(minLon)
            + ","
            + Math.toRadians(minLat)
            + ","
            + Math.toRadians(maxLon)
            + ","
            + Math.toRadians(maxLat)
            + ","
            + minHeight
            + ","
            + maxHeight,
        StandardCharsets.UTF_8);
  }

  private void determineBbox() {
    if (Objects.isNull(minLon)) {
      List<Geometry<?>> geometries =
          getGeometries(
              features,
              ImmutableList.of(
                  "lod2Solid",
                  "lod1Solid",
                  "consistsOfBuildingPart.lod2Solid",
                  "consistsOfBuildingPart.lod1Solid"));
      GeometryCollection geom = GeometryCollection.of(geometries);
      if (clampToEllipsoid) {
        geom = (GeometryCollection) new ClampToEllipsoid().visit(geom);
      }
      double[][] minMax = geom.accept(new MinMaxDeriver());
      if (Objects.isNull(minMax)) {
        minMax = fallbackMinMax();
      }
      minLon = Math.max(minMax[0][0], -180.0);
      maxLon = Math.min(minMax[1][0], 180.0);
      minLat = Math.max(minMax[0][1], -90.0);
      maxLat = Math.min(minMax[1][1], 90.0);
      minHeight = minMax[0][2];
      maxHeight = minMax[1][2];
    }
  }

  private double[][] fallbackMinMax() {
    return collectionExtent
        .map(
            bbox ->
                new double[][] {
                  {bbox.getXmin(), bbox.getYmin(), Objects.requireNonNullElse(bbox.getZmin(), 0.0)},
                  {bbox.getXmax(), bbox.getYmax(), Objects.requireNonNullElse(bbox.getZmax(), 0.0)}
                })
        .orElse(new double[][] {{-180.0, -90.0, 0.0}, {180.0, 90.0, 0.0}});
  }

  private List<Geometry<?>> getGeometries(
      List<FeatureHtml> features, List<String> geometryProperties) {
    return features.stream()
        .map(
            feature -> {
              List<PropertyHtml> geomProperties = ImmutableList.of();
              for (String geometryProperty : geometryProperties) {
                geomProperties = feature.findPropertiesByPath(geometryProperty);
                if (!geomProperties.isEmpty()) break;
              }
              if (!geomProperties.isEmpty()) {
                return geomProperties.stream().map(PropertyHtml::getGeometry).toList();
              }
              return feature.getGeometry().map(List::of).orElse(List.of());
            })
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableList());
  }
}
