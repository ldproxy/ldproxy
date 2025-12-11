/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.Query;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeatures;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.cql.domain.And;
import de.ii.xtraplatform.cql.domain.Bbox;
import de.ii.xtraplatform.cql.domain.BooleanValue2;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql.Format;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.Not;
import de.ii.xtraplatform.cql.domain.Property;
import de.ii.xtraplatform.cql.domain.SIntersects;
import de.ii.xtraplatform.cql.domain.SpatialLiteral;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.EntityRegistry;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.geometries.domain.Polygon;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles3d.domain.Tile3dBuilder;
import de.ii.xtraplatform.tiles3d.domain.Tile3dCoordinates;
import de.ii.xtraplatform.tiles3d.domain.Tileset3dFeatures;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class Tile3dBuilderGltf implements Tile3dBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(Tile3dBuilderGltf.class);
  private static final int MAX_FEATURES_PER_TILE = 20_000;
  private static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("model", "gltf-binary"))
          .label("glTF-Binary")
          .parameter("glb")
          .build();

  private final EntityRegistry entityRegistry;
  private final ExtensionRegistry extensionRegistry;
  private final ServicesContext servicesContext;
  private final FeaturesCoreQueriesHandler queryHandlerFeatures;
  private final Cql cql;

  @Inject
  public Tile3dBuilderGltf(
      EntityRegistry entityRegistry,
      ExtensionRegistry extensionRegistry,
      ServicesContext servicesContext,
      FeaturesCoreQueriesHandler queryHandlerFeatures,
      Cql cql) {
    this.entityRegistry = entityRegistry;
    this.extensionRegistry = extensionRegistry;
    this.servicesContext = servicesContext;
    this.queryHandlerFeatures = queryHandlerFeatures;
    this.cql = cql;
  }

  @Override
  public int getPriority() {
    return 1000;
  }

  @Override
  public byte[] generateTile(
      Tile3dCoordinates tile3dCoordinates,
      Tileset3dFeatures tileset,
      BoundingBox boundingBox,
      Optional<Polygon> exclusionPolygon,
      FeatureProvider featureProvider,
      String apiId,
      String collectionId) {
    OgcApi api = getOgcApi(apiId).orElseThrow();

    String bboxString =
        String.format(
            Locale.US,
            "%f,%f,%f,%f",
            boundingBox.getXmin(),
            boundingBox.getYmin(),
            boundingBox.getXmax(),
            boundingBox.getYmax());

    FeatureQuery featureQuery =
        getQuery(
            featureProvider,
            tileset.getFeatureType().orElse(tileset.getId()),
            boundingBox,
            exclusionPolygon);

    Response response =
        getContent(
            tileset,
            api,
            collectionId,
            featureProvider,
            queryHandlerFeatures,
            tile3dCoordinates.getLevel(),
            bboxString,
            featureQuery);

    return (byte[]) response.getEntity();
  }

  private Response getContent(
      Tileset3dFeatures tileset,
      OgcApi api,
      String collectionId,
      FeatureProvider provider,
      FeaturesCoreQueriesHandler queriesHandlerFeatures,
      int level,
      String bboxString,
      FeatureQuery featureQuery) {
    String contentFilterString = "true";
    FeatureQuery query =
        tileset.getContentFilters().isEmpty()
            ? featureQuery
            : getFinalQuery(
                tileset
                    .getContentFilters()
                    .get(level - Objects.requireNonNull(tileset.getContentLevels().getMin())),
                featureQuery);

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        getQueryInputFeatures(query, provider, collectionId);

    ApiRequestContext requestContextGltf =
        getFeaturesRequestContext(
            api, collectionId, tileset.shouldClampToEllipsoid(), bboxString, contentFilterString);

    return queriesHandlerFeatures.handle(Query.FEATURES, queryInput, requestContextGltf);
  }

  private FeaturesCoreQueriesHandler.QueryInputFeatures getQueryInputFeatures(
      FeatureQuery query, FeatureProvider provider, String collectionId) {
    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput;
    return new ImmutableQueryInputFeatures.Builder()
        .collectionId(collectionId)
        .query(query)
        .featureProvider(provider)
        .defaultCrs(OgcCrs.CRS84h)
        .defaultPageSize(Optional.of(MAX_FEATURES_PER_TILE))
        .sendResponseAsStream(false)
        .build();
  }

  private FeatureQuery getQuery(
      FeatureProvider provider,
      String featureType,
      BoundingBox bbox,
      Optional<Polygon> exclusionPolygon) {
    return ImmutableFeatureQuery.builder()
        .type(featureType)
        .crs(OgcCrs.CRS84h)
        .limit(MAX_FEATURES_PER_TILE)
        .filter(
            provider
                .info()
                .getSchema(featureType)
                .flatMap(SchemaBase::getFilterGeometry)
                .map(
                    property ->
                        (exclusionPolygon
                            .map(
                                p ->
                                    (Cql2Expression)
                                        And.of(
                                            SIntersects.of(
                                                Property.of(property.getFullPathAsString()),
                                                SpatialLiteral.of(Bbox.of(bbox))),
                                            Not.of(
                                                SIntersects.of(
                                                    Property.of(property.getFullPathAsString()),
                                                    SpatialLiteral.of(p)))))
                            .orElse(
                                SIntersects.of(
                                    Property.of(property.getFullPathAsString()),
                                    SpatialLiteral.of(Bbox.of(bbox))))))
                .orElse(BooleanValue2.of(false)))
        .build();
  }

  private ApiRequestContext getFeaturesRequestContext(
      OgcApi api,
      String collectionId,
      boolean clampToEllipsoid,
      String bboxString,
      String contentFilterString) {
    List<OgcApiQueryParameter> knownParameters =
        extensionRegistry.getExtensionsForType(EndpointSubCollection.class).stream()
            .filter(endpoint -> endpoint.isEnabledForApi(api.getData(), collectionId))
            .filter(
                endpoint ->
                    endpoint
                        .getDefinition(api.getData())
                        .matches(String.format("/collections/%s/items", collectionId), "GET"))
            .map(
                endpoint ->
                    endpoint.getQueryParameters(
                        extensionRegistry,
                        api.getData(),
                        "/collections/{collectionId}/items",
                        collectionId))
            .filter(list -> !list.isEmpty())
            .findFirst()
            .orElse(ImmutableList.of());
    Map<String, String> actualParameters =
        ImmutableMap.of(
            "bbox",
            bboxString,
            "filter",
            contentFilterString,
            "clampToEllipsoid",
            String.valueOf(clampToEllipsoid));
    QueryParameterSet queryParameterSet =
        QueryParameterSet.of(knownParameters, actualParameters)
            .evaluate(api, api.getData().getCollectionData(collectionId));
    return new ImmutableStaticRequestContext.Builder()
        .webContext(servicesContext)
        .api(api)
        .requestUri(
            URI.create(
                new URICustomizer(servicesContext.getUri())
                    .ensureLastPathSegments(api.getId(), "collections", collectionId, "items")
                    .toString()))
        .queryParameterSet(queryParameterSet)
        .mediaType(MEDIA_TYPE)
        .alternateMediaTypes(ImmutableList.of())
        .build();
  }

  private FeatureQuery getFinalQuery(String filter, FeatureQuery query) {
    return ImmutableFeatureQuery.builder()
        .from(query)
        .filter(And.of(query.getFilter().orElseThrow(), cql.read(filter, Format.TEXT)))
        .build();
  }

  private Optional<OgcApi> getOgcApi(String id) {
    return entityRegistry.getEntity(OgcApi.class, id);
  }
}
