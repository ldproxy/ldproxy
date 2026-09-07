/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.QueryInputFeature;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.immutables.value.Value;

public interface CommandHandlerCrud extends Volatile2 {

  Response postItemsResponse(QueryInputFeatureCreate queryInput, ApiRequestContext requestContext);

  Response putItemResponse(QueryInputFeatureReplace queryInput, ApiRequestContext requestContext);

  Response patchItemResponse(QueryInputFeatureReplace queryInput, ApiRequestContext requestContext);

  Response deleteItemResponse(QueryInputFeatureDelete queryInput, ApiRequestContext requestContext);

  /**
   * The representation of the feature that a response includes, where the client prefers one in a
   * {@code Prefer} header with the value {@code return=representation} (RFC 7240, 4.2) and the
   * collection supports the preference.
   */
  @Value.Immutable
  interface Representation {

    /** The negotiated media type of the representation. */
    ApiMediaType getMediaType();

    /** The other media types that the collection supports for a representation of the feature. */
    Set<ApiMediaType> getAlternateMediaTypes();

    /** The query parameters that determine the representation. */
    QueryParameterSet getQueryParameterSet();
  }

  @Value.Immutable
  interface QueryInputFeatureCreate extends QueryInput {

    String getCollectionId();

    String getFeatureType();

    EpsgCrs getCrs();

    FeatureProvider getFeatureProvider();

    InputStream getRequestBody();

    MediaType getContentType();

    boolean getValidate();

    /**
     * Whether an empty value in the request body makes it invalid. Only ever true together with
     * {@link #getValidate()}, i.e. for a request with {@code Prefer: handling=strict}.
     */
    @Value.Default
    default boolean getRejectEmptyValues() {
      return false;
    }

    @Value.Default
    default List<String> getLinkHeaders() {
      return List.of();
    }

    /**
     * The representation of the feature to include in the response body, empty if the response has
     * no body.
     */
    Optional<Representation> getRepresentation();
  }

  interface QueryInputFeatureCrud extends QueryInputFeature {

    QueryParameterSet getQueryParameterSet();

    /**
     * Query for the returnable representation of the feature, present if the collection supports
     * conditional processing based on the last modification time. It is used to determine the last
     * modification time of the feature, if the property is not part of the representation that is
     * used in mutation requests.
     */
    Optional<FeatureQuery> getLastModifiedQuery();

    /**
     * Whether a request that changes an existing feature of the collection has to state a
     * precondition.
     */
    boolean isPreconditionRequired();

    /** The 'If-Match' header of the request. */
    Optional<String> getIfMatch();

    /** The 'If-Unmodified-Since' header of the request. */
    Optional<String> getIfUnmodifiedSince();
  }

  @Value.Immutable
  interface QueryInputFeatureReplace extends QueryInputFeatureCrud, QueryInputFeatureCreate {

    boolean isAllowCreate();
  }

  @Value.Immutable
  interface QueryInputFeatureDelete extends QueryInputFeatureCrud {

    String getFeatureType();
  }
}
