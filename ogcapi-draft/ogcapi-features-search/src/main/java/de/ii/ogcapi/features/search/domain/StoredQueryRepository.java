/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// TODO move to xtraplatform-spatial
public interface StoredQueryRepository extends Volatile2 {

  /**
   * get a stream of all formats available for this query collection
   *
   * @param apiData information about the API
   * @return stream of encodings for a query collection
   */
  Stream<StoredQueriesFormat> getStoredQueriesFormatStream(OgcApiDataV2 apiData);

  /**
   * get a stream of all query encodings available for this stored queries collection
   *
   * @param apiData information about the API
   * @return stream of query encodings
   */
  Stream<StoredQueryFormat> getStoredQueryFormatStream(OgcApiDataV2 apiData);

  /**
   * get the list of all query encodings available for this query
   *
   * @param apiData information about the API
   * @param queryId the identifier of the query in the query collection
   * @return list of query encodings for this query
   */
  List<ApiMediaType> getStoredQueryMediaTypes(OgcApiDataV2 apiData, String queryId);

  /**
   * get an object representation for this stored queries collection, derived stored queries are
   * ignored
   *
   * @param apiData information about the API
   * @return the list of stored queries in this collection and links to API resources
   */
  List<StoredQueryExpression> getAll(OgcApiDataV2 apiData);

  /**
   * fetches a stored query
   *
   * @param apiData information about the API
   * @param queryId the identifier of the query in the query collection
   * @return the stored queriesheet, or throws an exception, if not available
   */
  StoredQueryExpression get(OgcApiDataV2 apiData, String queryId);

  /**
   * determine, if a stored query is available
   *
   * @param apiData information about the API
   * @param queryId the identifier of the query in the query collection
   * @return {@code true}, if the stored query exists
   */
  boolean exists(OgcApiDataV2 apiData, String queryId);

  /**
   * determine date of last change to a stored query
   *
   * @param apiData information about the API
   * @param queryId the identifier of the query
   * @return the date or {@code null}, if no stored query is found
   */
  Date getLastModified(OgcApiDataV2 apiData, String queryId);

  /**
   * determine date of last change to any stored query
   *
   * @param apiData information about the API
   * @return the date or {@code null}, if no stored query is found
   */
  Date getLastModified(OgcApiDataV2 apiData);

  /**
   * validate the query configuration during startup
   *
   * @param builder the startup validation result builder
   * @param apiData information about the API
   * @return the updated validation result builder
   */
  ImmutableValidationResult.Builder validate(
      ImmutableValidationResult.Builder builder, OgcApiDataV2 apiData);

  Set<String> getIds(OgcApiDataV2 apiData);

  void writeStoredQueryDocument(OgcApiDataV2 apiData, String queryId, StoredQueryExpression query)
      throws IOException;

  void deleteStoredQuery(OgcApiDataV2 apiData, String queryId) throws IOException;
}
