/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.ProfileFeatureQuery;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.CqlNode;
import de.ii.xtraplatform.cql.domain.TIntersects;
import de.ii.xtraplatform.cql.domain.TemporalLiteral;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Profile {@code versions-as-features-unique-ids}: like {@link ProfileVersionsAsFeatures} except
 * each version's feature id is rewritten to {@code <canonical>.<compactTimestamp>} so the response
 * can carry multiple versions of the same canonical feature without colliding ids (required by
 * formats that demand unique feature ids, e.g. GML).
 *
 * <p>The composite id uses the same {@code compositeIdPattern} + {@code compositeIdTimestampFormat}
 * configured on {@link VersionedFeaturesConfiguration} (and consumed inversely by {@link
 * VersionedMutationStrategy#splitCompositeId} on the write path), so a round-trip from
 * id-on-the-wire to canonical id and back is exactly the configured shape.
 *
 * <p>The profile URI is {@code
 * http://www.opengis.net/def/profile/ogc/0/versions-as-features-unique-ids}.
 */
@Singleton
@AutoBind
public class ProfileVersionsAsFeaturesUniqueIds extends ProfileFeatureQuery {

  public static final String ID = "versions-as-features-unique-ids";

  @Inject
  ProfileVersionsAsFeaturesUniqueIds(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getProfileSet() {
    return ProfileSetVersions.ID;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public FeatureQuery transformFeatureQuery(FeatureQuery query) {
    return query;
  }

  @Override
  public FeatureQuery transformFeatureQuery(
      FeatureQuery query, OgcApiDataV2 apiData, String collectionId) {
    Optional<VersionedFeaturesConfiguration> cfg =
        apiData.getExtension(VersionedFeaturesConfiguration.class, collectionId);
    String pattern = cfg.map(VersionedFeaturesConfiguration::getCompositeIdPattern).orElse(null);
    if (pattern == null || pattern.isBlank()) {
      return query;
    }
    // The composite id only matters when the response could carry multiple versions of the
    // same canonical id — i.e. when the request's `datetime` is an interval. For an instant
    // (or `NOW`), the OVERLAPS half-open semantics select at most one version per canonical
    // id, so feature ids are already unique. Skip the rewrite in that case.
    if (!hasIntervalDatetime(query)) {
      return query;
    }
    String fmt =
        cfg.map(VersionedFeaturesConfiguration::getCompositeIdTimestampFormat).orElse(null);
    return ImmutableFeatureQuery.builder()
        .from(query)
        .addExtensions(new CompositeIdExtension(pattern, fmt))
        .build();
  }

  // Walk the query filter looking for a TIntersects whose temporal-literal operand carries an
  // Interval value. That filter shape is what QueryParameterDatetime emits when the request's
  // datetime parameter is an interval (bounded or half-bounded, including "../..").
  private static boolean hasIntervalDatetime(FeatureQuery query) {
    for (Cql2Expression filter : query.getFilters()) {
      if (containsIntervalTemporalLiteral(filter)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIntervalTemporalLiteral(CqlNode node) {
    if (node instanceof TIntersects) {
      for (CqlNode child : ((TIntersects) node).getArgs()) {
        if (child instanceof TemporalLiteral
            && ((TemporalLiteral) child).getInterval().isPresent()) {
          return true;
        }
      }
    }
    if (node instanceof de.ii.xtraplatform.cql.domain.LogicalOperation) {
      for (CqlNode child : ((de.ii.xtraplatform.cql.domain.LogicalOperation) node).getArgs()) {
        if (containsIntervalTemporalLiteral(child)) {
          return true;
        }
      }
    }
    return false;
  }
}
