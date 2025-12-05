/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.html;

import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.domain.OgcApiCollection;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
public abstract class OgcApiCollectionsView extends OgcApiView {

  public abstract List<OgcApiCollection> rawCollections();

  public abstract Optional<Locale> language();

  abstract Optional<BoundingBox> spatialExtent();

  abstract boolean showCollectionDescriptions();

  abstract I18n i18n();

  abstract Optional<String> dataSourceUrl();

  abstract Optional<String> keywords();

  public abstract List<String> crs();

  abstract Optional<ApiMetadata> metadata();

  @Value.Derived
  String collectionsTitle() {
    return i18n().get("collectionsTitle", language());
  }

  @Value.Derived
  String supportedCrsTitle() {
    return i18n().get("supportedCrsTitle", language());
  }

  @Value.Derived
  String metadataTitle() {
    return i18n().get("metadataTitle", language());
  }

  @Value.Derived
  String licenseTitle() {
    return i18n().get("licenseTitle", language());
  }

  @Value.Derived
  String downloadTitle() {
    return i18n().get("downloadTitle", language());
  }

  @Value.Derived
  String additionalLinksTitle() {
    return i18n().get("additionalLinksTitle", language());
  }

  @Value.Derived
  String expertInformationTitle() {
    return i18n().get("expertInformationTitle", language());
  }

  @Value.Derived
  String none() {
    return i18n().get("none", language());
  }

  @Value.Derived
  String moreInformation() {
    return i18n().get("moreInformation", language());
  }

  @Value.Derived
  boolean hasGeometry() {
    return spatialExtent().isPresent();
  }

  @Value.Derived
  public boolean hasDownload() {
    return !getDownloadLinks().isEmpty();
  }

  @Value.Derived
  public boolean hasLicense() {
    return !getLicenseLinks().isEmpty();
  }

  @Value.Derived
  public boolean hasMetadata() {
    return !getMetadataLinks().isEmpty();
  }

  public OgcApiCollectionsView() {
    super("collections.mustache");
  }

  public List<Link> getLinks() {
    return rawLinks().stream()
        .filter(
            link -> !link.getRel().matches("^(?:self|alternate|describedby|license|enclosure)$"))
        .collect(Collectors.toList());
  }

  public List<Link> getMetadataLinks() {
    return rawLinks().stream()
        .filter(link -> link.getRel().matches("^(?:describedby)$"))
        .collect(Collectors.toList());
  }

  public List<Link> getLicenseLinks() {
    return rawLinks().stream()
        .filter(link -> link.getRel().matches("^(?:license)$"))
        .collect(Collectors.toList());
  }

  public List<Link> getDownloadLinks() {
    return rawLinks().stream()
        .filter(link -> link.getRel().matches("^(?:enclosure)$"))
        .collect(Collectors.toList());
  }

  public List<Map<String, String>> getCollections() {

    Comparator<OgcApiCollection> byTitle =
        Comparator.comparing(collection -> collection.getTitle().orElse(collection.getId()));

    return rawCollections().stream()
        .sorted(byTitle)
        .map(
            collection ->
                ImmutableMap.of(
                    "title",
                    collection.getTitle().orElse(collection.getId()),
                    "description",
                    showCollectionDescriptions() ? collection.getDescription().orElse("") : "",
                    "id",
                    collection.getId(),
                    "hrefcollection",
                    collection.getLinks().stream()
                        .filter(link -> link.getRel().equalsIgnoreCase("self"))
                        .findFirst()
                        .map(Link::getHref)
                        .orElse(""),
                    "hrefitems",
                    collection.getLinks().stream()
                        .filter(link -> collection.getDataRel().equalsIgnoreCase(link.getRel()))
                        .filter(link -> "text/html".equalsIgnoreCase(link.getType()))
                        .findFirst()
                        .map(Link::getHref)
                        .orElse(""),
                    "itemType",
                    i18n().get(collection.getItemType().orElse("unknown"), language()),
                    "itemCount",
                    collection.getItemCount().map(Object::toString).orElse("")))
        .collect(Collectors.toList());
  }
}
