/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.app;

import static de.ii.ogcapi.foundation.domain.ApiMediaType.HTML_MEDIA_TYPE;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.schema.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
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
  public ApiMediaType getMediaType() {
    return HTML_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return HTML_CONTENT;
  }

  @Override
  public Object getEntity(
      JsonSchemaDocument schema,
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

    return new ImmutableSchemaView.Builder()
        .title(schema.getTitle().orElse("Schema for " + collectionId))
        .description(schema.getDescription().orElse("No description found"))
        .apiData(api.getData())
        .collectionId(collectionId)
        .schemaCollectionProperties(schema)
        .rawLinks(links)
        .urlPrefix(requestContext.getStaticUrlPrefix())
        .htmlConfig(htmlConfig)
        // .noIndex(isNoIndexEnabledForApi(api.getData()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .i18n(i18n)
        .language(requestContext.getLanguage())
        .user(requestContext.getUser())
        .build();
  }
}
