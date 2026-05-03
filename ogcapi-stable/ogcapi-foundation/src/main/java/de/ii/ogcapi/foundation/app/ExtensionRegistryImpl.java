/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import dagger.Lazy;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@AutoBind
public class ExtensionRegistryImpl implements ExtensionRegistry {

  private final Lazy<Set<ApiExtension>> apiExtensions;

  @Inject
  ExtensionRegistryImpl(Lazy<Set<ApiExtension>> apiExtensions) {
    this.apiExtensions = apiExtensions;
  }

  @Override
  public List<ApiExtension> getExtensions() {
    return ImmutableList.copyOf(apiExtensions.get());
  }

  @Override
  public <T extends ApiExtension> List<T> getExtensionsForType(Class<T> extensionType) {
    return apiExtensions.get().stream()
        .filter(
            extension -> extension != null && extensionType.isAssignableFrom(extension.getClass()))
        .map(extensionType::cast)
        .collect(Collectors.toList());
  }
}
