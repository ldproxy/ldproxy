/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

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
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.tiles3d.domain.TileResourceDescriptor;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.cql.domain.And;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql.Format;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public final class Tiles3dContentUtil {

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("model", "gltf-binary"))
          .label("glTF-Binary")
          .parameter("glb")
          .build();

  private Tiles3dContentUtil() {}

  public static Response getContent(
      ServicesContext servicesContext,
      ExtensionRegistry extensionRegistry,
      OgcApi api,
      String collectionId,
      FeatureProvider provider,
      FeaturesCoreQueriesHandler queriesHandlerFeatures,
      Cql cql,
      Tiles3dConfiguration cfg,
      TileResourceDescriptor r,
      FeatureQuery featureQuery,
      URICustomizer uriCustomizer,
      Optional<QueryInput> queryInputGeneric)
      throws URISyntaxException {

    String contentFilterString = "true";
    FeatureQuery query =
        cfg.getContentFilters().isEmpty()
            ? featureQuery
            : getFinalQuery(
                cfg.getContentFilters()
                    .get(r.getLevel() - Objects.requireNonNull(cfg.getFirstLevelWithContent())),
                featureQuery,
                cql);

    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput =
        getQueryInputFeatures(query, provider, r, queryInputGeneric);

    ApiRequestContext requestContextGltf =
        getFeaturesRequestContext(
            servicesContext,
            extensionRegistry,
            api,
            collectionId,
            uriCustomizer,
            cfg,
            r,
            contentFilterString);

    return queriesHandlerFeatures.handle(Query.FEATURES, queryInput, requestContextGltf);
  }

  private static FeaturesCoreQueriesHandler.QueryInputFeatures getQueryInputFeatures(
      FeatureQuery query,
      FeatureProvider provider,
      TileResourceDescriptor r,
      Optional<QueryInput> queryInputGeneric) {
    FeaturesCoreQueriesHandler.QueryInputFeatures queryInput;
    ImmutableQueryInputFeatures.Builder builder = new ImmutableQueryInputFeatures.Builder();
    queryInputGeneric.ifPresent(builder::from);
    queryInput =
        builder
            .collectionId(r.getCollectionId())
            .query(query)
            .featureProvider(provider)
            .defaultCrs(OgcCrs.CRS84h)
            .defaultPageSize(Optional.of(TileResourceDescriptor.MAX_FEATURES_PER_TILE))
            .sendResponseAsStream(false)
            .build();
    return queryInput;
  }

  private static ApiRequestContext getFeaturesRequestContext(
      ServicesContext servicesContext,
      ExtensionRegistry extensionRegistry,
      OgcApi api,
      String collectionId,
      URICustomizer uriCustomizer,
      Tiles3dConfiguration cfg,
      TileResourceDescriptor r,
      String contentFilterString)
      throws URISyntaxException {

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
            r.getBboxString(),
            "filter",
            contentFilterString,
            "clampToEllipsoid",
            String.valueOf(cfg.shouldClampToEllipsoid()));
    QueryParameterSet queryParameterSet =
        QueryParameterSet.of(knownParameters, actualParameters)
            .evaluate(api, api.getData().getCollectionData(collectionId));
    return new ImmutableStaticRequestContext.Builder()
        .webContext(servicesContext)
        .api(r.getApi())
        .requestUri(
            uriCustomizer
                .copy()
                .removeLastPathSegments(2)
                .ensureLastPathSegment("items")
                .clearParameters()
                // query parameters have been evaluated and are not necessary here
                .build())
        .queryParameterSet(queryParameterSet)
        .mediaType(MEDIA_TYPE)
        .alternateMediaTypes(ImmutableList.of())
        .build();
  }

  private static FeatureQuery getFinalQuery(String filter, FeatureQuery query, Cql cql) {
    return ImmutableFeatureQuery.builder()
        .from(query)
        .filter(And.of(query.getFilter().orElseThrow(), cql.read(filter, Format.TEXT)))
        .build();
  }
}
