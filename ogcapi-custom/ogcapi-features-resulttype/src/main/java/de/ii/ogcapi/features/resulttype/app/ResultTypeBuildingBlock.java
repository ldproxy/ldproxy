/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.resulttype.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.resulttype.domain.ImmutableResultTypeConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - Result Type
 * @langEn Adds a query parameter to return only the number of features matched by a request.
 * @langDe Ergänzt einen Query-Parameter sodass nur die Anzahl der Features, die von einer Anfrage
 *     selektiert werden, zurückgegeben wird.
 * @ref:cfg {@link de.ii.ogcapi.features.resulttype.domain.ResultTypeConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.features.resulttype.domain.ImmutableResultTypeConfiguration}
 * @ref:queryParameters {@link
 *     de.ii.ogcapi.features.resulttype.app.QueryParameterResultTypeFeatures},
 */
@Singleton
@AutoBind
public class ResultTypeBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public ResultTypeBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableResultTypeConfiguration.Builder().enabled(false).build();
  }
}
