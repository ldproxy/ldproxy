/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * The {@code Prefer} header on the query execution endpoints ({@code POST /search} and {@code GET
 * /search/{queryId}}), declared only when asynchronous execution is enabled; without it, no
 * preference is honored on these endpoints.
 */
@Singleton
@AutoBind
public class HeaderPreferSearch extends HeaderPrefer {

  private final Schema<?> schema = new StringSchema().example("respond-async, wait=10");

  @Inject
  HeaderPreferSearch(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "PreferSearch";
  }

  @Override
  public String getDescription() {
    return "Controls how the server executes the query. Multiple preferences are separated by "
        + "commas. 'respond-async' executes the query asynchronously as a job; the response is "
        + "202 Accepted with a Location header that points to the job resource. 'wait' (a "
        + "non-negative integer, in seconds), in combination with 'respond-async', delays the "
        + "response for up to the requested number of seconds (the server may lower the "
        + "effective wait period); if the job completes in time, the query result is returned "
        + "synchronously instead of 202. Other preferences are ignored.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData)
                && ((method == HttpMethods.POST && "/search".equals(definitionPath))
                    || (method == HttpMethods.GET && "/search/{queryId}".equals(definitionPath))));
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(apiData, SearchConfiguration.class, SearchConfiguration::isAsync);
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
