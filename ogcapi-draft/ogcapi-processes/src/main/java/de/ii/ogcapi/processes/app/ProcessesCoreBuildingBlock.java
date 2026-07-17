/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.processes.domain.ImmutableProcessesCoreConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

// ToDo Docs
/**
 * @title PROCESSES_CORE
 * @langEn Docs WIP
 * @langDe Doku WIP
 */
@Singleton
@AutoBind
public class ProcessesCoreBuildingBlock implements ApiBuildingBlock, ConformanceClass {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/18-062r3.html", "OGC API - Processes - Part 1: Core"));

  @Inject
  public ProcessesCoreBuildingBlock() {}

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of(
        "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/core",
        "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/oas30",
        "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/ogc-process-description");
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProcessesCoreConfiguration.Builder().enabled(false).build();
  }
}
