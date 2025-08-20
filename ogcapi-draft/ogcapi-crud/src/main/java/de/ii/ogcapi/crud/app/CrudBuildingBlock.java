/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.crud.app.ImmutableCrudConfiguration.Builder;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title CRUD
 * @langEn Create, replace, update and delete features.
 * @langDe Erzeugen, Ersetzen, Aktualisieren und Löschen von Features.
 * @limitationsEn Only feature types from an SQL feature provider with `dialect` `PGIS` and
 *     `datasetChanges.mode` `CRUD` are supported.
 * @limitationsDe Es werden nur Objektarten von einem SQL-Feature-Provider mit `dialect` `PGIS` und
 *     `datasetChanges.mode` `CRUD` unterstützt.
 * @conformanceEn The building block is based on the specifications of the conformance classes
 *     "Create/Replace/Delete" and "Features" from the [Draft OGC API - Features - Part 4: Create,
 *     Replace, Update and Delete](https://docs.ogc.org/DRAFTS/20-002r1.html). The implementation
 *     will change as the draft will evolve during the standardization process.
 * @conformanceDe Der Baustein basiert auf den Vorgaben der Konformitätsklassen
 *     "Create/Replace/Delete" und "Features" aus dem [Entwurf von OGC API - Features - Part 4:
 *     Create, Replace, Update and Delete](https://docs.ogc.org/DRAFTS/20-002r1.html). Die
 *     Implementierung wird sich im Zuge der weiteren Standardisierung der Spezifikation noch
 *     ändern.
 * @ref:cfg {@link de.ii.ogcapi.crud.app.CrudConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.crud.app.ImmutableCrudConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.crud.app.EndpointCrud}
 * @ref:pathParameters {@link de.ii.ogcapi.features.core.domain.PathParameterCollectionIdFeatures}
 */
@Singleton
@AutoBind
public class CrudBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/20-002r1.html",
              "OGC API - Features - Part 4: Create, Replace, Update and Delete (DRAFT)"));

  private final FeaturesCoreProviders providers;

  @Inject
  public CrudBuildingBlock(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isProviderSupportsMutations(apiData) && ApiBuildingBlock.super.isEnabledForApi(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return isProviderSupportsMutations(apiData)
        && ApiBuildingBlock.super.isEnabledForApi(apiData, collectionId);
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).optimisticLockingLastModified(false).build();
  }

  private boolean isProviderSupportsMutations(OgcApiDataV2 apiData) {
    return providers
        .getFeatureProvider(apiData)
        .filter(provider -> provider.mutations().isSupported())
        .isPresent();
  }
}
