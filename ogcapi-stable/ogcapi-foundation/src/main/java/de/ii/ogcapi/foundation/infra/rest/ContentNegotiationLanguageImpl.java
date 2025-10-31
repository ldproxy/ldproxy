/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.I18n;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ContentNegotiationLanguageImpl implements ContentNegotiationLanguage {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ContentNegotiationLanguageImpl.class);
  private static final String LANGUAGE_PARAMETER = "lang";
  private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";

  private final I18n i18n;

  @Inject
  ContentNegotiationLanguageImpl(I18n i18n) {
    this.i18n = i18n;
  }

  @Override
  public Optional<Locale> negotiateLanguage(ContainerRequestContext requestContext) {

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("accept-language {}", requestContext.getHeaderString(ACCEPT_LANGUAGE_HEADER));
    }

    Optional<Locale> locale = negotiateLanguage(requestContext.getRequest());

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("content-language {}", locale);
    }

    return locale;
  }

  @Override
  public Optional<Locale> negotiateLanguage(
      Request request, HttpHeaders httpHeaders, UriInfo uriInfo) {

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("accept-language {}", httpHeaders.getHeaderString(ACCEPT_LANGUAGE_HEADER));
    }

    Optional<Locale> locale = negotiateLanguage(request);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("content-language {}", locale);
    }

    return locale;
  }

  private Optional<Locale> negotiateLanguage(Request request) {
    Locale[] supportedLanguagesArray = i18n.getLanguages().toArray(Locale[]::new);

    Variant variant = null;
    try {
      if (supportedLanguagesArray.length > 0) {
        variant = request.selectVariant(Variant.languages(supportedLanguagesArray).build());
      }
    } catch (Exception ex) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Could not parse request headers during content negotiation. Selecting any language. Reason: {}",
            ex.getMessage());
      }
      return Optional.ofNullable(supportedLanguagesArray[0]);
    }

    return Optional.ofNullable(variant)
        .map(Variant::getLanguage)
        .flatMap(locale -> findMatchingLanguage(locale, i18n.getLanguages()));
  }

  private Optional<Locale> findMatchingLanguage(Locale locale, Set<Locale> supportedLocales) {
    return supportedLocales.stream()
        .filter(language -> Objects.equals(language.getLanguage(), locale.getLanguage()))
        .findFirst();
  }
}
