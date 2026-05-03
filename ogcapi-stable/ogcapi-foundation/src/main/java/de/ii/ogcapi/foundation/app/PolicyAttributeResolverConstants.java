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
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class PolicyAttributeResolverConstants implements PolicyAttributeResolver {

  private static final String PREFIX = "ldproxy:constant:";

  @Inject
  PolicyAttributeResolverConstants() {}

  @Override
  public Category getCategory() {
    return Category.ACTION;
  }

  @Override
  public boolean canResolve(Map<String, PolicyAttribute> attributes, ApiOperation apiOperation) {
    return attributes.values().stream().anyMatch(attribute -> attribute.getConstant().isPresent());
  }

  @Override
  public Map<String, ?> resolve(
      Map<String, PolicyAttribute> attributes,
      ApiOperation apiOperation,
      ApiRequestContext requestContext,
      Optional<byte[]> body) {
    return attributes.entrySet().stream()
        .filter(entry -> entry.getValue().getConstant().isPresent())
        .map(entry -> Map.entry(PREFIX + entry.getKey(), entry.getValue().getConstant().get()))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
