/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.ogcapi.styles.domain.StyleMetadata;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import org.immutables.value.Value.Style;

@Value.Immutable
@Style(builder = "new")
public abstract class StyleMetadataView extends OgcApiView {

  public abstract StyleMetadata metadata();

  public abstract URICustomizer uriCustomizer();

  public abstract I18n i18n();

  public abstract Optional<Locale> language();

  @Value.Derived
  public String none() {
    return i18n().get("none", language());
  }

  @Value.Derived
  public String metadataTitle() {
    return i18n().get("metadataTitle", language());
  }

  @Value.Derived
  public String specificationTitle() {
    return i18n().get("specificationTitle", language());
  }

  @Value.Derived
  public String versionTitle() {
    return i18n().get("versionTitle", language());
  }

  @Value.Derived
  public String pointOfContactTitle() {
    return i18n().get("pointOfContactTitle", language());
  }

  @Value.Derived
  public String datesTitle() {
    return i18n().get("datesTitle", language());
  }

  @Value.Derived
  public String creationTitle() {
    return i18n().get("creationTitle", language());
  }

  @Value.Derived
  public String publicationTitle() {
    return i18n().get("publicationTitle", language());
  }

  @Value.Derived
  public String revisionTitle() {
    return i18n().get("revisionTitle", language());
  }

  @Value.Derived
  public String validTillTitle() {
    return i18n().get("validTillTitle", language());
  }

  @Value.Derived
  public String receivedOnTitle() {
    return i18n().get("receivedOnTitle", language());
  }

  @Value.Derived
  public String stylesheetsTitle() {
    return i18n().get("stylesheetsTitle", language());
  }

  @Value.Derived
  public String nativeTitle() {
    return i18n().get("nativeTitle", language());
  }

  @Value.Derived
  public String trueTitle() {
    return i18n().get("trueTitle", language());
  }

  @Value.Derived
  public String falseTitle() {
    return i18n().get("falseTitle", language());
  }

  @Value.Derived
  public String layersTitle() {
    return i18n().get("layersTitle", language());
  }

  @Value.Derived
  public String idTitle() {
    return i18n().get("idTitle", language());
  }

  @Value.Derived
  public String titleTitle() {
    return i18n().get("titleTitle", language());
  }

  @Value.Derived
  public String keywordsTitle() {
    return i18n().get("keywordsTitle", language());
  }

  @Value.Derived
  public String additionalLinksTitle() {
    return i18n().get("additionalLinksTitle", language());
  }

  @Value.Derived
  public String layerTypeTitle() {
    return i18n().get("layerTypeTitle", language());
  }

  @Value.Derived
  public String dataTypeTitle() {
    return i18n().get("dataTypeTitle", language());
  }

  @Value.Derived
  public String attributesTitle() {
    return i18n().get("attributesTitle", language());
  }

  @Value.Derived
  public String sampleDataTitle() {
    return i18n().get("sampleDataTitle", language());
  }

  @Value.Derived
  public String requiredTitle() {
    return i18n().get("requiredTitle", language());
  }

  @Value.Derived
  public String mediaTypesTitle() {
    return i18n().get("mediaTypesTitle", language());
  }

  @Value.Derived
  public String thumbnailTitle() {
    return i18n().get("thumbnailTitle", language());
  }

  public StyleMetadataView() {
    super("styleMetadata.mustache");
  }

  public boolean hasLayers() {
    return !metadata().getLayers().isEmpty();
  }

  public Link getThumbnail() {
    return rawLinks().stream()
        .filter(link -> Objects.equals(link.getRel(), "preview"))
        .findFirst()
        .orElse(null);
  }

  public List<Link> getAdditionalLinks() {
    return rawLinks().stream()
        .filter(link -> !link.getRel().matches("^(?:self|alternate|preview)$"))
        .collect(Collectors.toList());
  }
}
