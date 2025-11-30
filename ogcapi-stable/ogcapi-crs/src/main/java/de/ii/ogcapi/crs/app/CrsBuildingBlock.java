/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.ImmutableCrsConfiguration.Builder;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title CRS
 * @langEn Additional coordinate reference systems.
 * @langDe Zusätzliche Koordinatenreferenzsysteme.
 * @scopeEn The building block *CRS* provides support for additional coordinate reference systems
 *     apart from the default [CRS84](http://www.opengis.net/def/crs/OGC/1.3/CRS84) (WGS 84).
 *     <p>All transformations between two coordinate reference systems are handled by *PROJ*. If
 *     multiple transformations are available, *PROJ* decides which one to use.
 * @scopeDe Der Baustein *CRS* ergänzt die Unterstützung für weitere Koordinatenreferenzsysteme
 *     neben dem Standard-Koordinatenreferenzsystem
 *     [CRS84](http://www.opengis.net/def/crs/OGC/1.3/CRS84) (WGS 84).
 *     <p>Alle Koordinatentransformationen zwischen zwei Koordinatenreferenzsystemen erfolgen mit
 *     *PROJ*. *PROJ* entscheidet, welche Transformation verwendet wird, sofern mehrere verfügbar
 *     sind.
 * @cfgFilesEn PROJ grid shift files have to be placed in the resource store in the directory
 *     `proj`.
 * @cfgFilesDe PROJ-Grid-Shift-Dateien müssen im Ressourcen-Store im Verzeichnis `proj` abgelegt
 *     werden.
 * @conformanceEn *CRS* implements all requirements of conformance class *Coordinate Reference
 *     System by Reference* of [OGC API - Features - Part 2: Coordinate Reference System by *
 *     Reference 1.0.1](https://docs.ogc.org/is/18-058r1/18-058r1.html).
 * @conformanceDe Der Baustein implementiert alle Vorgaben der Konformitätsklasse "Coordinate
 *     Reference System by Reference" von [OGC API - Features - Part 2: Coordinate Reference System
 *     by Reference 1.0.1](https://docs.ogc.org/is/18-058r1/18-058r1.html).
 * @ref:cfg {@link de.ii.ogcapi.crs.domain.CrsConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.crs.domain.ImmutableCrsConfiguration}
 * @ref:queryParameters {@link de.ii.ogcapi.crs.app.QueryParameterCrsFeatures}, {@link
 *     de.ii.ogcapi.crs.app.QueryParameterBboxCrsFeatures}
 */
@Singleton
@AutoBind
public class CrsBuildingBlock implements ApiBuildingBlock, ApiExtensionHealth {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/18-058r1/18-058r1.html",
              "OGC API - Features - Part 2: Coordinate Reference Systems by Reference"));

  private final CrsTransformerFactory crsTransformerFactory;
  private final FeaturesCoreProviders featuresCoreProviders;

  @Inject
  public CrsBuildingBlock(
      CrsTransformerFactory crsTransformerFactory, FeaturesCoreProviders featuresCoreProviders) {
    this.crsTransformerFactory = crsTransformerFactory;
    this.featuresCoreProviders = featuresCoreProviders;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(true).suppressGlobalCrsList(false).build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    Optional<CrsConfiguration> crsConfiguration =
        api.getData()
            .getExtension(CrsConfiguration.class)
            .filter(ExtensionConfiguration::isEnabled);

    if (crsConfiguration.isEmpty()) {
      return ValidationResult.of();
    }

    EpsgCrs defaultCrs =
        api.getData()
            .getExtension(FeaturesCoreConfiguration.class)
            .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
            .orElse(OgcCrs.CRS84);
    EpsgCrs providerCrs =
        featuresCoreProviders
            .getFeatureProvider(api.getData())
            .flatMap(featureProvider2 -> featureProvider2.info().getCrs())
            .orElse(OgcCrs.CRS84);

    EpsgCrs lastCrs = null;
    try {
      lastCrs = defaultCrs;
      crsTransformerFactory.getTransformer(providerCrs, defaultCrs);

      for (EpsgCrs crs : crsConfiguration.get().getAdditionalCrs()) {
        lastCrs = crs;
        crsTransformerFactory.getTransformer(providerCrs, crs);
      }
    } catch (Throwable e) {
      return ImmutableValidationResult.builder()
          .mode(apiValidation)
          .addErrors(
              String.format(
                  "Could not find transformation for %s -> %s: %s",
                  providerCrs.toHumanReadableString(),
                  lastCrs.toHumanReadableString(),
                  e.getMessage()))
          .build();
    }
    try {
      lastCrs = defaultCrs;
      crsTransformerFactory.getTransformer(defaultCrs, providerCrs);

      for (EpsgCrs crs : crsConfiguration.get().getAdditionalCrs()) {
        lastCrs = crs;
        crsTransformerFactory.getTransformer(crs, providerCrs);
      }
    } catch (Throwable e) {
      return ImmutableValidationResult.builder()
          .mode(apiValidation)
          .addErrors(
              String.format(
                  "Could not find transformation for %s -> %s: %s",
                  lastCrs.toHumanReadableString(),
                  providerCrs.toHumanReadableString(),
                  e.getMessage()))
          .build();
    }

    return ValidationResult.of();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(crsTransformerFactory);
  }
}
