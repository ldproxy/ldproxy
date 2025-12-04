/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.html;

import de.ii.ogcapi.collections.domain.OgcApiCollection;
import de.ii.ogcapi.common.domain.OgcApiDatasetView;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.html.domain.ImmutableMapClient;
import de.ii.ogcapi.html.domain.ImmutableStyle;
import de.ii.ogcapi.html.domain.MapClient;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
public abstract class OgcApiCollectionView extends OgcApiDatasetView {

  public OgcApiCollectionView() {
    super("collection.mustache");
  }

  public abstract OgcApiCollection collection();

  public abstract I18n i18n();

  @Value.Derived
  public Optional<Long> itemCount() {
    return collection().getItemCount();
  }

  @Value.Derived
  public boolean spatialSearch() {
    return false;
  }

  @Value.Derived
  public List<String> crs() {
    return collection().getCrs();
  }

  public abstract boolean hasGeometry();

  @Nullable
  @Value.Derived
  public String storageCrs() {
    return collection().getStorageCrs().orElse(null);
  }

  public abstract Optional<ApiMetadata> metadata();

  @Value.Derived
  public Link items() {
    return collection().getLinks().stream()
        .filter(link -> collection().getDataRel().equalsIgnoreCase(link.getRel()))
        .filter(link -> "text/html".equalsIgnoreCase(link.getType()))
        .findFirst()
        .orElse(null);
  }

  @Value.Derived
  public Optional<String> defaultStyle() {
    return collection().getDefaultStyle();
  }

  @Value.Derived
  public String itemType() {
    return i18n().get(collection().getItemType().orElse("feature"), language());
  }

  @Value.Derived
  public String itemTypeTitle() {
    return i18n().get("itemTypeTitle", language());
  }

  @Value.Derived
  public String itemCountTitle() {
    return i18n().get("itemCountTitle", language());
  }

  @Value.Derived
  public String dataTitle() {
    return i18n().get("dataTitle", language());
  }

  @Value.Derived
  public String metadataTitle() {
    return i18n().get("metadataTitle", language());
  }

  @Value.Derived
  public String licenseTitle() {
    return i18n().get("licenseTitle", language());
  }

  @Value.Derived
  public String downloadTitle() {
    return i18n().get("downloadTitle", language());
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
  public String supportedCrsTitle() {
    return i18n().get("supportedCrsTitle", language());
  }

  @Value.Derived
  public String storageCrsTitle() {
    return i18n().get("storageCrsTitle", language());
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
  public String defaultStyleTitle() {
    return i18n().get("defaultStyleTitle", language());
  }

  @Value.Derived
  public String styleInfosTitle() {
    return i18n().get("styleInfosTitle", language());
  }

  @Value.Derived
  public String collectionInformationTitle() {
    return i18n().get("collectionInformationTitle", language());
  }

  @Value.Derived
  public String mainLinksTitle() {
    return i18n().get("mainLinksTitle", language());
  }

  @Value.Derived
  public boolean isDataset() {
    return Objects.nonNull(htmlConfig()) ? htmlConfig().getSchemaOrgEnabled() : false;
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

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  public boolean hasMetadata() {
    return !getMetadataLinks().isEmpty();
  }

  public List<Link> getMetadataLinks() {
    return rawLinks().stream()
        .filter(link -> link.getRel().matches("^(?:describedby)$"))
        .collect(Collectors.toList());
  }

  public boolean hasLicense() {
    return !getLicenseLinks().isEmpty();
  }

  public List<Link> getLicenseLinks() {
    return rawLinks().stream()
        .filter(link -> link.getRel().matches("^(?:license)$"))
        .collect(Collectors.toUnmodifiableList());
  }

  public boolean hasDownload() {
    return !getDownloadLinks().isEmpty();
  }

  public List<Link> getDownloadLinks() {
    return rawLinks().stream()
        .filter(link -> link.getRel().matches("^(?:enclosure)$"))
        .collect(Collectors.toUnmodifiableList());
  }

  public List<Link> getDistributionLinks() {
    return rawLinks().stream()
        .filter(
            link ->
                Objects.equals(link.getRel(), "items")
                    || Objects.equals(link.getRel(), "enclosure"))
        .filter(link -> !"text/html".equals(link.getType()))
        .collect(Collectors.toUnmodifiableList());
  }

  public List<Link> getTiles() {
    return rawLinks().stream()
        .filter(
            link ->
                link.getRel().startsWith("http://www.opengis.net/def/rel/ogc/1.0/tilesets")
                    || link.getRel().startsWith("http://www.opengis.net/def/rel/ogc/0.0/tileset-"))
        .filter(link -> "text/html".equalsIgnoreCase(link.getType()))
        .collect(Collectors.toUnmodifiableList());
  }

  public Optional<Link> getStyles() {
    return rawLinks().stream()
        .filter(
            link -> Objects.equals(link.getRel(), "http://www.opengis.net/def/rel/ogc/1.0/styles"))
        .findFirst();
  }

  public Optional<Link> getMap() {
    return rawLinks().stream().filter(link -> Objects.equals(link.getRel(), "ldp-map")).findFirst();
  }

  public Optional<String> getSchemaOrgDataset() {
    // for cases with a single collection, that collection is not reported as a sub-dataset
    return apiData().getCollections().size() > 1
        ? Optional.of(
            getSchemaOrgDataset(
                apiData(),
                Optional.of(apiData().getCollections().get(collection().getId())),
                uriCustomizer()
                    .clearParameters()
                    .removeLastPathSegments(2)
                    .ensureNoTrailingSlash()
                    .copy(),
                false))
        : Optional.empty();
  }
}
