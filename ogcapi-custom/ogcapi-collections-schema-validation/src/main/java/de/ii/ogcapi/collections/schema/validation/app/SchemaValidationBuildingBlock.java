/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.validation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Feature Collections - Schema Validation
 * @langEn JSON Schema profiles for GeoJSON with or without the JSON-FG extensions and for the
 *     returnables and receivables representations of features.
 * @langDe JSON-Schema-Profile für GeoJSON (mit oder ohne JSON-FG-Erweiterungen) und für die
 *     Darstellungen von Objekten (bei Antworten auf Queries und für CRUD-Operationen).
 * @scopeEn This building block defines four profiles for the JSON Schema resources of a collection
 *     to support validation of GeoJSON and JSON-FG features.
 *     <p>The profiles are: <code>
 * - "validation-returnables-geojson": JSON Schema for validating a GeoJSON feature that is returned as a response to a query
 * - "validation-receivables-geojson": JSON Schema for validating a GeoJSON feature that is sent to the server as part of a create or update operation
 * - "validation-returnables-jsonfg": JSON Schema for validating JSON-FG features that a returned as a response to a query
 * - "validation-receivables-jsonfg": JSON Schema for validating a JSON-FG feature that is sent to the server as part of a create or update operation
 * </code>
 * @scopeDe Dieser Baustein definiert vier Profile für die JSON-Schema-Ressourcen einer Sammlung, um
 *     die Validierung von GeoJSON- und JSON-FG-Features zu unterstützen.
 *     <p>Die Profile sind: <code>
 * - "validation-returnables-geojson": JSON-Schema zur Validierung eines GeoJSON-Features, das als Antwort auf eine Query zurückgegeben wird
 * - "validation-receivables-geojson": JSON-Schema zur Validierung eines GeoJSON-Features, das im Rahmen einer Create- oder Update-Operation an den Server gesendet wird
 * - "validation-returnables-jsonfg": JSON-Schema zur Validierung eines JSON-FG-Features, das als Antwort auf eine Query zurückgegeben wird
 * - "validation-receivables-jsonfg": JSON-Schema zur Validierung eines JSON-FG-Features, das im Rahmen einer Create- oder Update-Operation an den Server gesendet wird
 * </code>
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @ref:cfg {@link SchemaValidationConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.collections.schema.validation.app.ImmutableSchemaValidationConfiguration}
 */
@Singleton
@AutoBind
public class SchemaValidationBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/23-058r1.html",
              "OGC API - Features - Part 5: Schemas (DRAFT)"));

  @Inject
  public SchemaValidationBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableSchemaValidationConfiguration.Builder().enabled(false).build();
  }
}
