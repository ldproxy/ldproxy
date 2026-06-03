/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.crs.domain.HeaderContentCrs;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

@Singleton
@AutoBind
public class HeaderContentCrsCrud extends HeaderContentCrs {

  @Inject
  HeaderContentCrsCrud(SchemaValidator schemaValidator, CrsSupport crsSupport) {
    super(schemaValidator, crsSupport);
  }

  @Override
  public String getId() {
    return "ContentCrsCrudFeature";
  }

  @Override
  public String getDescription() {
    return "The coordinate reference system of coordinates in the request.";
  }

  @Override
  public boolean isRequestHeader() {
    return true;
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData)
                && ((method == HttpMethods.POST && definitionPath.endsWith("/items"))
                    || (method == HttpMethods.PUT
                        && definitionPath.endsWith("/items/{featureId}"))));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(apiData, CrudConfiguration.class)
        && isExtensionEnabled(apiData, CrsConfiguration.class);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return CrudBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return CrudBuildingBlock.SPEC;
  }
}
