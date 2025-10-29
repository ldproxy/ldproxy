/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import static de.ii.ogcapi.foundation.domain.ApiMediaType.HTML_MEDIA_TYPE;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class SchemaFormatHtmlSchema implements SchemaFormatExtension {

  private final I18n i18n;

  @Inject
  public SchemaFormatHtmlSchema(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return SchemaFormatExtension.super.isEnabledForApi(apiData)
        && apiData
            .getExtension(HtmlConfiguration.class)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return SchemaFormatExtension.super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(HtmlConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public ApiMediaType getMediaType() {
    return HTML_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return HTML_CONTENT;
  }

  private boolean isNoIndexEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getNoIndexEnabled)
        .orElse(true);
  }

  @Override
  public Object getEntity(
      JsonSchemaDocument schema,
      SchemaType type,
      List<Link> links,
      String collectionId,
      OgcApi api,
      ApiRequestContext requestContext) {

    HtmlConfiguration htmlConfig =
        api.getData()
            .getCollections()
            .get(collectionId)
            .getExtension(HtmlConfiguration.class)
            .orElse(null);

    String typeTitle = i18n.get(type.toString() + "Title", requestContext.getLanguage());
    String typeDescription =
        i18n.get(type.toString() + "Description", requestContext.getLanguage());

    return new ImmutableSchemaView.Builder()
        .title(schema.getTitle().orElse(collectionId))
        .description(schema.getDescription().orElse(""))
        .typeLabel(typeTitle)
        .typeDescription(typeDescription)
        .apiData(api.getData())
        .collectionId(collectionId)
        .schema(schema)
        .type(type)
        .rawLinks(links)
        .basePath(requestContext.getBasePath())
        .apiPath(requestContext.getApiPath())
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .i18n(i18n)
        .language(requestContext.getLanguage())
        .user(requestContext.getUser())
        .build();
  }
}
