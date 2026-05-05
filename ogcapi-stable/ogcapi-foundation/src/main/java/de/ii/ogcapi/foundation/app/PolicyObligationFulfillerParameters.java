/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ApiSecurity.PolicyAttribute;
import de.ii.ogcapi.foundation.domain.ImmutableRequestContext.Builder;
import de.ii.ogcapi.foundation.domain.PolicyObligationFulfiller;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.base.domain.util.Tuple;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
@AutoBind
public class PolicyObligationFulfillerParameters implements PolicyObligationFulfiller {

  @Inject
  PolicyObligationFulfillerParameters() {}

  @Override
  public boolean canFulfill(Map<String, PolicyAttribute> obligations, ApiOperation apiOperation) {
    return !apiOperation.getQueryParameters().isEmpty()
        && obligations.values().stream()
            .anyMatch(obligation -> obligation.getParameter().isPresent());
  }

  @Override
  public Tuple<ApiRequestContext, Set<String>> fulfill(
      Map<String, PolicyAttribute> obligations,
      ApiOperation apiOperation,
      ApiRequestContext requestContext,
      Map<String, String> values) {
    final QueryParameterSet[] newQueryParameterSet = {requestContext.getQueryParameterSet()};
    Set<String> fulfilled = new HashSet<>();

    obligations.entrySet().stream()
        .filter(
            entry ->
                entry.getValue().getParameter().isPresent() && values.containsKey(entry.getKey()))
        .forEach(
            entry -> {
              String attribute = entry.getKey();
              String parameter = entry.getValue().getParameter().get();

              newQueryParameterSet[0] =
                  newQueryParameterSet[0].merge(
                      requestContext.getApi(),
                      requestContext
                          .getCollectionId()
                          .flatMap(
                              collectionId ->
                                  requestContext
                                      .getApi()
                                      .getData()
                                      .getCollectionData(collectionId)),
                      ImmutableMap.of(parameter, values.get(attribute)));
              fulfilled.add(attribute);
            });

    ApiRequestContext newRequestContext =
        new Builder().from(requestContext).queryParameterSet(newQueryParameterSet[0]).build();

    return Tuple.of(newRequestContext, fulfilled);
  }
}
