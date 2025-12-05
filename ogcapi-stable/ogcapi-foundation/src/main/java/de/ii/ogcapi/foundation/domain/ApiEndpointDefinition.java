/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import io.swagger.v3.oas.models.OpenAPI;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public abstract class ApiEndpointDefinition {

  // stable 0 - 999
  public static final int SORT_PRIORITY_LANDING_PAGE = 0;
  public static final int SORT_PRIORITY_CONFORMANCE = 1;
  public static final int SORT_PRIORITY_API_DEFINITION = 2;
  public static final int SORT_PRIORITY_ASYNC_API_DEFINITION = 3;
  public static final int SORT_PRIORITY_COLLECTIONS = 10;
  public static final int SORT_PRIORITY_COLLECTION = 11;
  public static final int SORT_PRIORITY_FEATURES = 100;

  // draft 1000 - 9999
  public static final int SORT_PRIORITY_SEARCH_STORED_QUERIES = 1000;
  public static final int SORT_PRIORITY_SEARCH_AD_HOC = 1010;
  public static final int SORT_PRIORITY_SEARCH_STORED_QUERY = 1020;
  public static final int SORT_PRIORITY_SEARCH_PARAMETERS = 1030;
  public static final int SORT_PRIORITY_SEARCH_PARAMETER = 1040;
  public static final int SORT_PRIORITY_SEARCH_MANAGER = 1050;
  public static final int SORT_PRIORITY_FEATURES_TRANSACTION = 1100;
  public static final int SORT_PRIORITY_QUERYABLES = 1200;
  public static final int SORT_PRIORITY_SORTABLES = 1210;
  public static final int SORT_PRIORITY_SCHEMA = 1230;
  public static final int SORT_PRIORITY_FEATURES_EXTENSIONS = 1400;
  public static final int SORT_PRIORITY_FEATURES_JSONLD_CONTEXT = 1450;
  public static final int SORT_PRIORITY_TILE_SETS = 1500;
  public static final int SORT_PRIORITY_TILE_SET = 1510;
  public static final int SORT_PRIORITY_TILE = 1520;
  public static final int SORT_PRIORITY_TILE_SETS_COLLECTION = 1530;
  public static final int SORT_PRIORITY_TILE_SET_COLLECTION = 1540;
  public static final int SORT_PRIORITY_TILE_COLLECTION = 1550;
  public static final int SORT_PRIORITY_MAP_TILE_SETS = 1600;
  public static final int SORT_PRIORITY_MAP_TILE_SET = 1610;
  public static final int SORT_PRIORITY_MAP_TILE = 1620;
  public static final int SORT_PRIORITY_MAP_TILE_SETS_COLLECTION = 1630;
  public static final int SORT_PRIORITY_MAP_TILE_SET_COLLECTION = 1640;
  public static final int SORT_PRIORITY_MAP_TILE_COLLECTION = 1650;
  public static final int SORT_PRIORITY_TILE_MATRIX_SETS = 1700;
  public static final int SORT_PRIORITY_STYLES = 2000;
  public static final int SORT_PRIORITY_STYLESHEET = 2010;
  public static final int SORT_PRIORITY_STYLE_METADATA = 2020;
  public static final int SORT_PRIORITY_STYLE_LEGEND = 2025;
  public static final int SORT_PRIORITY_STYLES_MANAGER = 2030;
  public static final int SORT_PRIORITY_STYLES_COLLECTION = 2050;
  public static final int SORT_PRIORITY_STYLESHEET_COLLECTION = 2060;
  public static final int SORT_PRIORITY_STYLE_METADATA_COLLECTION = 2070;
  public static final int SORT_PRIORITY_STYLE_LEGEND_COLLECTION = 2075;
  public static final int SORT_PRIORITY_STYLES_MANAGER_COLLECTION = 2080;
  public static final int SORT_PRIORITY_RESOURCES = 2100;
  public static final int SORT_PRIORITY_RESOURCE = 2110;
  public static final int SORT_PRIORITY_RESOURCES_MANAGER = 2120;
  public static final int SORT_PRIORITY_WMTS = 2200;
  public static final int SORT_PRIORITY_ROUTES_POST = 2500;
  public static final int SORT_PRIORITY_ROUTES_GET = 2510;
  public static final int SORT_PRIORITY_ROUTE_GET = 2520;
  public static final int SORT_PRIORITY_ROUTE_DELETE = 2530;
  public static final int SORT_PRIORITY_ROUTE_DEFINITION = 2540;

  public static final int SORT_PRIORITY_3D_TILES = 3000;
  public static final int SORT_PRIORITY_3D_TILES_CONTENT = 3050;
  public static final int SORT_PRIORITY_3D_TILES_SUBTREE = 3060;

  public static final int SORT_PRIORITY_DUMMY = Integer.MAX_VALUE;

  /**
   * @return the entrypoint resource for this definition, all sub-paths are relative to this base
   *     path
   */
  public abstract String getApiEntrypoint();

  /**
   * @param subPath resource path relative to the base path of the API entrypoint
   * @return API path ("/" is the API landing page)
   */
  public String getPath(String subPath) {
    return "/" + getApiEntrypoint() + subPath;
  }

  /**
   * @return the sort priority of the definition, relative to the other definitions in the API
   *     definition
   */
  public abstract int getSortPriority();

  /**
   * @return a map of API paths to the resource at the path
   */
  public abstract Map<String, OgcApiResource> getResources();

  /**
   * @return if this endpoint should be hidden from the API documentation
   */
  @Value.Default
  public boolean getHidden() {
    return false;
  }

  /**
   * Checks, if a request is supported by this endpoint based on the API path and the HTTP method
   *
   * @param requestPath the path of the resource
   * @param method the HTTP method that; set to {@code null} for checking against all methods
   * @return flag, whether the endpoint supports the request
   */
  public boolean matches(String requestPath, String method) {
    return getOperation(requestPath, method).isPresent();
  }

  /**
   * Checks, if a request is supported by this endpoint based on the API path and the HTTP method
   *
   * @param resource the resource
   * @param method the HTTP method that; set to {@code null} for checking against all methods
   * @return the operation that supports the request
   */
  public Optional<ApiOperation> getOperation(OgcApiResource resource, String method) {
    // at least one method is supported?
    if (method == null) {
      return resource.getOperations().values().stream().findAny();
    }

    // support HEAD for all GETs
    if ("HEAD".equals(method)) {
      return Optional.ofNullable(resource.getOperations().get("GET"));
    }

    return Optional.ofNullable(resource.getOperations().get(method));
  }

  /**
   * Checks, if a request is supported by this endpoint based on the API path and the HTTP method
   *
   * @param requestPath the path of the resource
   * @param method the HTTP method that; set to {@code null} for checking against all methods
   * @return the operation that supports the request
   */
  public Optional<ApiOperation> getOperation(String requestPath, String method) {
    Optional<OgcApiResource> resource = getResource(requestPath);

    if (resource.isEmpty()) {
      return Optional.empty();
    }

    return getOperation(resource.get(), method);
  }

  /**
   * Checks, if a request may be supported by this endpoint based on the API path
   *
   * @param requestPath the path of the resource
   * @return the resource that supports the request
   */
  public Optional<OgcApiResource> getResource(String requestPath) {
    OgcApiResource resource = getResources().get(requestPath);
    if (resource == null) {
      // if nothing was found, replace path parameters with their pattern
      resource =
          getResources().values().stream()
              .filter(r -> r.getPathPatternCompiled().matcher(requestPath).matches())
              .findAny()
              .orElse(null);
    }

    return Optional.ofNullable(resource);
  }

  /**
   * adds the endpoint to the OpenAPI definition
   *
   * @param apiData the data about the API
   * @param openAPI the OpenAPI definition without the endpoint
   */
  public void updateOpenApiDefinition(OgcApiDataV2 apiData, OpenAPI openAPI) {
    getResources().values().stream()
        .sorted(Comparator.comparing(OgcApiResource::getPath))
        .forEachOrdered(resource -> resource.updateOpenApiDefinition(apiData, openAPI));
  }
}
