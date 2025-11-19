/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiCatalog;
import de.ii.ogcapi.foundation.domain.ApiCatalogProvider;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.ImmutableHtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.xtraplatform.entities.domain.EntityDataBuilder;
import de.ii.xtraplatform.entities.domain.EntityDataDefaultsStore;
import de.ii.xtraplatform.services.domain.Service;
import de.ii.xtraplatform.services.domain.ServiceData;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class ApiCatalogProviderHtml extends ApiCatalogProvider {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.TEXT_HTML_TYPE)
          .label("HTML")
          .parameter("html")
          .build();

  @Inject
  public ApiCatalogProviderHtml(
      ServicesContext servicesContext,
      I18n i18n,
      EntityDataDefaultsStore defaultsStore,
      ExtensionRegistry extensionRegistry) {
    super(servicesContext, i18n, defaultsStore, extensionRegistry);
  }

  private HtmlConfiguration getHtmlConfigurationDefaults() {
    EntityDataBuilder<?> builder =
        defaultsStore.getBuilder(
            Identifier.from(
                EntityDataDefaultsStore.EVENT_TYPE,
                Service.TYPE,
                OgcApiDataV2.SERVICE_TYPE.toLowerCase()));
    if (builder instanceof ImmutableOgcApiDataV2.Builder) {
      ImmutableOgcApiDataV2 defaults =
          ((ImmutableOgcApiDataV2.Builder) builder.fillRequiredFieldsWithPlaceholders()).build();
      return defaults
          .getExtension(HtmlConfiguration.class)
          .orElse(new ImmutableHtmlConfiguration.Builder().build());
    }
    return new ImmutableHtmlConfiguration.Builder().build();
  }

  @Override
  public ApiMediaType getApiMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public MediaType getMediaType() {
    return MEDIA_TYPE.type();
  }

  // TODO: add locale parameter in ServiceListing.getServiceListing() in xtraplatform
  @Override
  public Response getServiceListing(
      List<ServiceData> apis,
      URICustomizer uriCustomizer,
      Map<String, String> queryParameters,
      Optional<Principal> user,
      Optional<Locale> language)
      throws URISyntaxException {
    ApiCatalog apiCatalog = getCatalog(apis, uriCustomizer, language, queryParameters);

    // TODO: map in caller
    return Response.ok()
        .entity(
            new ImmutableServiceOverviewView.Builder()
                .uriCustomizer(uriCustomizer)
                .apiCatalog(apiCatalog)
                .htmlConfig(getHtmlConfigurationDefaults())
                .i18n(i18n)
                .language(language.get())
                .apiData(null)
                .breadCrumbs(
                    new ImmutableList.Builder<NavigationDTO>()
                        .add(new NavigationDTO(i18n.get("root", Optional.of(language.get())), true))
                        .build())
                .noIndex(getHtmlConfigurationDefaults().getNoIndexEnabled())
                .basePath(apiCatalog.basePath())
                .apiPath(apiCatalog.basePath())
                .rawLinks(apiCatalog.getLinks())
                .title(
                    apiCatalog
                        .getTitle()
                        .orElse(i18n.get("rootTitle", Optional.of(language.get()))))
                .description(
                    apiCatalog
                        .getDescription()
                        .orElse(i18n.get("rootDescription", Optional.of(language.get()))))
                .user(Optional.empty())
                .build())
        .build();
  }
}
