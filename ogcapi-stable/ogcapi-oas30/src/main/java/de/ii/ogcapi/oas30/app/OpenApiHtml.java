/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.oas30.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.domain.ApiDefinitionFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.oas30.domain.Oas30Configuration;
import de.ii.xtraplatform.auth.domain.Oidc;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class OpenApiHtml implements ApiDefinitionFormatExtension, FormatHtml {

  private final ExtensionRegistry extensionRegistry;
  private final I18n i18n;
  private final Oidc oidc;

  @Inject
  public OpenApiHtml(ExtensionRegistry extensionRegistry, I18n i18n, Oidc oidc) {
    this.extensionRegistry = extensionRegistry;
    this.i18n = i18n;
    this.oidc = oidc;
  }

  // always active, if OpenAPI 3.0 is active, since a service-doc link relation is mandatory
  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Oas30Configuration.class;
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.HTML_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return FormatExtension.HTML_CONTENT;
  }

  @Override
  public Response getResponse(OgcApiDataV2 apiData, ApiRequestContext apiRequestContext) {
    String rootTitle = i18n.get("root", apiRequestContext.getLanguage());
    URICustomizer resourceUri = apiRequestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    rootTitle,
                    homeUrl(apiData)
                        .orElse(
                            resourceUri
                                .copy()
                                .removeLastPathSegments(apiData.getSubPath().size() + 1)
                                .toString())))
            .add(
                new NavigationDTO(
                    apiData.getLabel(), resourceUri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO("OpenAPI Definition"))
            .build();

    List<Link> links =
        extensionRegistry.getExtensionsForType(ApiDefinitionFormatExtension.class).stream()
            .filter(outputFormatExtension -> outputFormatExtension.isEnabledForApi(apiData))
            .filter(outputFormatExtension -> !Objects.equals(outputFormatExtension, this))
            .map(outputFormatExtension -> outputFormatExtension.getMediaType())
            .map(
                mediaType ->
                    new ImmutableLink.Builder()
                        .href(
                            apiRequestContext
                                .getUriCustomizer()
                                .copy()
                                .setParameter("f", mediaType.parameter())
                                .toString())
                        .type(mediaType.type().toString())
                        .rel("alternate")
                        .build())
            .collect(Collectors.toList());

    boolean showCommonExtensions =
        apiData
            .getExtension(Oas30Configuration.class)
            .map(Oas30Configuration::isShowSchemaConstraintsInHtml)
            .orElse(false);

    OpenApiView view =
        new ImmutableOpenApiView.Builder()
            .breadCrumbs(breadCrumbs)
            .rawLinks(links)
            .urlPrefix(apiRequestContext.getStaticUrlPrefix())
            .uriCustomizer(apiRequestContext.getUriCustomizer().copy())
            .showCommonExtensions(showCommonExtensions)
            .oidc(oidc)
            .user(apiRequestContext.getUser())
            .build();

    return Response.ok(view).build();
  }

  @Override
  public Optional<String> getRel() {
    return Optional.of("service-doc");
  }
}
