/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.schema.domain.ImmutableSchemaConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Feature Collections - Schema
 * @langEn Publish a logical schema of the feature properties, described in JSON Schema.
 * @langDe Veröffentlichen eines logisches Schema der Feature-Eigenschaften, beschrieben in JSON
 *     Schema.
 * @scopeEn The schema is automatically derived from the type definitions in the feature provider.
 *     Currently, schemas are created according to JSON Schema 2020-12.
 * @scopeDe Das Schema wird aus den Schema-Informationen im Feature-Provider abgeleitet. Aktuell
 *     wird JSON Schema 2020-12 unterstützt.
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @conformanceEn *Feature Collections - Schema* is based on the [OGC API - Features - Part 5:
 *     Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html).
 * @conformanceDe Der Baustein basiert auf dem [Entwurf für OGC API - Features - Part 5:
 *     Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html).
 * @ref:cfg {@link de.ii.ogcapi.collections.schema.domain.SchemaConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.collections.schema.domain.ImmutableSchemaConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.collections.schema.infra.EndpointSchema},
 * @ref:queryParameters {@link de.ii.ogcapi.collections.schema.domain.QueryParameterFSchema}
 * @ref:pathParameters {@link
 *     de.ii.ogcapi.collections.schema.domain.PathParameterCollectionIdSchema}
 */
@Singleton
@AutoBind
public class SchemaBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/23-058r1.html",
              "OGC API - Features - Part 5: Schemas (DRAFT)"));

  @Inject
  public SchemaBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder()
        .enabled(false)
        .defaultProfiles(Map.of("codelist", "codelists-ref"))
        .build();
  }
}
