/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.docs.DocIgnore;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@DocIgnore
@Singleton
@AutoBind
public class QueryParameterTilesetVersion extends OgcApiQueryParameterBase {

  private final SchemaValidator schemaValidator;

  @Inject
  QueryParameterTilesetVersion(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId() {
    return "tileset3dVersion";
  }

  @Override
  public String getName() {
    return "v";
  }

  @Override
  public String getDescription() {
    return "If a 'tilesetVersion' is set, this will be added to requests by clients.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.endsWith("/3dtiles/{subPath}");
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }
}
