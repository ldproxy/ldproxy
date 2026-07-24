/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.format.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.processes.app.parameter.QueryParameterLimitProcessList;
import de.ii.ogcapi.processes.app.parameter.QueryParameterOffsetProcessList;
import de.ii.ogcapi.processes.domain.format.ProcessListFormatExtension;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.ogcapi.processes.domain.model.ProcessSummary;
import de.ii.ogcapi.processes.domain.model.ogc.OgcProcessList;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// ToDo Proper language handling
/**
 * @title HTML
 */
@Singleton
@AutoBind
public class ProcessListFormatHtml
    implements ProcessListFormatExtension, FormatHtml, ConformanceClass {

  private final I18n i18n;
  private final ProcessRepository repository;

  @Inject
  public ProcessListFormatHtml(I18n i18n, ProcessRepository repository) {
    this.i18n = i18n;
    this.repository = repository;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    if (isEnabledForApi(apiData)) {
      return ImmutableList.of("https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/html");
    }

    return ImmutableList.of();
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
      OgcProcessList processList, OgcApi api, ApiRequestContext requestContext) {
    String rootTitle = i18n.get("root", requestContext.getLanguage());
    String processListTitle = i18n.get("processListTitle", requestContext.getLanguage());

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
            .add(new NavigationDTO(processListTitle))
            .build();

    HtmlConfiguration htmlConfig = api.getData().getExtension(HtmlConfiguration.class).orElse(null);

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    Optional<Integer> optionalLimit = Optional.empty();
    Optional<Integer> optionalOffset = Optional.empty();
    for (OgcApiQueryParameter queryParameter : queryParameterSet.getDefinitions()) {
      if (queryParameter instanceof QueryParameterLimitProcessList) {
        optionalLimit = ((QueryParameterLimitProcessList) queryParameter).parse(queryParameterSet);
      }
      if (queryParameter instanceof QueryParameterOffsetProcessList) {
        optionalOffset =
            ((QueryParameterOffsetProcessList) queryParameter).parse(queryParameterSet);
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

    return ImmutableProcessListView.builder()
        .apiData(api.getData())
        .basePath(requestContext.getBasePath())
        .apiPath(requestContext.getApiPath())
        .processList(
            processList.getProcessList().stream()
                .sorted(Comparator.comparing(ProcessSummary::getId))
                .toList())
        .breadCrumbs(breadCrumbs)
        .htmlConfig(htmlConfig)
        .noIndex(isNoIndexEnabledForApi(api.getData()))
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .uri(URI.create("processes"))
        .i18n(i18n)
        .language(requestContext.getLanguage())
        .description(i18n.get("processListLink", requestContext.getLanguage()))
        .title(processListTitle)
        .rawLinks(processList.getLinks())
        .user(requestContext.getUser())
        .pagination(pagination.build())
        .build();
  }
}
