/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.resources.domain.ImmutableResourcesConfiguration.Builder;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Resources
 * @langEn Publish and manage file resources.
 * @langDe Bereitstellung und Verwaltung von Datei-Ressourcen.
 * @scopeEn Typical file resources are resources referenced from styles (icons, sprites) or schema
 *     documents referenced from feature data. The files are located in the folder
 *     `api-resources/resources/{apiId}`.
 * @scopeDe Typische Dateiressourcen sind Ressourcen, die von Styles referenziert werden (Icons,
 *     Sprites) oder Schemadokumente, die aus Daten referenziert werden. Die Dateien befinden sich
 *     im Ordner `api-resources/resources/{apiId}`.
 * @cfgFilesEn Resource files are placed in the resource store in the directory
 *     `api-resources/{apiId}/`. Sub-directories are supported.
 * @cfgFilesDe Ressourcendateien werden im Ressourcen-Store im Verzeichnis `api-resources/{apiId}/`
 *     abgelegt. Unterverzeichnisse werden unterstützt.
 * @ref:cfg {@link de.ii.ogcapi.resources.domain.ResourcesConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.resources.domain.ImmutableResourcesConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.resources.infra.EndpointResources}, {@link
 *     de.ii.ogcapi.resources.infra.EndpointResource}, {@link
 *     de.ii.ogcapi.resources.infra.EndpointResourcesManager}
 * @ref:queryParameters {@link de.ii.ogcapi.resources.app.QueryParameterFResources}
 * @ref:pathParameters {@link de.ii.ogcapi.resources.app.PathParameterResourceId}
 */
@Singleton
@AutoBind
public class ResourcesBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();
  public static final String STORE_RESOURCE_TYPE = "api-resources";

  @Inject
  public ResourcesBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).managerEnabled(false).build();
  }
}
