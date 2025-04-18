/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.cql.domain.And;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Geometry;
import de.ii.xtraplatform.cql.domain.Geometry.Bbox;
import de.ii.xtraplatform.cql.domain.Geometry.Polygon;
import de.ii.xtraplatform.cql.domain.Not;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.SIntersects;
import de.ii.xtraplatform.cql.domain.SpatialLiteral;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.shape.fractal.MortonCode;

/** This class represents a resource in a 3D Tiles tileset with implicit tiling */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true)
public abstract class TileResourceDescriptor {

  public static final int MAX_FEATURES_PER_TILE = 20_000;

  public static TileResourceDescriptor subtreeOf(
      TileResourceDescriptor subtree, int level, int x, int y) {
    return new ImmutableTileResourceDescriptor.Builder()
        .from(subtree)
        .level(level)
        .x(x)
        .y(y)
        .build();
  }

  public static TileResourceDescriptor subtreeOf(
      OgcApi api, String collectionId, int level, int x, int y) {
    return new ImmutableTileResourceDescriptor.Builder()
        .type(TYPE.SUBTREE)
        .api(api)
        .collectionId(collectionId)
        .level(level)
        .x(x)
        .y(y)
        .build();
  }

  public static TileResourceDescriptor contentOf(
      OgcApi api, String collectionId, int level, int x, int y) {
    return new ImmutableTileResourceDescriptor.Builder()
        .type(TYPE.CONTENT)
        .api(api)
        .collectionId(collectionId)
        .level(level)
        .x(x)
        .y(y)
        .build();
  }

  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  public List<TileResourceDescriptor> getChildren(int relativeLevel) {
    final int xl = getX() * (int) Math.pow(2, relativeLevel);
    final int yl = getY() * (int) Math.pow(2, relativeLevel);
    final int targetLevel = getLevel() + relativeLevel;
    ImmutableList.Builder<TileResourceDescriptor> children = ImmutableList.builder();
    for (int idx = 0; idx < MortonCode.size(relativeLevel); idx++) {
      Coordinate coord = MortonCode.decode(idx);
      children.add(
          new ImmutableTileResourceDescriptor.Builder()
              .from(this)
              .level(targetLevel)
              .x(xl + (int) coord.x)
              .y(yl + (int) coord.y)
              .build());
    }
    return children.build();
  }

  @Value.Derived
  public String getBboxString() {
    BoundingBox bboxTile = computeBbox();

    return String.format(
        Locale.US,
        "%f,%f,%f,%f",
        bboxTile.getXmin(),
        bboxTile.getYmin(),
        bboxTile.getXmax(),
        bboxTile.getYmax());
  }

  @Value.Derived
  public int getIndex() {
    return MortonCode.encode(getX(), getY());
  }

  public FeatureQuery getQuery(FeaturesCoreProviders providers) {
    FeatureTypeConfigurationOgcApi collectionData =
        getApiData().getCollectionData(getCollectionId()).orElseThrow();
    return ImmutableFeatureQuery.builder()
        .type(
            collectionData
                .getExtension(FeaturesCoreConfiguration.class)
                .flatMap(FeaturesCoreConfiguration::getFeatureType)
                .orElse(getCollectionId()))
        .crs(OgcCrs.CRS84h)
        .limit(MAX_FEATURES_PER_TILE)
        .filter(
            providers
                .getFeatureSchema(getApiData(), collectionData)
                .flatMap(SchemaBase::getPrimaryGeometry)
                .map(
                    property ->
                        (computeExclusionPolygon()
                            .map(
                                p ->
                                    (Cql2Expression)
                                        And.of(
                                            SIntersects.of(
                                                Property.of(property.getFullPathAsString()),
                                                SpatialLiteral.of(Bbox.of(computeBbox()))),
                                            Not.of(
                                                SIntersects.of(
                                                    Property.of(property.getFullPathAsString()),
                                                    SpatialLiteral.of(p)))))
                            .orElse(
                                SIntersects.of(
                                    Property.of(property.getFullPathAsString()),
                                    SpatialLiteral.of(Bbox.of(computeBbox()))))))
                .orElse(BooleanValue2.of(false)))
        .build();
  }

  public enum TYPE {
    CONTENT,
    SUBTREE
  }

  /**
   * @return the level of the tile
   */
  public abstract int getLevel();

  /**
   * @return the column of the tile
   */
  public abstract int getX();

  /**
   * @return the row of the tile
   */
  public abstract int getY();

  /**
   * @return the id of the collection included in the tile
   */
  public abstract String getCollectionId();

  /**
   * @return the API that produces the tile
   */
  public abstract OgcApi getApi();

  /**
   * @return the API that produces the tile
   */
  @Value.Derived
  @Value.Auxiliary
  public OgcApiDataV2 getApiData() {
    return getApi().getData();
  }

