/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import de.ii.ogcapi.features.core.domain.DeprecatedExtents;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityMigration;
import java.util.Optional;

public class ExtentMigrationV5 extends EntityMigration<OgcApiDataV2, OgcApiDataV2> {

  public ExtentMigrationV5(EntityMigrationContext context) {
    super(context);
  }

  @Override
  public String getSubject() {
    return "options defaultExtent and extent of collections";
  }

  @Override
  public String getDescription() {
    return "are deprecated and will be replaced by the option extent of the building block FEATURES_CORE of the API or the collection";
  }

  @Override
  public boolean isApplicable(EntityData entityData, Optional<EntityData> defaults) {
    return entityData instanceof OgcApiDataV2 apiData && DeprecatedExtents.isPresent(apiData);
  }

  @Override
  public OgcApiDataV2 migrate(OgcApiDataV2 entityData, Optional<OgcApiDataV2> defaults) {
    return DeprecatedExtents.apply(entityData, true);
  }
}
