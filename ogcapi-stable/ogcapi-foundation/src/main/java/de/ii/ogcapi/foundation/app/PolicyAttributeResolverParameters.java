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
import de.ii.ogcapi.foundation.domain.PolicyAttributeResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Optional;

@Singleton
@AutoBind
public class PolicyAttributeResolverParameters implements PolicyAttributeResolver {
  private static final String PREFIX = "ldproxy:request:parameter:";

  @Inject
  PolicyAttributeResolverParameters() {}

  @Override
  public Category getCategory() {
    return Category.ACTION;
  }

  @Override
  public boolean canResolve(Map<String, PolicyAttribute> attributes, ApiOperation apiOperation) {
    return !apiOperation.getQueryParameters().isEmpty()
        && attributes.values().stream().anyMatch(attribute -> attribute.getParameter().isPresent());
  }

  @Override
  public Map<String, ?> resolve(
      Map<String, PolicyAttribute> attributes,
      ApiOperation apiOperation,
      ApiRequestContext requestContext,
      Optional<byte[]> body) {
    return attributes.entrySet().stream()
        .filter(entry -> entry.getValue().getParameter().isPresent())
        .filter(
            entry ->
                requestContext.getParameters().containsKey(entry.getValue().getParameter().get()))
        .map(
            entry ->
                Map.entry(
                    PREFIX + entry.getKey(),
                    requestContext.getParameters().get(entry.getValue().getParameter().get())))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
