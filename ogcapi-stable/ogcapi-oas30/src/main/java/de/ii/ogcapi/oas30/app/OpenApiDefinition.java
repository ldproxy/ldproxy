/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.oas30.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.oas30.domain.Oas30Configuration;
import de.ii.ogcapi.oas30.domain.OpenApiExtension;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.Comparator;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class OpenApiDefinition implements OpenApiExtension {

  private final ExtensionRegistry extensionRegistry;

  @Inject
  public OpenApiDefinition(ExtensionRegistry extensionRegistry) {
    this.extensionRegistry = extensionRegistry;
  }

  @Override
  public int getSortPriority() {
    return 0;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Oas30Configuration.class;
  }

  @Override
  public OpenAPI process(OpenAPI openAPI, OgcApiDataV2 apiData) {
    if (apiData != null) {

      extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
          .filter(endpoint -> endpoint.isEnabledForApi(apiData))
          .map(endpoint -> endpoint.getDefinition(apiData))
          .filter(def -> !def.getHidden())
          .sorted(Comparator.comparing(ApiEndpointDefinition::getSortPriority))
          .forEachOrdered(
              ogcApiEndpointDefinition -> {
                ogcApiEndpointDefinition.updateOpenApiDefinition(apiData, openAPI);
              });
    }

    return openAPI;
  }
}
