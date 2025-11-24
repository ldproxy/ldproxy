/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.oas30.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.oas30.domain.ImmutableOas30Configuration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title OpenAPI 3.0
 * @langEn Publish OpenAPI definitions.
 * @langDe Veröffentlichung von OpenAPI-Definitionen.
 * @conformanceEn *OpenAPI 3.0* implements all requirements of conformance class *OpenAPI 3.0* from
 *     [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_oas30) for the mentioned resource.
 * @conformanceDe *OpenAPI 3.0* implementiert alle Vorgaben der gleichnamigen Konformitätsklasse von
 *     [OGC API - Features - Part 1: Core
 *     1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_oas30).
 * @ref:cfg {@link de.ii.ogcapi.oas30.domain.Oas30Configuration}
 * @ref:cfgProperties {@link de.ii.ogcapi.oas30.domain.ImmutableOas30Configuration}
 */
@Singleton
@AutoBind
public class OpenApiBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/17-069r4/17-069r4.html",
              "OGC API - Features - Part 1: Core"));

  @Inject
  public OpenApiBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableOas30Configuration.Builder()
        .enabled(true)
        .showSchemaConstraintsInHtml(false)
        .build();
  }
}
