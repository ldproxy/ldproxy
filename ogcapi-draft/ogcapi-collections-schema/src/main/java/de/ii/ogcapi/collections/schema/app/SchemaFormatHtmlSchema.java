/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.schema.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.JsonSchemaObject;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class SchemaFormatHtmlSchema implements SchemaFormatExtension {

  private final I18n i18n;

  @Inject
  public SchemaFormatHtmlSchema(I18n i18n) {
    this.i18n = i18n;
  }

  static final ApiMediaType HTML_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.TEXT_HTML_TYPE)
          .label("HTML")
          .parameter("html")
          .fileExtension("html")
          .build();

  static final ApiMediaTypeContent HTML_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new ObjectSchema())
          .schemaRef("#/components/schemas/HTMLSchema")
          .ogcApiMediaType(HTML_MEDIA_TYPE)
          .build();

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
      JsonSchemaObject schema,
      List<Link> links,
      String collectionId,
      OgcApi api,
      ApiRequestContext requestContext) {
    String test =
        "<html><head><title>Schema</title></head><body><h1>Schema for collection: "
            + collectionId
            + "</h1><pre>"
            + schema.toString() // or format your schema nicely here
            + "</pre></body></html>";

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
