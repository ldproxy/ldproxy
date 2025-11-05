/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.mcp.domain.ImmutableMcpConfiguration;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class McpBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public McpBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableMcpConfiguration.Builder().enabled(false).build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    return ApiBuildingBlock.super.onStartup(api, apiValidation);
  }
}
