/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.text.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.text.search.domain.ImmutableTextSearchConfiguration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Text Search
 * @langEn General text search in multiple text properties of the data.
 * @langDe Generelle Textsuche über mehrere Properties der Daten.
 * @conformanceEn *Text Search* implements the [draft of OGC API - Features - Part 1: Text
 *     Search](https://docs.ogc.org/DRAFTS/24-031.html).
 * @conformanceDe Der Baustein implementiert den [Entwurf von OGC API - Features - Part 9: Text
 *     Search](https://docs.ogc.org/DRAFTS/24-031.html).
 * @ref:cfg {@link de.ii.ogcapi.text.search.domain.TextSearchConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.text.search.domain.ImmutableTextSearchConfiguration}
 * @ref:queryParameters {@link de.ii.ogcapi.text.search.app.QueryParameterQ}
 */
@Singleton
@AutoBind
public class TextSearchBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/24-031.html",
              "OGC API - Features - Part 9: Text Search (DRAFT)"));

  @Inject
  public TextSearchBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableTextSearchConfiguration.Builder().enabled(false).build();
  }
}
