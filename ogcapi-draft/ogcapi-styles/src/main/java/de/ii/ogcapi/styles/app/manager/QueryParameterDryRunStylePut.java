/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app.manager;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterDryRun;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import java.util.Objects;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title dry-run
 * @endpoints Style, Collection Style
 * @langEn If set to 'true', the operation just validates the content without creating a new style
 *     or updating an existing style.
 * @langDe Bei `true` wird der Inhalt lediglich überprüft, ohne dass ein neuer Style erstellt oder
 *     ein bestehender Style aktualisiert wird.
 */
@Singleton
@AutoBind
public class QueryParameterDryRunStylePut extends QueryParameterDryRun {

  @Inject
  QueryParameterDryRunStylePut(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "dryRunStylesPut";
  }

  @Override
  public String getDescription() {
    return "'true' just validates the style without creating a new style or updating an existing style "
        + "and returns 400, if validation fails, otherwise 204.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.endsWith("/styles/{styleId}");
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
            apiData, StylesConfiguration.class, StylesConfiguration::isManagerEnabled)
        && isExtensionEnabled(
            apiData, StylesConfiguration.class, StylesConfiguration::isValidationEnabled);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    return Objects.nonNull(collectionData)
        && isExtensionEnabled(
            collectionData, StylesConfiguration.class, StylesConfiguration::isManagerEnabled)
        && isExtensionEnabled(
            collectionData, StylesConfiguration.class, StylesConfiguration::isValidationEnabled);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return StylesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return StylesBuildingBlock.SPEC;
  }
}
