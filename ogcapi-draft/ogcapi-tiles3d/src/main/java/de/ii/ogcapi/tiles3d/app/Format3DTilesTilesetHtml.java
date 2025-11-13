/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.StyleReader;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesTileset;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import de.ii.xtraplatform.web.domain.URICustomizer;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class Format3DTilesTilesetHtml implements Format3dTilesTileset {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.TEXT_HTML_TYPE)
          .label("HTML")
          .parameter("html")
          .build();

  private static final String SCHEMA_REF = "#/components/schemas/htmlSchema";
  private final Schema<?> schema;
  private final URI servicesUri;
  private final StyleReader styleReader;

  @Inject
  public Format3DTilesTilesetHtml(ServicesContext servicesContext, StyleReader styleReader) {
    this.servicesUri = servicesContext.getUri();
    this.styleReader = styleReader;
    this.schema = new StringSchema().example("<html>...</html>");
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return Format3dTilesTileset.super.isEnabledForApi(apiData)
        && apiData
            .getExtension(Tiles3dConfiguration.class)
            .filter(
                config ->
                    config.shouldClampToEllipsoid()
                        || config.getIonAccessToken().isPresent()
                        || config.getMaptilerApiKey().isPresent())
            .isPresent();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schema)
        .schemaRef(SCHEMA_REF)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public Object getEntity(
      Tileset3d tileset,
      List<Link> links,
      String collectionId,
      OgcApi api,
      ApiRequestContext requestContext) {

    URICustomizer uriCustomizer =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(api.getData().getSubPath().toArray(String[]::new));
    String serviceUrl = uriCustomizer.toString();
    Tiles3dConfiguration tiles3dConfig =
        api.getData().getExtension(Tiles3dConfiguration.class, collectionId).orElseThrow();
    HtmlConfiguration htmlConfig =
        api.getData().getExtension(HtmlConfiguration.class, collectionId).orElseThrow();
    Optional<String> styleUrl =
        Optional.ofNullable(
            styleReader.getStyleUrl(
                Optional.of(Objects.requireNonNullElse(tiles3dConfig.getStyle(), "DEFAULT")),
                Optional.of(collectionId),
                api.getData().getId(),
                serviceUrl,
                MapClient.Type.CESIUM,
                htmlConfig.getDefaultStyle(),
                api.getData()));

    return ImmutableTilesetView.builder()
        .apiData(api.getData())
        .collectionId(collectionId)
        .tileset(tileset)
        .basePath(requestContext.getBasePath())
        .apiPath(requestContext.getApiPath())
        .additionalStyleUrl(styleUrl)
        .htmlConfig(htmlConfig)
        .rawLinks(links)
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .user(requestContext.getUser())
        .build();
  }
}
