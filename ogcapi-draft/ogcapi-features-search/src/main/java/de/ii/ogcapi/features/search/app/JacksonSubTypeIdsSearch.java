/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.base.domain.ImmutableJacksonSubType;
import de.ii.xtraplatform.base.domain.JacksonSubTypeIds;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@AutoBind
public class JacksonSubTypeIdsSearch implements JacksonSubTypeIds {

  @Inject
  public JacksonSubTypeIdsSearch() {}

  @Override
  public List<JacksonSubType> getSubTypes() {
    return ImmutableList.of(
        ImmutableJacksonSubType.builder()
            .superType(ExtensionConfiguration.class)
            .subType(SearchConfiguration.class)
            .id(ExtensionConfiguration.getBuildingBlockIdentifier(SearchConfiguration.class))
            .build());
  }
}
