/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.cache.domain.Cache;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.services.domain.Service;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OgcApi extends Service {

  @Override
  OgcApiDataV2 getData();

  boolean isAvailable(ApiExtension extension, boolean checkBuildingBlock);

  <T extends FormatExtension> Optional<T> getOutputFormat(
      Class<T> extensionType, ApiMediaType mediaType, Optional<String> collectionId);

  <T extends FormatExtension> List<T> getAllOutputFormats(
      Class<T> extensionType, ApiMediaType mediaType, Optional<T> excludeFormat);

  /**
   * Determine spatial extent of all collections in the dataset.
   *
   * @return the bounding box in the default CRS
   */
  Optional<BoundingBox> getSpatialExtent();

  /**
   * Determine spatial extent of all collections in the dataset in another CRS.
   *
   * @param targetCrs the target CRS
   * @return the bounding box
   */
  Optional<BoundingBox> getSpatialExtent(EpsgCrs targetCrs);

  /**
   * Determine spatial extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @return the bounding box in the default CRS
   */
  Optional<BoundingBox> getSpatialExtent(String collectionId);

  /**
   * Determine spatial extent of a collection in the dataset in another CRS.
   *
   * @param collectionId the name of the feature type
   * @param targetCrs the target CRS
   * @return the bounding box in the target CRS
   */
  Optional<BoundingBox> getSpatialExtent(String collectionId, EpsgCrs targetCrs);

  default Optional<BoundingBox> getSpatialExtent(Optional<String> collectionId, EpsgCrs targetCrs) {
    return collectionId.isPresent()
        ? getSpatialExtent(collectionId.get(), targetCrs)
        : getSpatialExtent(targetCrs);
  }

  /**
   * Set spatial extent of the dataset.
   *
   * @param bbox the extent
   */
  void setSpatialExtent(BoundingBox bbox);

  /**
   * Set spatial extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @param bbox the extent
   */
  void setSpatialExtent(String collectionId, BoundingBox bbox);

  /**
   * Update spatial extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @param bbox the extent
   * @return {@code true} if the value changed and was non-empty before
   */
  boolean updateSpatialExtent(String collectionId, BoundingBox bbox);

  /**
   * Determine temporal extent of all collections in the dataset.
   *
   * @return the temporal extent in the Gregorian calendar
   */
  Optional<TemporalExtent> getTemporalExtent();

  /**
   * Determine temporal extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @return the temporal extent in the Gregorian calendar
   */
  Optional<TemporalExtent> getTemporalExtent(String collectionId);

  /**
   * Set temporal extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @param temporalExtent the extent
   */
  void setTemporalExtent(String collectionId, TemporalExtent temporalExtent);

  /**
   * Update temporal extent of a collection in the dataset.
   *
   * @param collectionId the name of the feature type
   * @param temporalExtent the extent
   * @return {@code true} if the value changed and was non-empty before
   */
  boolean updateTemporalExtent(String collectionId, TemporalExtent temporalExtent);

  /**
   * Determine timestamp of the last modification of all collections in the dataset.
   *
   * @return the timestamp
   */
  Optional<Instant> getLastModified();

  /**
   * Determine timestamp of the last modification in a collection.
   *
   * @param collectionId the name of the feature type
   * @return the timestamp
   */
  Optional<Instant> getLastModified(String collectionId);

  /**
   * Update timestamp of the last modification in a collection.
   *
   * @param collectionId the name of the feature type
   * @param lastModified the timestamp
   * @return {@code true} if the value changed and was non-empty before
   */
  boolean updateLastModified(String collectionId, Instant lastModified);

  /**
   * Determine the number of items in all collections in the dataset.
   *
   * @return the number of items
   */
  Optional<Long> getItemCount();

  /**
   * Determine the number of items in a collection.
   *
   * @param collectionId the name of the feature type
   * @return the number of items
   */
  Optional<Long> getItemCount(String collectionId);

  /**
   * Set the number of items in a collection.
   *
   * @param collectionId the name of the feature type
   * @param itemCount the item count
   */
  void setItemCount(String collectionId, Long itemCount);

  /**
   * Update the number of items in a collection.
   *
   * @param collectionId the name of the feature type
   * @param itemCount the item count
   * @return {@code true} if the value changed and was non-empty before
   */
  boolean updateItemCount(String collectionId, Long itemCount);

  // convenience methods

  default Optional<BoundingBox> getSpatialExtent(Optional<String> collectionId) {
    return collectionId.isPresent() ? getSpatialExtent(collectionId.get()) : getSpatialExtent();
  }

  default Optional<TemporalExtent> getTemporalExtent(Optional<String> collectionId) {
    return collectionId.isPresent() ? getTemporalExtent(collectionId.get()) : getTemporalExtent();
  }

  default Optional<Instant> getLastModified(Optional<String> collectionId) {
    return collectionId.isPresent() ? getLastModified(collectionId.get()) : getLastModified();
  }

  default Optional<Long> getItemCount(Optional<String> collectionId) {
    return collectionId.isPresent() ? getItemCount(collectionId.get()) : getItemCount();
  }

  URI getUri();

  boolean isAsyncStartup();

  Cache getCache();
}
