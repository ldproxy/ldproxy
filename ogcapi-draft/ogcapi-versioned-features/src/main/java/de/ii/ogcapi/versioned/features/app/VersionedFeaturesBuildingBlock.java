/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration.Builder;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @title Versioned Features
 * @langEn Per-feature version selection, version history, and immutable mementoes.
 * @langDe Versionsauswahl pro Feature, Versionshistorie und unveränderliche Mementoes.
 * @scopeEn Adds version-selection semantics to the existing Features resources and provides a Time
 *     Map resource. The `datetime` query parameter is reinterpreted to select the version of each
 *     feature that intersects the supplied instant or interval; when omitted, the current version
 *     is returned. Per-version responses carry a `Memento-Datetime` header and the link relations
 *     defined by RFC 7089 ({@code predecessor-version}, {@code successor-version}, {@code
 *     latest-version}, {@code version-history}, {@code timemap}, {@code memento}, {@code
 *     original}). Combined with the Transactions and CRUD building blocks, mutations create new
 *     versions (or retire existing ones) instead of overwriting rows in place.
 * @scopeDe Erweitert die vorhandenen Features-Ressourcen um Versionsauswahl-Semantik und stellt
 *     eine Time-Map-Ressource bereit. Der Anfrageparameter `datetime` selektiert die Version jedes
 *     Features, die den angegebenen Zeitpunkt oder das Intervall schneidet; ohne Angabe wird die
 *     aktuelle Version geliefert. Antworten zu einzelnen Versionen enthalten den Header
 *     `Memento-Datetime` sowie die in RFC 7089 definierten Linkrelationen ({@code
 *     predecessor-version}, {@code successor-version}, {@code latest-version}, {@code
 *     version-history}, {@code timemap}, {@code memento}, {@code original}). Zusammen mit den
 *     Bausteinen Transactions und CRUD erzeugen Mutationen neue Versionen (bzw. stellen vorhandene
 *     still), statt Zeilen in der Datenbank zu überschreiben.
 * @ref:cfg {@link de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration}
 */
@Singleton
@AutoBind
public class VersionedFeaturesBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://github.com/opengeospatial/ogcapi-features/tree/master/proposals/versioned-features.md",
              "OGC API - Features: Versioned Features (PROPOSAL)"));

  private final FeaturesCoreProviders providers;

  @Inject
  public VersionedFeaturesBuildingBlock(FeaturesCoreProviders providers) {
    this.providers = providers;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = ApiBuildingBlock.super.onStartup(api, apiValidation);

    if (apiValidation == MODE.NONE) {
      return result;
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(api.getData());

    api.getData()
        .getCollections()
        .forEach(
            (collectionId, collectionData) -> {
              String featureTypeId =
                  collectionData
                      .getExtension(FeaturesCoreConfiguration.class)
                      .flatMap(FeaturesCoreConfiguration::getFeatureType)
                      .orElse(collectionId);
              FeatureSchema featureSchema = featureSchemas.get(featureTypeId);
              if (Objects.isNull(featureSchema)) {
                builder.addErrors(
                    String.format("Collection '%s' does not have a schema.", collectionId));
              } else {
                if (featureSchema.getPrimaryInterval().isEmpty()) {
                  builder.addStrictErrors(
                      String.format(
                          "Collection '%s' does not have a primary interval. Version Features requires a temporal interval.",
                          collectionId));
                }
              }
            });

    return builder.build();
  }
}