  /**
   * @return the resource type
   */
  public abstract TYPE getType();

  @Value.Derived
  @Value.Auxiliary
  public Path getRelativePath() {
    String extension = getType() == TYPE.CONTENT ? "glb" : "subtree";
    return Paths.get(String.format("%d_%d_%d.%s", getLevel(), getX(), getY(), extension));
  }

  @Value.Derived
  @Value.Auxiliary
  public BoundingBox computeBbox() {
    BoundingBox bbox = getApi().getSpatialExtent(getCollectionId()).orElseThrow();
    double dx = bbox.getXmax() - bbox.getXmin();
    double dy = bbox.getYmax() - bbox.getYmin();
    double factor = Math.pow(2, getLevel());
    double xmin = bbox.getXmin() + dx / factor * getX();
    double xmax = xmin + dx / factor;
    double ymin = bbox.getYmin() + dy / factor * getY();
    double ymax = ymin + dy / factor;
    return BoundingBox.of(
        xmin,
        ymin,
        Objects.requireNonNull(bbox.getZmin()),
        xmax,
        ymax,
        Objects.requireNonNull(bbox.getZmax()),
        OgcCrs.CRS84h);
  }

  @Value.Derived
  @Value.Auxiliary
  public Optional<Polygon> computeExclusionPolygon() {
    BoundingBox bbox = getApi().getSpatialExtent(getCollectionId()).orElseThrow();
    double dx = bbox.getXmax() - bbox.getXmin();
    double dy = bbox.getYmax() - bbox.getYmin();
    double factor = Math.pow(2, getLevel());

    /* The exclusion polygon is the area west and north of the tile. If T is the tile,
      any building that intersects the tiles TW, TN, and TNW, the tiles to the west,
      north, and northwest of T, is excluded.
      Special cases are: In the northwest corner, there is no tile to the north or west,
      so no area is excluded. For the western or nothern boundaries, only the northern
      or western are excluded, respectively.

         x ---- x ---- x
         |      |      |
         | TNW  |  TN  |
         |      |      |
         x ---- x ---- x
         |      |      |
         |  TW  |  T   |
         |      |      |
         x ---- x ---- x
    */

    // handle the special cases first
    if (getX() == 0 && getY() == (factor - 1)) {
      return Optional.empty();
    } else if (getX() == 0) {
      double xmin = bbox.getXmin() + dx / factor * getX();
      double xmax = xmin + dx / factor;
      double ymin = bbox.getYmin() + dy / factor * (getY() + 1);
      double ymax = ymin + dy / factor;
      return Optional.of(
          Polygon.of(
              OgcCrs.CRS84,
              List.of(
                  Geometry.Coordinate.of(xmin, ymin),
                  Geometry.Coordinate.of(xmax, ymin),
                  Geometry.Coordinate.of(xmax, ymax),
                  Geometry.Coordinate.of(xmin, ymax),
                  Geometry.Coordinate.of(xmin, ymin))));
    } else if (getY() == (factor - 1)) {
      double xmin = bbox.getXmin() + dx / factor * (getX() - 1);
      double xmax = xmin + dx / factor;
      double ymin = bbox.getYmin() + dy / factor * getY();
      double ymax = ymin + dy / factor;
      return Optional.of(
          Polygon.of(
              OgcCrs.CRS84,
              List.of(
                  Geometry.Coordinate.of(xmin, ymin),
                  Geometry.Coordinate.of(xmax, ymin),
                  Geometry.Coordinate.of(xmax, ymax),
                  Geometry.Coordinate.of(xmin, ymax),
                  Geometry.Coordinate.of(xmin, ymin))));
    }

    double x0 = bbox.getXmin() + dx / factor * (getX() - 1);
    double y0 = bbox.getYmin() + dy / factor * getY();
    double x1 = x0 + dx / factor;
    double y1 = y0 + dy / factor;
    double x2 = x0 + 2 * dx / factor;
    double y2 = y0 + 2 * dy / factor;
    return Optional.of(
        Polygon.of(
            OgcCrs.CRS84,
            List.of(
                Geometry.Coordinate.of(x0, y0),
                Geometry.Coordinate.of(x1, y0),
                Geometry.Coordinate.of(x1, y1),
                Geometry.Coordinate.of(x2, y1),
                Geometry.Coordinate.of(x2, y2),
                Geometry.Coordinate.of(x0, y2),
                Geometry.Coordinate.of(x0, y0))));
  }

  @Override
  @Value.Derived
  @Value.Auxiliary
  public String toString() {
    return String.format(
        "%s %s %d/%d/%d", getCollectionId(), getType(), getLevel(), getX(), getY());
  }

  @Value.Derived
  @Value.Auxiliary
  public String toStringShort() {
    return String.format("%d/%d/%d", getLevel(), getX(), getY());
  }
}
