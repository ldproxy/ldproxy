/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.ProfileFeatureQuery;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase.Scope;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileAllAsReceivable extends ProfileFeatureQuery {

  private static final String ID = "all-as-receivable";

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
}
