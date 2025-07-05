/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.app;

import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument.VERSION;
import de.ii.ogcapi.features.core.domain.SchemaDeriverFeatures;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.transform.WithScope;
import de.ii.xtraplatform.features.domain.transform.WithTransformationsApplied;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class SchemaCacheFeatures extends JsonSchemaCache {

  private final Supplier<Map<String, Codelist>> codelistSupplier;
  private static final WithTransformationsApplied WITH_TRANSFORMATIONS_APPLIED =
      new WithTransformationsApplied();
  private static final WithScope WITH_SCOPE_SCHEMA =
      new WithScope(EnumSet.of(SchemaBase.Scope.RETURNABLE, SchemaBase.Scope.RECEIVABLE));

  public SchemaCacheFeatures(Supplier<Map<String, Codelist>> codelistSupplier) {
    super();
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

    return (JsonSchemaDocument)
        schema
            .accept(WITH_SCOPE_SCHEMA)
            .accept(WITH_TRANSFORMATIONS_APPLIED)
            .accept(schemaDeriverFeatures);
  }
}
