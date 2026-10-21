/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.async.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.async.domain.ImmutableAsyncConfiguration;
import de.ii.ogcapi.common.domain.async.AsyncConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @title Async
 * @langEn This helper building block provides async job operations to other building blocks. It
 *     cannot be configured and is automatically enabled when needed.
 * @langDe Dieser Hilfsbaustein stellt anderen Bausteinen Operationen für asynchrone Jobs bereit. Er
 *     kann nicht konfiguriert werden und wird bei Bedarf automatisch aktiviert.
 * @ref:endpoints {@link de.ii.ogcapi.async.infra.EndpointJob}, {@link
 *     de.ii.ogcapi.async.infra.EndpointDismiss}, {@link de.ii.ogcapi.async.infra.EndpointResults},
 *     {@link de.ii.ogcapi.async.infra.EndpointResultsSpecific}, {@link
 *     de.ii.ogcapi.async.infra.EndpointResultsSpecificN}
 * @ref:pathParameters {@link de.ii.ogcapi.async.app.parameter.PathParameterJobId}, {@link
 *     de.ii.ogcapi.async.app.parameter.PathParameterOutputId}, {@link
 *     de.ii.ogcapi.async.app.parameter.PathParameterN}
 * @ref:queryParameters {@link de.ii.ogcapi.async.app.parameter.QueryParameterFStatusInfoAsync}
 */
@Singleton
@AutoBind
public class AsyncBuildingBlock implements ApiBuildingBlock, ConformanceClass {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/18-062r3.html", "OGC API - Processes - Part 1: Core"));

  public static boolean isAsyncEnabled(OgcApiDataV2 apiData) {
    // Automatically enable when any consumer is enabled
    return apiData.getMergedExtensions().stream()
        .filter(ExtensionConfiguration::isEnabled)
        .filter(extension -> extension instanceof AsyncConfiguration)
        .map(extension -> (AsyncConfiguration) extension)
        .anyMatch(asyncConfig -> Objects.equals(asyncConfig.getAsync(), Boolean.TRUE));
  }

  @Inject
  public AsyncBuildingBlock() {}

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isAsyncEnabled(apiData);
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableAsyncConfiguration.Builder().build();
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/callback");
  }
}
