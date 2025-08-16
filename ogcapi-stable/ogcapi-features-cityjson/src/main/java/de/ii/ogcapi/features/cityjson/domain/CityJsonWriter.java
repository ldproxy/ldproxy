/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import com.google.common.collect.ImmutableList;
import de.ii.xtraplatform.geometries.domain.Axes;
import de.ii.xtraplatform.geometries.domain.Geometry;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@AutoMultiBind
public interface CityJsonWriter {
  String ADDRESS = "address";
  String ADMINISTRATIVE_AREA = "AdministrativeArea";
  String BOUNDARIES = "boundaries";
  String BUILDING = "Building";
  String BUILDING_PART = "BuildingPart";
  String CEILING_SURFACE = "CeilingSurface";
  String CHILDREN = "children";
  String CITY_JSON = "CityJSON";
  String CITY_JSON_FEATURE = "CityJSONFeature";
  String CITY_OBJECTS = "CityObjects";
  String CLOSURE_SURFACE = "ClosureSurface";
  String CONSISTS_OF_BUILDING_PART = "consistsOfBuildingPart";
  String COUNTRY_NAME = "CountryName";
  String DOOR = "Door";
  String FLOOR_SURFACE = "FloorSurface";
  String GEOGRAPHICAL_EXTENT = "geographicalExtent";
  String GROUND_SURFACE = "GroundSurface";
  String ID = "id";
  String INTERIOR_WALL_SURFACE = "InteriorWallSurface";
  String LOCALITY_NAME = "LocalityName";
  String LOCATION = "location";
  String LOD = "lod";
  String METADATA = "metadata";
  String MULTI_POINT = "MultiPoint";
  String OUTER_CEILING_SURFACE = "OuterCeilingSurface";
  String OUTER_FLOOR_SURFACE = "OuterFloorSurface";
  String PARENTS = "parents";
  String POSTAL_CODE = "PostalCode";
  String REFERENCE_SYSTEM = "referenceSystem";
  String ROOF_SURFACE = "RoofSurface";
  String SCALE = "scale";
  String SEMANTICS = "semantics";
  String SOLID = "Solid";
  String SURFACES = "surfaces";
  String SURFACE_TYPE = "surfaceType";
  String THOROUGHFARE_NAME = "ThoroughfareName";
  String THOROUGHFARE_NUMBER = "ThoroughfareNumber";
  String TITLE = "title";
  String TRANSFORM = "transform";
  String TRANSLATE = "translate";
  String TYPE = "type";
  String VALUES = "values";
  String VERSION = "version";
  String VERTICES = "vertices";
  String WALL_SURFACE = "WallSurface";
  String WINDOW = "Window";

  List<String> ADDRESS_ATTRIBUTES =
      ImmutableList.of(
          COUNTRY_NAME,
          LOCALITY_NAME,
          THOROUGHFARE_NAME,
          THOROUGHFARE_NUMBER,
          POSTAL_CODE,
          ADMINISTRATIVE_AREA);
  List<String> SURFACE_TYPES =
      ImmutableList.of(
          CEILING_SURFACE,
          CLOSURE_SURFACE,
          DOOR,
          FLOOR_SURFACE,
          GROUND_SURFACE,
          INTERIOR_WALL_SURFACE,
          OUTER_CEILING_SURFACE,
          OUTER_FLOOR_SURFACE,
          ROOF_SURFACE,
          WALL_SURFACE,
          WINDOW);

  CityJsonWriter create();

  int getSortPriority();

  default void onEvent(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    switch (context.getState().getEvent()) {
      case START:
        onStart(context, next);
        break;
      case END:
        onEnd(context, next);
        break;
      case FEATURE_START:
        onFeatureStart(context, next);
        break;
      case FEATURE_END:
        if (context.getState().inSection()
            == FeatureTransformationContextCityJson.StateCityJson.Section.OUTSIDE) {
          onFeatureEndEnd(context, next);
        } else {
          onFeatureEnd(context, next);
        }
        break;
      case PROPERTY:
        onValue(context, next);
        break;
      case GEOMETRY:
        onGeometry(context, next);
        break;
      case ARRAY_START:
        onArrayStart(context, next);
        break;
      case OBJECT_START:
        onObjectStart(context, next);
        break;
      case OBJECT_END:
        onObjectEnd(context, next);
        break;
      case ARRAY_END:
        onArrayEnd(context, next);
        break;
    }
  }

  default void onStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onFeatureStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onFeatureEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onFeatureEndEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onArrayStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onObjectStart(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onObjectEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onArrayEnd(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onValue(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void onGeometry(
      EncodingAwareContextCityJson context, Consumer<EncodingAwareContextCityJson> next)
      throws IOException {
    next.accept(context);
  }

  default void checkGeometry(Geometry<?> geometry) {
    if (geometry == null) {
      throw new IllegalArgumentException("Geometry must not be null.");
    } else if (geometry.isEmpty()) {
      throw new IllegalArgumentException("Geometry must not be empty.");
    } else if (geometry.getAxes() != Axes.XYZ && geometry.getAxes() != Axes.XYZM) {
      throw new IllegalArgumentException(
          "Geometry axes must include XYZ, but was: " + geometry.getAxes());
    }
  }

  default int addVertex(EncodingAwareContextCityJson context, double[] coordinates, int index) {
    Optional<Integer> optionalIndex = context.encoding().processOrdinate(coordinates[index]);
    if (optionalIndex.isPresent()) {
      throw new IllegalStateException("Vertex already complete after X coordinate.");
    }
    optionalIndex = context.encoding().processOrdinate(coordinates[index + 1]);
    if (optionalIndex.isPresent()) {
      throw new IllegalStateException("Vertex already complete after Y coordinate.");
    }
    optionalIndex = context.encoding().processOrdinate(coordinates[index + 2]);
    if (optionalIndex.isEmpty()) {
      throw new IllegalStateException("Vertex not complete after Z coordinate.");
    }
    return optionalIndex.get();
  }
}
