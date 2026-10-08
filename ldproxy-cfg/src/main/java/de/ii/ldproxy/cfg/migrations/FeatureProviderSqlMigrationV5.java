/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityMigration;
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSqlData;
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSqlData.DatasetChangeMode;
import de.ii.xtraplatform.features.sql.domain.ImmutableConnectionInfoSql;
import de.ii.xtraplatform.features.sql.domain.ImmutableDatasetChangeSettings;
import de.ii.xtraplatform.features.sql.domain.ImmutableFeatureProviderSqlData;
import java.util.Objects;
import java.util.Optional;

/**
 * Replaces the deprecated SQL feature provider option `connectionInfo.assumeExternalChanges` with
 * `datasetChanges.mode: EXTERNAL`.
 */
@SuppressWarnings("removal")
public class FeatureProviderSqlMigrationV5
    extends EntityMigration<FeatureProviderSqlData, FeatureProviderSqlData> {

  public FeatureProviderSqlMigrationV5(EntityMigrationContext context) {
    super(context);
  }

  @Override
  public String getSubject() {
    return "feature provider option connectionInfo.assumeExternalChanges";
  }

  @Override
  public String getDescription() {
    return "is deprecated and will be replaced by datasetChanges.mode: EXTERNAL";
  }

  @Override
  public boolean isApplicable(EntityData entityData, Optional<EntityData> defaults) {
    return entityData instanceof FeatureProviderSqlData
        && Objects.nonNull(((FeatureProviderSqlData) entityData).getConnectionInfo())
        && ((FeatureProviderSqlData) entityData).getConnectionInfo().getAssumeExternalChanges();
  }

  @Override
  public FeatureProviderSqlData migrate(
      FeatureProviderSqlData entityData, Optional<FeatureProviderSqlData> defaults) {
    return new ImmutableFeatureProviderSqlData.Builder()
        .from(entityData)
        .connectionInfo(
            new ImmutableConnectionInfoSql.Builder()
                .from(entityData.getConnectionInfo())
                .assumeExternalChanges(false)
                .build())
        .datasetChanges(
            Objects.nonNull(entityData.getDatasetChanges())
                ? entityData.getDatasetChanges()
                : new ImmutableDatasetChangeSettings.Builder()
                    .mode(DatasetChangeMode.EXTERNAL)
                    .build())
        .build();
  }
}
