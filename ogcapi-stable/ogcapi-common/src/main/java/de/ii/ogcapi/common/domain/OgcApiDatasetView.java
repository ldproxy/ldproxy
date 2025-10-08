/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import static de.ii.xtraplatform.base.domain.util.LambdaWithException.mayThrow;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMetadata;
import de.ii.ogcapi.foundation.domain.ImmutableTemporalExtent;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.TemporalExtent;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

public abstract class OgcApiDatasetView extends OgcApiView {

  // Member before Immutable

  static final String INDENT = "  ";
  static final String NEW_LINE = "\n" + INDENT;

  public abstract Optional<Locale> language();

  public abstract Optional<OgcApiExtent> extent();

  @Value.Derived
  Optional<OgcApiExtentTemporal> temporalExtentIso() {
    return extent().flatMap(OgcApiExtent::getTemporal);
  }

  @Value.Default
  public Optional<TemporalExtent> rawTemporalExtent() {
    return temporalExtentIso()
        .map(v -> v.getInterval()[0])
        .map(
            v -> {
              ImmutableTemporalExtent.Builder builder = new ImmutableTemporalExtent.Builder();
              if (Objects.nonNull(v[0])) builder.start(v[0].toEpochMilli());
              if (Objects.nonNull(v[1])) builder.end(v[1].toEpochMilli());
              return builder.build();
            });
  }

  @Value.Derived
  public Optional<BoundingBox> rawBbox() {
    return extent()
        .flatMap(OgcApiExtent::getSpatial)
        .map(OgcApiExtentSpatial::getBbox)
        .map(
            v ->
                v[0].length == 4
                    ? BoundingBox.of(v[0][0], v[0][1], v[0][2], v[0][3], OgcCrs.CRS84)
                    : BoundingBox.of(
                        v[0][0], v[0][1], v[0][2], v[0][3], v[0][4], v[0][5], OgcCrs.CRS84h));
  }

  protected OgcApiDatasetView(String templateName) {
    super(templateName);
  }

  public abstract List<Link> getDistributionLinks();

  public List<Link> getLinks() {
    List<String> ignoreRels =
        new ImmutableList.Builder<String>()
            .add("self")
            .add("alternate")
            .add("conformance")
            .add("service-desc")
            .add("service-doc")
            .add("describedby")
            .add("data")
            .add("items")
            .add("tilesets-\\w+")
            .add("styles")
            .add("routes")
            .add("search")
            .add("ldp-map")
            .build();

    return rawLinks().stream()
        .filter(
            link ->
                !link.getRel()
                    .replace("http://www\\.opengis\\.net/def/rel/ogc/1\\.0/", "")
                    .replace("http://www\\.opengis\\.net/def/rel/ogc/0\\.0/", "")
                    .matches("^(?:" + String.join("|", ignoreRels) + ")$"))
        .sorted(Comparator.comparing(Link::getTitle))
        .collect(Collectors.toList());
  }

  public Optional<ApiMetadata> getMetadata() {
    return apiData().getMetadata();
  }

  public Optional<String> getCanonicalUrl() throws URISyntaxException {
    return rawLinks().stream()
        .filter(link -> Objects.equals(link.getRel(), "self"))
        .map(Link::getHref)
        .map(
            mayThrow(
                url -> new URICustomizer(url).clearParameters().ensureNoTrailingSlash().toString()))
        .findFirst();
  }

  public Map<String, String> getTemporalExtent() {
    if (rawTemporalExtent().isEmpty()) return null;
    else if (Objects.isNull(rawTemporalExtent().get().getStart())
        && Objects.isNull(rawTemporalExtent().get().getEnd())) return ImmutableMap.of();
    else if (Objects.isNull(rawTemporalExtent().get().getStart()))
      return ImmutableMap.of("end", String.valueOf(rawTemporalExtent().get().getEnd()));
    else if (Objects.isNull(rawTemporalExtent().get().getEnd()))
      return ImmutableMap.of("start", String.valueOf(rawTemporalExtent().get().getStart()));
    else
      return ImmutableMap.of(
          "start",
          String.valueOf(rawTemporalExtent().get().getStart()),
          "end",
          String.valueOf(rawTemporalExtent().get().getEnd()));
  }

  public Optional<String> getTemporalCoverage() {
    return temporalExtentIso().map(v -> v.getFirstIntervalIso8601());
  }

  public Optional<String> getTemporalCoverageHtml() {
    return rawTemporalExtent()
        .map(extent -> extent.humanReadable(language().orElse(Locale.getDefault())));
  }

