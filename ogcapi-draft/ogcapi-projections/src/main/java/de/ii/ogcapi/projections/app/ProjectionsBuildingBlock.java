/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.projections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.projections.app.ImmutableProjectionsConfiguration.Builder;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Projections
 * @langEn Select the feature properties included in the feature response.
 * @langDe Auswahl der Feature-Eigenschaften in Rückgaben.
 * @conformanceEn *Projections* is based on the [draft of OGC API Features Part 6: Property
 *     Selection](https://docs.ogc.org/DRAFTS/24-019.html).
 * @conformanceDe Der Baustein basiert auf dem [Entwurf für OGC API Features Part 6: Property
 *     Selection](https://docs.ogc.org/DRAFTS/24-019.html).
 * @ref:cfg {@link de.ii.ogcapi.projections.app.ProjectionsConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.projections.app.ImmutableProjectionsConfiguration}
 * @ref:queryParameters {@link de.ii.ogcapi.projections.app.QueryParameterProperties}, {@link
 *     de.ii.ogcapi.projections.app.QueryParameterExcludeProperties}, {@link
 *     de.ii.ogcapi.projections.app.QueryParameterSkipGeometry}
 */
@Singleton
@AutoBind
public class ProjectionsBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/24-019.html",
              "OGC API - Features - Part 6: Property Selection"));

  @Inject
  public ProjectionsBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).build();
  }
}
