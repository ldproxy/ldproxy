/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import de.ii.ogcapi.foundation.domain.ApiCatalog;
import de.ii.ogcapi.foundation.domain.ApiCatalogEntry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.html.domain.OgcApiView;
import java.net.URI;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

/**
 * @author zahnen
 */
@Value.Immutable
@Value.Style(builder = "new", visibility = ImplementationVisibility.PUBLIC)
public abstract class ServiceOverviewView extends OgcApiView {

  public abstract ApiCatalog apiCatalog();

  public abstract URI uri();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  @Value.Default
  public boolean isApiCatalog() {

    return true;
  }

  @Value.Derived
  public String tagsTitle() {
    return i18n().get("tagsTitle", language());
  }

  @Value.Derived
  public String canonicalUrl() {
    return apiCatalog().getCatalogUri().toString();
  }

  @Value.Derived
  public List<ApiCatalogEntry> data() {
    return apiCatalog().getApis().stream()
        .sorted(Comparator.comparing(e -> e.getTitle().orElse("")))
        .collect(Collectors.toList());
  }

  @Value.Derived
  public Optional<String> googleSiteVerification() {
    return apiCatalog().getGoogleSiteVerification();
  }

  public ServiceOverviewView() {
    super("services.mustache");
  }

  public String getDatasetsAsString() {
    return data().stream()
        .filter(ApiCatalogEntry::isDataset)
        .map(
            api ->
                "{ \"@type\": \"Dataset\", "
                    + "\"name\": \""
                    + api.getTitle().orElse(api.getId()).replace("\"", "\\\"")
                    + "\", "
                    + "\"description\": \""
                    + api.getDescription().orElse("").replace("\"", "\\\"")
                    + "\", "
                    + "\"sameAs\": \""
                    + api.getLandingPageUri()
                    + "\""
                    + " }")
        .collect(Collectors.joining(", "));
  }

  public List<String> getAllTags() {
    return data().stream()
        .map(api -> api.getTags())
        .flatMap(Collection::stream)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public boolean hasTags() {
    return !getAllTags().isEmpty();
  }
}