  public Map<String, String> getBbox() {
    return rawBbox()
        .map(
            v ->
                ImmutableMap.of(
                    "minLng",
                    Double.toString(v.getXmin()),
                    "minLat",
                    Double.toString(v.getYmin()),
                    "maxLng",
                    Double.toString(v.getXmax()),
                    "maxLat",
                    Double.toString(v.getYmax())))
        .orElse(null);
  }

  public Optional<String> getSpatialCoverage() {
    return rawBbox()
        .map(
            v ->
                String.format(
                    Locale.US, "%f %f %f %f", v.getYmin(), v.getXmin(), v.getYmax(), v.getXmax()));
  }

  public Optional<String> getDistributionsAsString() {
    String distribution =
        getDistributionLinks().stream()
            .map(
                link ->
                    "{ \"@type\": \"DataDownload\", "
                        + "\"name\":\""
                        + link.getTitle()
                        + "\", "
                        + "\"encodingFormat\": \""
                        + link.getType()
                        + "\", "
                        + "\"contentUrl\": \""
                        + link.getHref()
                        + "\" }")
            .collect(Collectors.joining(", "));
    return distribution.isEmpty() ? Optional.empty() : Optional.of(distribution);
  }

  protected String getSchemaOrgDataset(
      OgcApiDataV2 apiData,
      Optional<FeatureTypeConfigurationOgcApi> collection,
      URICustomizer landingPageUriCustomizer,
      boolean embedded) {
    ApiMetadata metadata = getMetadata().orElse(new ImmutableApiMetadata.Builder().build());
    String url =
        collection
            .map(
                s ->
                    landingPageUriCustomizer
                        .copy()
                        .removeParameters()
                        .ensureLastPathSegments("collections", s.getId())
                        .ensureNoTrailingSlash()
                        .toString())
            .orElse(
                landingPageUriCustomizer
                    .copy()
                    .removeParameters()
                    .ensureNoTrailingSlash()
                    .toString());

    return "{"
        + NEW_LINE
        + (!embedded ? "\"@context\": \"https://schema.org/\"," + NEW_LINE : "")
        + (embedded ? INDENT : "")
        + "\"@type\": \"Dataset\","
        + NEW_LINE
        + (embedded ? INDENT : "")
        + "\"name\": \""
        + collection
            .map(FeatureTypeConfiguration::getLabel)
            .orElse(apiData().getLabel())
            .replace("\"", "\\\"")
        + "\","
        + NEW_LINE
        + collection
            .map(FeatureTypeConfiguration::getDescription)
            .orElse(apiData().getDescription())
            .map(
                s ->
                    (embedded ? INDENT : "")
                        + "\"description\": \""
                        + s.replace("\"", "\\\"")
                        + "\","
                        + NEW_LINE)
            .orElse("")
        + (!embedded
            ? "\"keywords\": [ "
                + String.join(
                    ",",
                    metadata.getKeywords().stream()
                        .filter(keyword -> Objects.nonNull(keyword) && !keyword.isEmpty())
                        .map(keyword -> ("\"" + keyword + "\""))
                        .collect(Collectors.toList()))
                + " ],"
                + NEW_LINE
            : "")
        + (metadata.getCreatorName().isPresent()
            ? ((embedded ? INDENT : "")
                + "\"creator\": {"
                + NEW_LINE
                + (embedded ? INDENT : "")
                + INDENT
                + "\"@type\": \"Organization\""
                + metadata
                    .getCreatorName()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + "\"name\": \""
                                + s
                                + "\"")
                    .orElse("")
                + metadata
                    .getCreatorUrl()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + "\"url\": \""
                                + s
                                + "\"")
                    .orElse("")
                + metadata
                    .getCreatorLogoUrl()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + "\"logo\": \""
                                + s
                                + "\"")
                    .orElse("")
                + NEW_LINE
                + (embedded ? INDENT : "")
                + "},"
                + NEW_LINE)
            : "")
        + (embedded ? INDENT : "")
        + "\"publisher\": {"
        + NEW_LINE
        + (embedded ? INDENT : "")
        + INDENT
        + "\"@type\": \"Organization\""
        + metadata
            .getPublisherName()
            .map(
                s -> "," + NEW_LINE + (embedded ? INDENT : "") + INDENT + "\"name\": \"" + s + "\"")
            .orElse("")
        + metadata
            .getPublisherUrl()
            .map(s -> "," + NEW_LINE + (embedded ? INDENT : "") + INDENT + "\"url\": \"" + s + "\"")
            .orElse("")
        + metadata
            .getPublisherLogoUrl()
            .map(
                s -> "," + NEW_LINE + (embedded ? INDENT : "") + INDENT + "\"logo\": \"" + s + "\"")
            .orElse("")
        + (metadata.getContactEmail().isPresent()
                || metadata.getContactPhone().isPresent()
                || metadata.getContactUrl().isPresent()
            ? ","
                + NEW_LINE
                + (embedded ? INDENT : "")
                + INDENT
                + "\"contactPoint\": {"
                + NEW_LINE
                + (embedded ? INDENT : "")
                + INDENT
                + INDENT
                + "\"@type\": \"ContactPoint\","
                + NEW_LINE
                + (embedded ? INDENT : "")
                + INDENT
                + INDENT
                + "\"contactType\": \"technical support\""
                + metadata
                    .getContactName()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + INDENT
                                + "\"name\": \""
                                + s
                                + "\"")
                    .orElse("")
                + metadata
                    .getContactEmail()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + INDENT
                                + "\"email\": \""
                                + s
                                + "\"")
                    .orElse("")
                + metadata
                    .getContactPhone()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + INDENT
                                + "\"telephone\": \""
                                + s
                                + "\"")
                    .orElse("")
                + metadata
                    .getContactUrl()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + INDENT
                                + "\"url\": \""
                                + s
                                + "\"")
                    .orElse("")
                + NEW_LINE
                + (embedded ? INDENT : "")
                + INDENT
                + "}"
            : "")
        + NEW_LINE
        + (embedded ? INDENT : "")
        + "},"
        + NEW_LINE
        + (embedded ? INDENT : "")
        + "\""
        + (embedded ? "sameAs" : "url")
        + "\":\""
        + url
        + "\""
        + (metadata.getLicenseUrl().isPresent() || metadata.getLicenseName().isPresent()
            ? ","
                + NEW_LINE
                + (embedded ? INDENT : "")
                + "\"license\": {"
                + NEW_LINE
                + (embedded ? INDENT : "")
                + INDENT
                + "\"@type\": \"CreativeWork\""
                + metadata
                    .getLicenseName()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + "\"name\": \""
                                + s
                                + "\"")
                    .orElse("")
                + metadata
                    .getLicenseUrl()
                    .map(
                        s ->
                            ","
                                + NEW_LINE
                                + (embedded ? INDENT : "")
                                + INDENT
                                + "\"url\": \""
                                + s
                                + "\"")
                    .orElse("")
                + NEW_LINE
                + (embedded ? INDENT : "")
                + "}"
            : "")
        + (!embedded
            ? getTemporalCoverage()
                .map(s -> "," + NEW_LINE + "\"temporalCoverage\": \"" + s + "\"")
                .orElse("")
            : "")
        + (!embedded
            ? getSpatialCoverage()
                .map(
                    s ->
                        ","
                            + NEW_LINE
                            + "\"spatialCoverage\": { \"@type\": \"Place\", \"geo\":{ \"@type\": \"GeoShape\", \"box\": \""
                            + s
                            + "\" } }")
                .orElse("")
            : "")
        + (!embedded
            ? collection
                .map(
                    s ->
                        ","
                            + NEW_LINE
                            + "\"isPartOf\": "
                            + getSchemaOrgDataset(
                                apiData, Optional.empty(), landingPageUriCustomizer.copy(), true))
                // for cases with a single collection, that collection is not reported as a
                // sub-dataset
                .orElse(
                    (apiData().getCollections().size() > 1
                            ? (","
                                + NEW_LINE
                                + "\"hasPart\": [ "
                                + String.join(
                                    ", ",
                                    apiData().getCollections().values().stream()
                                        .filter(col -> col.getEnabled())
                                        .map(
                                            s ->
                                                getSchemaOrgDataset(
                                                    apiData,
                                                    Optional.of(s),
                                                    landingPageUriCustomizer.copy(),
                                                    true))
                                        .collect(Collectors.toUnmodifiableList()))
                                + " ]")
                            : "")
                        + ","
                        + NEW_LINE
                        + "\"includedInDataCatalog\": { \"@type\": \"DataCatalog\", \"url\": \""
                        + landingPageUriCustomizer
                            .copy()
                            .removeParameters()
                            .ensureNoTrailingSlash()
                            .toString()
                        + "\" }")
            : "")
        + (!embedded
            ? getDistributionsAsString()
                .map(s -> "," + NEW_LINE + "\"distribution\": [ " + s + " ]")
                .orElse("")
            : "")
        + "\n"
        + (embedded ? INDENT : "")
        + "}";
  }
}
