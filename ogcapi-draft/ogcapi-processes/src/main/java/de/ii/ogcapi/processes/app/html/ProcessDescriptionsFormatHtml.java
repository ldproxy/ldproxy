/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.processes.app.QueryParameterLimitProcesses;
import de.ii.ogcapi.processes.app.QueryParameterOffsetProcesses;
import de.ii.ogcapi.processes.domain.ProcessDescriptionsFormatExtension;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptionRepository;
import de.ii.ogcapi.processes.domain.model.ProcessDescriptions;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class ProcessDescriptionsFormatHtml
    implements ProcessDescriptionsFormatExtension, FormatHtml {

  private final I18n i18n;
  // ToDo Repository access in Format file seems like bad smell, move pagnitation logic?
  private final ProcessDescriptionRepository repository;

  @Inject
  public ProcessDescriptionsFormatHtml(I18n i18n, ProcessDescriptionRepository repository) {
    this.i18n = i18n;
    this.repository = repository;
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.HTML_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return FormatExtension.HTML_CONTENT;
  }

  private boolean isNoIndexEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getNoIndexEnabled)
        .orElse(true);
  }

  @Override
  public Object getEntity(
      ProcessDescriptions processDescriptions, OgcApi api, ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String processDescriptionsTitle =
        i18n.get("processDescriptionsTitle", requestContext.getLanguage());

    URICustomizer resourceUri = requestContext.getUriCustomizer().copy().clearParameters();
    final List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    rootTitle,
                    homeUrl(api.getData())
                        .orElse(
                            resourceUri
                                .copy()
                                .removeLastPathSegments(api.getData().getSubPath().size() + 1)
                                .toString())))
            .add(
                new NavigationDTO(
                    api.getData().getLabel(),
                    resourceUri.copy().removeLastPathSegments(1).toString()))
            .add(new NavigationDTO(processDescriptionsTitle))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    Optional<Integer> optionalLimit = Optional.empty();
    Optional<Integer> optionalOffset = Optional.empty();
    for (OgcApiQueryParameter queryParameter : queryParameterSet.getDefinitions()) {
      if (queryParameter instanceof QueryParameterLimitProcesses) {
        optionalLimit = ((QueryParameterLimitProcesses) queryParameter).parse(queryParameterSet);
      }
      if (queryParameter instanceof QueryParameterOffsetProcesses) {
        optionalOffset = ((QueryParameterOffsetProcesses) queryParameter).parse(queryParameterSet);
      }
    }

    // We just .get() because of the default values
    int offset = optionalOffset.get();
    int limit = optionalLimit.get();
    int size = repository.getAll().size();
    int page = Math.floorDiv(offset, limit) + 1;
    int pages = (int) Math.ceil((double) size / limit);

    ImmutableList.Builder<NavigationDTO> pagination = new ImmutableList.Builder<>();

    if (page > 1) {
      pagination
          .add(new NavigationDTO("«", String.format("limit=%d&offset=%d", limit, 0)))
          .add(new NavigationDTO("‹", String.format("limit=%d&offset=%d", limit, offset - limit)));
    } else {
      pagination.add(new NavigationDTO("«")).add(new NavigationDTO("‹"));
    }

    long from = Math.max(1, page - 2);
    long to = Math.min(pages, from + 4);
    if (to == pages) {
      from = Math.max(1, to - 4);
    }
    for (long i = from; i <= to; i++) {
      if (i == page) {
        pagination.add(new NavigationDTO(String.valueOf(i), true));
      } else {
        pagination.add(
            new NavigationDTO(
                String.valueOf(i), String.format("limit=%d&offset=%d", limit, (i - 1) * limit)));
      }
    }

    if (page < pages) {
      pagination
          .add(new NavigationDTO("›", String.format("limit=%d&offset=%d", limit, offset + limit)))
          .add(
              new NavigationDTO(
                  "»", String.format("limit=%d&offset=%d", limit, (pages - 1) * limit)));
    } else {
      pagination.add(new NavigationDTO("›")).add(new NavigationDTO("»"));
    }

    return ImmutableProcessDescriptionsView.builder()
        .apiData(api.getData())
        .basePath(requestContext.getBasePath())
        .apiPath(requestContext.getApiPath())
        .processDescriptions(processDescriptions.getProcesses())
        .breadCrumbs(breadCrumbs)
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .uri(URI.create("processes"))
        .i18n(i18n)
        .language(requestContext.getLanguage().orElse(null))
        .description(i18n.get("processDescriptionsLink", requestContext.getLanguage()))
        .title(i18n.get("processDescriptionsTitle", requestContext.getLanguage()))
        .rawLinks(processDescriptions.getLinks())
        .user(requestContext.getUser())
        .pagination(pagination.build())
        .build();
  }
}
