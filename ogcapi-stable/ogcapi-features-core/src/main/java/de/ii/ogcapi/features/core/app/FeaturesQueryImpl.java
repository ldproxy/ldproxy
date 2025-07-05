/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureQueryParameter;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesQuery;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.cql.domain.Cql2Expression;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.crs.domain.CrsInfo;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class FeaturesQueryImpl extends AbstractVolatileComposed implements FeaturesQuery {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesQueryImpl.class);

  private final CrsInfo crsInfo;
  private final Cql cql;

  @Inject
  public FeaturesQueryImpl(CrsInfo crsInfo, Cql cql, VolatileRegistry volatileRegistry) {
    super(FeaturesQuery.class.getSimpleName(), volatileRegistry, true);
    this.crsInfo = crsInfo;
    this.cql = cql;

    onVolatileStart();

    addSubcomponent(crsInfo);

    onVolatileStarted();
  }

  @Override
  public FeatureQuery requestToFeatureQuery(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      EpsgCrs defaultCrs,
      Map<String, Integer> coordinatePrecision,
      QueryParameterSet queryParameterSet,
      String featureId,
      Optional<ETag.Type> withEtag,
      SchemaBase.Scope withScope) {
    final ImmutableFeatureQuery.Builder queryBuilder =
        ImmutableFeatureQuery.builder()
            .type(
                collectionData
                    .getExtension(FeaturesCoreConfiguration.class)
                    .flatMap(FeaturesCoreConfiguration::getFeatureType)
                    .orElse(collectionData.getId()))
            .filter(In.of(ScalarLiteral.of(featureId)))
            .returnsSingleFeature(true)
            .crs(defaultCrs)
            .eTag(withEtag)
            .schemaScope(withScope);

    return processParameters(
        queryBuilder, apiData, collectionData, coordinatePrecision, queryParameterSet);
  }

  @Override
  public FeatureQuery requestToFeatureQuery(
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      EpsgCrs defaultCrs,
      Map<String, Integer> coordinatePrecision,
      int defaultPageSize,
      QueryParameterSet queryParameterSet) {
    final ImmutableFeatureQuery.Builder queryBuilder =
        ImmutableFeatureQuery.builder()
            .type(
                collectionData
                    .getExtension(FeaturesCoreConfiguration.class)
                    .flatMap(FeaturesCoreConfiguration::getFeatureType)
                    .orElse(collectionData.getId()))
            .crs(defaultCrs)
            .limit(defaultPageSize);

    return processParameters(
        queryBuilder, apiData, collectionData, coordinatePrecision, queryParameterSet);
  }

  @Override
  public FeatureQuery requestToBareFeatureQuery(
      OgcApiDataV2 apiData,
      String featureTypeId,
      EpsgCrs defaultCrs,
      Map<String, Integer> coordinatePrecision,
      int defaultPageSize,
      QueryParameterSet queryParameterSet) {

    final ImmutableFeatureQuery.Builder queryBuilder =
        ImmutableFeatureQuery.builder().type(featureTypeId).crs(defaultCrs).limit(defaultPageSize);

    return processParameters(queryBuilder, apiData, null, coordinatePrecision, queryParameterSet);
  }

  @Override
  public Optional<String> validateFilter(
      String filter,
      Cql.Format filterLang,
      EpsgCrs filterCrs,
      Map<String, FeatureSchema> queryables) {
    try {
      Cql2Expression cql2Expression = cql.read(filter, filterLang, filterCrs);

      List<String> invalidProperties =
          cql.findInvalidProperties(cql2Expression, queryables.keySet());
      if (!invalidProperties.isEmpty()) {
        throw new IllegalArgumentException(
            String.format(
                "Unknown or forbidden properties used: %s.", String.join(", ", invalidProperties)));
      }

      cql.checkTypes(
          cql2Expression,
          queryables.entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      Entry::getKey, entry -> entry.getValue().getType().toString())));
    } catch (Throwable e) {
      return Optional.of(String.format("The filter '%s' is invalid: %s", filter, e.getMessage()));
    }

    return Optional.empty();
  }

  private FeatureQuery processParameters(
      ImmutableFeatureQuery.Builder queryBuilder,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      Map<String, Integer> coordinatePrecision,
      QueryParameterSet queryParameterSet) {
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof FeatureQueryParameter) {
        ((FeatureQueryParameter) parameter)
            .applyTo(queryBuilder, queryParameterSet, apiData, collectionData);
      }
    }

    processCoordinatePrecision(queryBuilder, coordinatePrecision);

    FeatureQuery query = queryBuilder.build();

    if (LOGGER.isDebugEnabled()) {
      query.getFilter().ifPresent(filter -> LOGGER.debug("Filter: {}", filter));
      LOGGER.debug("FeatureQuery: {}", query);
    }

    return query;
  }

  private void processCoordinatePrecision(
      ImmutableFeatureQuery.Builder queryBuilder, Map<String, Integer> coordinatePrecision) {
    // check, if we need to add a precision value; for this we need the target CRS,
    // so we need to build the query to get the CRS
    ImmutableFeatureQuery query = queryBuilder.build();
    if (!coordinatePrecision.isEmpty() && query.getCrs().isPresent()) {
      List<Integer> precisionList =
          crsInfo.getPrecisionList(query.getCrs().get(), coordinatePrecision);
      if (!precisionList.isEmpty()) {
        queryBuilder.geometryPrecision(precisionList);
      }
      precisionList = crsInfo.getPrecisionList(OgcCrs.CRS84h, coordinatePrecision);
      if (!precisionList.isEmpty()) {
        queryBuilder.wgs84GeometryPrecision(precisionList);
      }
    }
  }
}
