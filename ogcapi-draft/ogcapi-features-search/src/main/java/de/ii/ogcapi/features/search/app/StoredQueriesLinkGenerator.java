/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.ImmutableLink.Builder;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class StoredQueriesLinkGenerator extends DefaultLinksGenerator {

  public static final String NAME_TEMPLATE = "{{name}}";
  public static final String PARAMETER_TEMPLATE = "{{parameter}}";
  public static final String OFFSET = "offset";
  public static final String PARAMETERS = "parameters";
  public static final String DESCRIBEDBY = "describedby";
  public static final String QUERY_PARAMETERS_LINK = "queryParametersLink";
  public static final String QUERY_PARAMETER_LINK = "queryParameterLink";
  public static final String F = "f";
  public static final String LANG = "lang";
  public static final String NEXT_LINK = "nextLink";
  public static final String NEXT = "next";
  public static final String PREV = "prev";
  public static final String PREV_LINK = "prevLink";
  public static final String FIRST = "first";
  public static final String FIRST_LINK = "firstLink";
  public static final String DEFINITION = "definition";
  public static final String QUERY_DEFINITION_LINK = "queryDefinitionLink";
  public static final String SELF = "self";
  public static final String STORED_QUERY_LINK = "storedQueryLink";
  public static final String SEARCH = "search";
  public static final String STORED_QUERIES_LINK = "storedQueriesLink";

  /**
   * generates the links on the landing page
   *
   * @param uriBuilder the URI, split in host, path and query
   * @return a list with links
   */
  public List<Link> generateLandingPageLinks(
      URICustomizer uriBuilder, I18n i18n, Optional<Locale> language) {

    ImmutableList.Builder<Link> builder = ImmutableList.builder();

    builder.add(
        new ImmutableLink.Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegment(SEARCH)
                    .removeParameters(F)
                    .toString())
            .rel(SEARCH)
            .title(i18n.get(STORED_QUERIES_LINK, language))
            .build());

    return builder.build();
  }

  /**
   * generates the links for a stored query on the page /{serviceId}/search
   *
   * @param uriBuilder the URI, split in host, path and query
   * @param queryId the ids of the queries
   * @param parameterNames
   * @return a list with links
   */
  public List<Link> generateStoredQueryLinks(
      URICustomizer uriBuilder,
      String name,
      String queryId,
      Set<String> parameterNames,
      boolean managerEnabled,
      I18n i18n,
      Optional<Locale> language) {

    final ImmutableList.Builder<Link> builder = new ImmutableList.Builder<>();

    addSelfLink(uriBuilder, name, queryId, parameterNames, i18n, language, builder);

    if (managerEnabled) {
      addDefinitionLink(uriBuilder, name, queryId, i18n, language, builder);
    }

    if (!parameterNames.isEmpty()) {
      addParametersLink(uriBuilder, name, queryId, i18n, language, builder);
      addParameterLinks(uriBuilder, name, queryId, parameterNames, i18n, language, builder);
    }

    return builder.build();
  }

  private void addDefinitionLink(
      URICustomizer uriBuilder,
      String name,
      String queryId,
      I18n i18n,
      Optional<Locale> language,
      ImmutableList.Builder<Link> builder) {
    builder.add(
        new Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegments(queryId, DEFINITION)
                    .removeParameters(F)
                    .toString())
            .rel(DESCRIBEDBY)
            .title(i18n.get(QUERY_DEFINITION_LINK, language).replace(NAME_TEMPLATE, name))
            .build());
  }

  private void addParametersLink(
      URICustomizer uriBuilder,
      String name,
      String queryId,
      I18n i18n,
      Optional<Locale> language,
      ImmutableList.Builder<Link> builder) {
    builder.add(
        new Builder()
            .href(
                uriBuilder
                    .copy()
                    .ensureNoTrailingSlash()
                    .ensureLastPathSegments(queryId, PARAMETERS)
                    .removeParameters(F)
                    .toString())
            .rel(DESCRIBEDBY)
            .title(i18n.get(QUERY_PARAMETERS_LINK, language).replace(NAME_TEMPLATE, name))
            .build());
  }

  private void addParameterLinks(
      URICustomizer uriBuilder,
      String name,
      String queryId,
      Set<String> parameterNames,
      I18n i18n,
      Optional<Locale> language,
      ImmutableList.Builder<Link> builder) {
    parameterNames.stream()
        .sorted()
        .forEach(
            parameterName ->
                builder.add(
                    new Builder()
                        .href(
                            uriBuilder
                                .copy()
                                .ensureNoTrailingSlash()
                                .ensureLastPathSegments(queryId, PARAMETERS, parameterName)
                                .removeParameters(F)
                                .toString())
                        .rel(DESCRIBEDBY)
                        .title(
                            i18n.get(QUERY_PARAMETER_LINK, language)
                                .replace(NAME_TEMPLATE, name)
                                .replace(PARAMETER_TEMPLATE, parameterName))
                        .build()));
  }

  private void addSelfLink(
      URICustomizer uriBuilder,
      String name,
      String queryId,
      Set<String> parameterNames,
      I18n i18n,
      Optional<Locale> language,
      ImmutableList.Builder<Link> builder) {
    Builder selfBuilder =
        new Builder()
            .rel(SELF)
            .title(i18n.get(STORED_QUERY_LINK, language).replace(NAME_TEMPLATE, name));

    if (parameterNames.isEmpty()) {
      selfBuilder.href(
          uriBuilder
              .copy()
              .ensureNoTrailingSlash()
              .ensureLastPathSegment(queryId)
              .removeParameters(F)
              .toString());
    } else {
      selfBuilder
          .href(
              uriBuilder
                  .copy()
                  .clearParameters()
                  .ensureNoTrailingSlash()
                  .ensureLastPathSegment(
                      String.format("%s{?%s}", queryId, String.join(",", parameterNames)))
                  .toString())
          .templated(true)
          .varBase(
              uriBuilder
                  .copy()
                  .clearParameters()
                  .ensureLastPathSegments(queryId, PARAMETERS)
                  .ensureTrailingSlash()
                  .toString());
    }
    builder.add(selfBuilder.build());
  }

  public List<Link> generateFeaturesLinks(
      URICustomizer uriBuilder,
      int offset,
      int limit,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      I18n i18n,
      Optional<Locale> language) {
    final ImmutableList.Builder<Link> builder =
        new ImmutableList.Builder<Link>()
            .addAll(
                super.generateLinks(uriBuilder, mediaType, alternateMediaTypes, i18n, language));

    uriBuilder.removeParameters(LANG);

    // we have to create a next link here as we do not know numberMatched yet, but it will
    // be removed again in the feature transformer, if we are on the last page
    builder.add(
        new ImmutableLink.Builder()
            .href(getUrlWithOffset(uriBuilder.copy(), offset + limit))
            .rel(NEXT)
            .type(mediaType.type().toString())
            .title(i18n.get(NEXT_LINK, language))
            .build());
    if (offset > 0) {
      builder.add(
          new ImmutableLink.Builder()
              .href(getUrlWithOffset(uriBuilder.copy(), offset - limit))
              .rel(PREV)
              .type(mediaType.type().toString())
              .title(i18n.get(PREV_LINK, language))
              .build());
      builder.add(
          new ImmutableLink.Builder()
              .href(getUrlWithOffset(uriBuilder.copy(), 0))
              .rel(FIRST)
              .type(mediaType.type().toString())
              .title(i18n.get(FIRST_LINK, language))
              .build());
    }

    return builder.build();
  }

  private String getUrlWithOffset(final URICustomizer uriBuilder, final int offset) {
    if (offset == 0) {
      return uriBuilder.ensureNoTrailingSlash().removeParameters(OFFSET).toString();
    }

    return uriBuilder
        .ensureNoTrailingSlash()
        .removeParameters(OFFSET)
        .setParameter(OFFSET, String.valueOf(Integer.max(0, offset)))
        .toString();
  }
}
