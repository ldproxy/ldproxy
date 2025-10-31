/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FoundationConfiguration;
import de.ii.ogcapi.foundation.domain.HttpRequestOverrideQueryParameter;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;

/**
 * @title lang
 * @endpoints *
 * @langEn Select the language of the response. If no value is provided, the standard HTTP rules
 *     apply, i.e., the "Accept-Language" header will be used to determine the language.
 * @langDe Wählt die Sprache der Antwort. Wenn kein Wert angegeben wird, gelten die Standard-HTTP
 *     Regeln, d.h. der "Accept-Language"-Header wird zur Bestimmung der Sprache verwendet.
 */
@Singleton
@AutoBind
public class QueryParameterLang extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Locale>, HttpRequestOverrideQueryParameter {

  private Schema<?> schema = null;
  private final SchemaValidator schemaValidator;
  private final I18n i18n;

  @Inject
  public QueryParameterLang(SchemaValidator schemaValidator, I18n i18n) {
    this.schemaValidator = schemaValidator;
    this.i18n = i18n;
  }

  @Override
  public String getName() {
    return "lang";
  }

  @Override
  public Locale parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    return i18n.getLanguages().stream()
        .filter(lang -> Objects.equals(lang.getLanguage(), value))
        .findFirst()
        .orElse(null);
  }

  @Override
  public void applyTo(ContainerRequestContext requestContext, QueryParameterSet parameters) {
    if (parameters.getTypedValues().containsKey(getName())) {
      Locale value = (Locale) parameters.getTypedValues().get(getName());
      requestContext.getHeaders().putSingle("Accept-Language", value.toLanguageTag());
    }
  }

  @Override
  public String getDescription() {
    return "Select the language of the response. If no value is provided, the standard HTTP rules "
        + "apply, i.e., the accept-language header will be used to determine the format.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return true;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    if (schema == null) {
      schema =
          new StringSchema()
              ._enum(
                  i18n.getLanguages().stream()
                      .map(Locale::getLanguage)
                      .collect(Collectors.toList()));
    }
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(
        apiData, FoundationConfiguration.class, FoundationConfiguration::getUseLangParameter);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(FoundationConfiguration.class, collectionId)
            .map(FoundationConfiguration::getUseLangParameter)
            .orElse(false);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FoundationConfiguration.class;
  }
}
