/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.codelists.domain.CodelistsConfiguration;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaExtension;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.services.domain.ServicesContext;
import java.net.URI;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class CodelistsInSchema implements JsonSchemaExtension {

  private final URI serviceUri;

  @Inject
  public CodelistsInSchema(ServicesContext servicesContext) {
    this.serviceUri = servicesContext.getUri();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CodelistsConfiguration.class;
  }

  @Override
  public int getPriority() {
    return 20;
  }

  @Override
  public JsonSchema process(
      JsonSchema jsonSchema,
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      String collectionId,
      List<Profile> profiles) {
    return jsonSchema.accept(new WithCodelistUri(serviceUri, apiData));
  }
}
