/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.features.core.domain.ProfileFeatureQuery;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileFilter;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileAllAsReceivable extends ProfileFeatureQuery implements ProfileFilter {

  private static final String ID = "all-as-receivable";
  private static final List<String> IGNORE_SETS = List.of("rel", "val");

  @Inject
  ProfileAllAsReceivable(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getProfileSet() {
    return ProfileSetAll.ID;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }

  @Override
  public FeatureQuery transformFeatureQuery(FeatureQuery query) {
    return ImmutableFeatureQuery.builder().from(query).schemaScope(Scope.RECEIVABLE).build();
  }

  @Override
  public List<Profile> filterProfiles(List<Profile> profiles) {
    return profiles.stream()
        .filter(profile -> !IGNORE_SETS.contains(profile.getProfileSet()))
        .toList();
  }
}
