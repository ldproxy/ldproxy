/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.html;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.domain.LandingPage;
import de.ii.ogcapi.common.domain.OgcApiDatasetView;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.html.domain.ImmutableMapClient;
import de.ii.ogcapi.html.domain.ImmutableStyle;
import de.ii.ogcapi.html.domain.MapClient;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@Value.Immutable
@Value.Style(builder = "new", visibility = ImplementationVisibility.PUBLIC)
public abstract class OgcApiLandingPageView extends OgcApiDatasetView {

  public abstract I18n i18n();

  public abstract LandingPage apiLandingPage();

  public abstract Optional<String> dataSourceUrl();

  @Value.Derived
  public String mainLinksTitle() {
    return i18n().get("mainLinksTitle", language());
  }

  @Value.Derived
  public String apiInformationTitle() {
    return i18n().get("apiInformationTitle", language());
  }

  @Value.Derived
  public List<Link> distributionLinks() {
    return Objects.requireNonNullElse(
            (List<Link>) apiLandingPage().getExtensions().get("datasetDownloadLinks"),
            ImmutableList.<Link>of())
        .stream()
        .sorted(Comparator.comparing(Link::getTitle))
        .collect(Collectors.toList());
  }

  @Value.Derived
  public String keywords() {
    return apiData()
        .getMetadata()
        .map(ApiMetadata::getKeywords)
        .map(v -> Joiner.on(',').skipNulls().join(v))
        .orElse(null);
  }

  public abstract Optional<String> keywordsWithQuotes();

  @Value.Derived
  public boolean spatialSearch() {
    return false;
  }

  @Value.Derived
  public String dataTitle() {
    return i18n().get("dataTitle", language());
  }

  @Value.Derived
  public String apiDefinitionTitle() {
    return i18n().get("apiDefinitionTitle", language());
  }

  @Value.Derived
  public String apiDocumentationTitle() {
    return i18n().get("apiDocumentationTitle", language());
  }

  @Value.Derived
  public String providerTitle() {

    return i18n().get("providerTitle", language());
  }

  @Value.Derived
  public String licenseTitle() {
    return i18n().get("licenseTitle", language());
  }

  @Value.Derived
  public String spatialExtentTitle() {
    return i18n().get("spatialExtentTitle", language());
  }

  @Value.Derived
  public String temporalExtentTitle() {
    return i18n().get("temporalExtentTitle", language());
  }

  @Value.Derived
  public String dataSourceTitle() {
    return i18n().get("dataSourceTitle", language());
  }

  @Value.Derived
  public String additionalLinksTitle() {
    return i18n().get("additionalLinksTitle", language());
  }

  @Value.Derived
  public String expertInformationTitle() {
    return i18n().get("expertInformationTitle", language());
  }

  @Value.Derived
  public String externalDocsTitle() {
    return i18n().get("externalDocsTitle", language());
  }

  @Value.Derived
  public String attributionTitle() {
    return i18n().get("attributionTitle", language());
  }

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  @Value.Derived
  public boolean isDataset() {
    return apiData().isDataset()
        && Objects.nonNull(htmlConfig())
        && Objects.equals(htmlConfig().getSchemaOrgEnabled(), true);
  }

  @Value.Derived
  public MapClient mapClient() {
    return new ImmutableMapClient.Builder()
        .backgroundUrl(Optional.ofNullable(htmlConfig().getBasemapUrl()))
        .attribution(Optional.ofNullable(htmlConfig().getBasemapAttribution()))
        .bounds(Optional.ofNullable(this.getBbox()))
        .drawBounds(true)
        .isInteractive(false)
        .defaultStyle(new ImmutableStyle.Builder().color("red").build())
        .build();
  }

  public OgcApiLandingPageView() {
    super("landingPage.mustache");
  }

  public List<Link> getDistributionLinks() {
    return distributionLinks();
  }

  public Optional<Link> getData() {
    return rawLinks().stream()
        .filter(
            link ->
                Objects.equals(link.getRel(), "data")
                    || Objects.equals(link.getRel(), "http://www.opengis.net/def/rel/ogc/1.0/data"))
        .findFirst();
  }

  public List<Link> getTiles() {
    return rawLinks().stream()
        .filter(
            link -> link.getRel().startsWith("http://www.opengis.net/def/rel/ogc/1.0/tilesets-"))
        .collect(Collectors.toUnmodifiableList());
  }

  public List<Link> getTiles3d() {
    return rawLinks().stream()
        .filter(link -> link.getRel().startsWith("http://www.opengis.net/def/rel/ogc/0.0/tileset-"))
        .collect(Collectors.toUnmodifiableList());
  }

  public Optional<Link> getQueries() {
    return rawLinks().stream().filter(link -> Objects.equals(link.getRel(), "search")).findFirst();
  }

  public Optional<Link> getStyles() {
    return rawLinks().stream()
        .filter(
            link -> Objects.equals(link.getRel(), "http://www.opengis.net/def/rel/ogc/1.0/styles"))
        .findFirst();
  }

  public Optional<Link> getRoutes() {
    return rawLinks().stream()
        .filter(
            link -> Objects.equals(link.getRel(), "http://www.opengis.net/def/rel/ogc/1.0/routes"))
        .findFirst();
  }

  public Optional<Link> getMap() {
    return rawLinks().stream().filter(link -> Objects.equals(link.getRel(), "ldp-map")).findFirst();
  }

  public List<Link> getApiDefinition() {
    return rawLinks().stream()
        .filter(link -> Objects.equals(link.getRel(), "service-desc"))
        .filter(link -> link.getHref().endsWith("f=json"))
        .collect(Collectors.toUnmodifiableList());
  }

  public List<Link> getApiDocumentation() {
    return rawLinks().stream()
        .filter(link -> Objects.equals(link.getRel(), "service-doc"))
        .collect(Collectors.toUnmodifiableList());
  }

  public Optional<ExternalDocumentation> getExternalDocs() {
    return Optional.ofNullable(
        apiLandingPage().getExtensions().containsKey("externalDocs")
            ? (ExternalDocumentation) apiLandingPage().getExtensions().get("externalDocs")
            : null);
  }

  public Optional<String> getSchemaOrgDataset() {
    return Optional.of(
        getSchemaOrgDataset(apiData(), Optional.empty(), uriCustomizer().copy(), false));
  }

  public boolean getContactInfo() {
    return getMetadata()
        .filter(
            md ->
                md.getContactEmail().isPresent()
                    || md.getContactUrl().isPresent()
                    || md.getContactName().isPresent()
                    || md.getContactPhone().isPresent())
        .isPresent();
  }
}
