/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.ProfileResponseCrs;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StringOrParameter;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether the response of a stored query can be represented as HTML, before the query is executed:
 * HTML is only available for queries with paging whose positions are in the default CRS of the API
 * (see the check in SearchQueriesHandlerImpl.executeQuery). A parameterized CRS or profile may
 * resolve to a non-default CRS at execution time, so HTML is not offered for such queries.
 */
final class StoredQueryHtmlSupport {

  private final EpsgCrs defaultCrs;
  private final Set<String> crsProfileIds;

  StoredQueryHtmlSupport(OgcApiDataV2 apiData, ExtensionRegistry extensionRegistry) {
    this(
        apiData
            .getExtension(FeaturesCoreConfiguration.class)
            .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
            .orElse(OgcCrs.CRS84),
        extensionRegistry.getExtensionsForType(Profile.class).stream()
            .filter(ProfileResponseCrs.class::isInstance)
            .map(Profile::getId)
            .collect(Collectors.toUnmodifiableSet()));
  }

  /**
   * @param defaultCrs the default CRS of the API
   * @param crsProfileIds the ids of the profiles that select the CRS of the response
   */
  StoredQueryHtmlSupport(EpsgCrs defaultCrs, Set<String> crsProfileIds) {
    this.defaultCrs = defaultCrs;
    this.crsProfileIds = crsProfileIds;
  }

  boolean offersHtml(StoredQueryExpression query) {
    if (!query.getSupportPaging().orElse(false)) {
      return false;
    }
    if (selectsCrs(query)) {
      return false;
    }
    if (query.getCrs().isEmpty()) {
      return true;
    }
    if (query.getVerticalCrs().isPresent()) {
      return false;
    }
    return query
        .getCrs()
        .flatMap(StringOrParameter::getValue)
        .map(uri -> EpsgCrs.fromString(uri).equals(defaultCrs))
        .orElse(false);
  }

  boolean anyOffersHtml(List<StoredQueryExpression> queries) {
    return queries.stream().anyMatch(this::offersHtml);
  }

  private boolean selectsCrs(StoredQueryExpression query) {
    if (query.getProfiles().isEmpty() || crsProfileIds.isEmpty()) {
      return false;
    }
    Optional<List<StringOrParameter>> profiles = query.getProfiles().get().getValue();
    if (profiles.isEmpty()) {
      // the whole list is a parameter, so its values are only known at execution time
      return true;
    }
    return profiles.get().stream()
        .anyMatch(
            profile ->
                profile
                    .getValue()
                    .map(crsProfileIds::contains)
                    // a parameterized entry may resolve to such a profile
                    .orElse(true));
  }
}
