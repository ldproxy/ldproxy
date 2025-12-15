/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.codelists.domain.ImmutableCodelistsConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Codelists
 * @langEn Publish codelists associated with feature properties.
 * @langDe Veröffentlichung von Codelisten, die mit Feature-Eigenschaften verbunden sind.
 * @scopeEn If the schema of a feature type includes "codelist"
 *     [constraints](../../../providers/details/constraints.md) and this building block is enabled,
 *     the codelists can be accessed via the API in HTML and JSON.
 * @scopeDe Wenn das Schema einer Objektart
 *     "codelist"-[Constraints](../../../providers/details/constraints.md) enthält und dieser
 *     Baustein aktiviert ist, kann auf die Codelisten über die API in HTML und JSON zugegriffen
 *     werden.
 * @cfgFilesEn Codelists are placed in the value store. See
 *     [Codelists](../../auxiliaries/codelists.md#storage) for details.
 * @cfgFilesDe Codelisten werden im Value-Store abgelegt. Siehe
 *     [Codelists](../../auxiliaries/codelists.md#speicherung) für Details.
 * @ref:cfg {@link de.ii.ogcapi.codelists.domain.CodelistsConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.codelists.domain.ImmutableCodelistsConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.codelists.infra.EndpointCodelists}, {@link
 *     de.ii.ogcapi.codelists.infra.EndpointCodelist}
 * @ref:queryParameters {@link QueryParameterFCodelists}, {@link QueryParameterFCodelist}
 * @ref:pathParameters {@link PathParameterCodelistId}
 */
@Singleton
@AutoBind
public class CodelistsBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public CodelistsBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableCodelistsConfiguration.Builder().enabled(false).build();
  }
}
