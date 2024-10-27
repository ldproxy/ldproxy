/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.domain;

import com.github.mustachejava.util.DecoratedCollection;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.common.domain.OgcApiDatasetView;
import de.ii.ogcapi.features.html.app.CesiumDataFeatures;
import de.ii.ogcapi.features.html.app.FeatureHtml;
import de.ii.ogcapi.features.html.app.FilterEditor;
import de.ii.ogcapi.features.html.app.ImmutableFilterEditor.Builder;
import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration.POSITION;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.DatasetView;
import de.ii.ogcapi.html.domain.ImmutableMapClient;
import de.ii.ogcapi.html.domain.ImmutableSource;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.MapClient.Popup;
import de.ii.ogcapi.html.domain.MapClient.Source.TYPE;
import de.ii.ogcapi.html.domain.MapClient.Type;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FeaturesView extends OgcApiDatasetView {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesView.class);

  protected FeaturesView(String templateName) {
    super(templateName);
  }

  public abstract Optional<FeatureTypeConfigurationOgcApi> collectionData();

  public abstract String name();

  @Nullable
  public abstract String rawAttribution();

  public abstract List<NavigationDTO> rawFormats();

  public abstract Optional<Boolean> bare();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  public abstract Type mapClientType();

  @Nullable
  public abstract String styleUrl();

  public abstract Optional<String> style();

  public abstract boolean removeZoomLevelConstraints();

  public abstract boolean propertyTooltips();

  @Nullable
  public abstract URI uri();

  @Nullable
  public abstract List<NavigationDTO> pagination();

  public abstract List<NavigationDTO> metaPagination();

  public abstract POSITION mapPosition();

  public abstract Set<Entry<String, String>> filterFields();

  public abstract Optional<BoundingBox> spatialExtent();

  @Nullable
  public abstract Object data();

  @Nullable
  public abstract String url();

  @Nullable
  public abstract String version();

  @Nullable
  public abstract String license();

  @Nullable
  public abstract String metadataUrl();

  @Value.Default
  public List<String> keywords() {
    return new ArrayList<>();
  }

  @Value.Default
  public List<DatasetView> featureTypes() {
    return new ArrayList<>();
  }

  public abstract Optional<String> PersistentUri();

  public abstract boolean isCollection();

  @Nullable
  public abstract Boolean spatialSearch();

  @Value.Default
  public List<FeatureHtml> features() {
    return new ArrayList<>();
  }

  @Value.Default
  public boolean hideMap() {
    return false;
  }

  @Value.Derived
  public boolean fromStoredQuery() {
    return false;
  }

  @Nullable
  @Value.Derived
  public Map<String, String> bbox() {
    return spatialExtent()
        .map(
            boundingBox ->
                ImmutableMap.of(
                    "minLng", Double.toString(boundingBox.getXmin()),
                    "minLat", Double.toString(boundingBox.getYmin()),
                    "maxLng", Double.toString(boundingBox.getXmax()),
                    "maxLat", Double.toString(boundingBox.getYmax())))
        .orElse(null);
  }

  @Value.Derived
  public URICustomizer uriBuilder() {
    return new URICustomizer(uri());
  }

  @Value.Derived
  public boolean schemaOrgFeatures() {
    return (Objects.nonNull(htmlConfig())
        && Objects.equals(htmlConfig().getSchemaOrgEnabled(), true));
  }

  @Value.Derived
  public MapClient mapClient() {
    if (mapClientType().equals(MapClient.Type.MAP_LIBRE)) {

      return new ImmutableMapClient.Builder()
          .backgroundUrl(Optional.ofNullable(htmlConfig().getBasemapUrl()))
          .attribution(getAttribution().replace("'", "\\'"))
          .bounds(Optional.ofNullable(bbox()))
          .data(
              new ImmutableSource.Builder()
                  .type(TYPE.geojson)
                  .url(uriBuilder().removeParameters("f").ensureParameter("f", "json").toString())
                  .build())
          .popup(Popup.HOVER_ID)
          .styleUrl(Optional.ofNullable(styleUrl()))
          .removeZoomLevelConstraints(removeZoomLevelConstraints())
          .useBounds(true)
          .build();
    } else if (mapClientType().equals(MapClient.Type.CESIUM)) {
      return new ImmutableMapClient.Builder()
          .type(mapClientType())
          .backgroundUrl(
              Optional.ofNullable(htmlConfig().getBasemapUrl())
                  .map(
                      url ->
                          url.replace("{z}", "{TileMatrix}")
                              .replace("{y}", "{TileRow}")
                              .replace("{x}", "{TileCol}")))
          .attribution(getAttribution().replace("'", "\\'"))
          .build();
    } else {
      LOGGER.error(
          "Configuration error: {} is not a supported map client for the HTML representation of features.",
          mapClientType());
      return null;
    }
  }

  @Value.Default
  @Nullable
  public FilterEditor filterEditor() {
    return new Builder()
        .backgroundUrl(Optional.ofNullable(htmlConfig().getBasemapUrl()))
        .attribution(Optional.ofNullable(htmlConfig().getBasemapAttribution()))
        .build();
  }

  @Value.Derived
  public CesiumDataFeatures cesiumData() {
    return new CesiumDataFeatures(
        features(),
        uriBuilder()
            .copy()
            .removeParameters("f")
            .ensureParameter("f", "glb")
            // must be true as long as no terrain is used in the Cesium viewer
            .ensureParameter("clampToEllipsoid", "true")
            .toString());
  }

  @Value.Derived
  public String getPath() {
    String path = uri().getPath();
    return path;
  }

  @Value.Derived
  public boolean isMapTop() {
    return mapPosition() == POSITION.TOP
        || (mapPosition() == POSITION.AUTO
            && (features().isEmpty() || features().stream().anyMatch(FeatureHtml::hasObjects)));
  }

  @Value.Derived
  public boolean isMapRight() {
    return mapPosition() == POSITION.RIGHT
        || (mapPosition() == POSITION.AUTO
            && !features().isEmpty()
            && features().stream().noneMatch(FeatureHtml::hasObjects));
  }

  @Value.Derived
  @Override
  public List<NavigationDTO> getFormats() {
    return rawFormats();
  }

  @Value.Derived
  @Override
  public String getAttribution() {
    String basemapAttribution = super.getAttribution();
    if (Objects.nonNull(rawAttribution())) {
      if (Objects.nonNull(basemapAttribution))
        return String.join(" | ", rawAttribution(), basemapAttribution);
      else return rawAttribution();
    }
    return basemapAttribution;
  }

  @Value.Derived
  @Override
  public Optional<String> getCanonicalUrl() {
    if (!isCollection() && PersistentUri().isPresent()) return PersistentUri();

    URICustomizer canonicalUri = uriBuilder().copy().ensureNoTrailingSlash().clearParameters();

    boolean hasOtherParams = !canonicalUri.isQueryEmpty();
    boolean hasPrevLink =
        Objects.nonNull(metaPagination())
            && metaPagination().stream()
                .anyMatch(navigationDTO -> "prev".equals(navigationDTO.label));

    return !hasOtherParams && (!isCollection() || !hasPrevLink)
        ? Optional.of(canonicalUri.toString())
        : Optional.empty();
  }

  @Value.Derived
  public String getQueryWithoutPage() {
    try {
      List<NameValuePair> query =
          new URIBuilder(RawQuery())
              .getQueryParams().stream()
                  .filter(kvp -> !kvp.getName().equals("offset") && !kvp.getName().equals("limit"))
                  .collect(Collectors.toList());

      if (query.isEmpty()) {
        return "?";
      }
      return '?' + new URIBuilder().setParameters(query).build().getRawQuery() + '&';
    } catch (URISyntaxException e) {
      throw new IllegalStateException(
          String.format("Failed to parse query parameters: '%s'", RawQuery()), e);
    }
  }

  @Value.Derived
  public Function<String, String> getCurrentUrlWithSegment() {
    return segment ->
        uriCustomizer()
            .copy()
            .clearParameters()
            .ensureParameter("f", "html")
            .ensureLastPathSegment(segment)
            .ensureNoTrailingSlash()
            .toString();
  }

  @Value.Derived
  public DecoratedCollection<String> getKeywordsDecorated() {
    return new DecoratedCollection<>(keywords());
  }

  @Value.Derived
  public Function<String, String> getQueryWithout() {
    return without -> {
      List<String> ignore = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(without);

      try {
        List<NameValuePair> query =
            new URIBuilder(RawQuery())
                .getQueryParams().stream()
                    .filter(kvp -> !ignore.contains(kvp.getName().toLowerCase()))
                    .collect(Collectors.toList());

        if (query.isEmpty()) {
          return "?";
        }
        return '?' + new URIBuilder().setParameters(query).build().getRawQuery() + '&';
      } catch (URISyntaxException e) {
        throw new IllegalStateException(
            String.format("Failed to parse query parameters: '%s'", RawQuery()), e);
      }
    };
  }

  @Value.Derived
  public Function<String, String> getCurrentUrlWithSegmentClearParams() {
    return segment ->
        uriBuilder()
            .copy()
            .ensureLastPathSegment(segment)
            .ensureNoTrailingSlash()
            .clearParameters()
            .toString();
  }

  @Value.Derived
  public String Query() {

    return "?" + (uri().getQuery() != null ? uri().getQuery() + "&" : "");
  }

  @Value.Derived
  public String RawQuery() {

    return "?" + (uri().getRawQuery() != null ? uri().getRawQuery() + "&" : "");
  }

  @Value.Derived
  public String getQuery() {

    return "?" + (uri().getQuery() != null ? uri().getQuery() + "&" : "");
  }

  @Value.Derived
  public String getRawQuery() {

    return "?" + (uri().getRawQuery() != null ? uri().getRawQuery() + "&" : "");
  }
}
