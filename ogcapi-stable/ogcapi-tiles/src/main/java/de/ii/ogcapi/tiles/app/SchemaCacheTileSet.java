/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument.VERSION;
import de.ii.ogcapi.features.core.domain.SchemaDeriverFeatures;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

class SchemaCacheTileSet extends JsonSchemaCache {

  private static final String DEFAULT_FLATTENING_SEPARATOR = ".";

  private final Supplier<Map<String, Codelist>> codelistSupplier;

  SchemaCacheTileSet(Supplier<Map<String, Codelist>> codelistSupplier) {
    this.codelistSupplier = codelistSupplier;
  }

  @Override
  protected JsonSchemaDocument deriveSchema(
      FeatureSchema schema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      List<Profile> profiles,
      Optional<String> schemaUri,
      VERSION version) {

    SchemaDeriverFeatures schemaDeriverFeatures =
        new SchemaDeriverFeatures(
            version,
            schemaUri,
            collectionData.getLabel(),
            Optional.empty(),
            codelistSupplier.get());

    return (JsonSchemaDocument) schema.accept(schemaDeriverFeatures);
  }
}
