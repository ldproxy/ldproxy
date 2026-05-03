/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterDryRun;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * @title dry-run
 * @endpoints Stored Query
 * @langEn Set to `true` to only validate the request, but not alter the stored query.
 * @langDe Bei `true` wird die Anfrage nur validiert, die gespeicherte Abfrage wird nicht geändert.
 */
@Singleton
@AutoBind
public class QueryParameterDryRunStoredQueriesManager extends QueryParameterDryRun {

  @Inject
  QueryParameterDryRunStoredQueriesManager(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "dryRunStoredQueriesManager";
  }

  @Override
  public String getDescription() {
    return "'true' just validates the stored query without creating or updating a stored query; "
        + "returns 400, if validation fails, otherwise 204.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/search/{queryId}".equals(definitionPath);
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () -> isEnabledForApi(apiData) && method == HttpMethods.PUT && matchesPath(definitionPath));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(
            apiData, SearchConfiguration.class, SearchConfiguration::isManagerEnabled)
        && isExtensionEnabled(
            apiData, SearchConfiguration.class, SearchConfiguration::isValidationEnabled);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return SearchBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return SearchBuildingBlock.SPEC;
  }
}
