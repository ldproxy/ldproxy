/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableMap;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableLink.Builder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@XmlType(propOrder = {"rel", "type", "title", "href", "hreflang", "length", "templated"})
public abstract class Link {

  private static final Map<String, String> LABEL_MAP =
      new ImmutableMap.Builder<String, String>()
          .put("application/geo+json", "GeoJSON")
          .put("application/fg+json", "JSON-FG")
          .put("application/vnd.ogc.fg+json", "JSON-FG")
          .put("application/schema+json", "JSON Schema")
          .put("application/city+json", "CityJSON")
          .put("application/vnd.ogc.city+json", "CityJSON") // legacy, can be removed
          .put("application/city+json-seq", "CityJSON-Seq")
          .put("application/vnd.ogc.city+json-seq", "CityJSON-Seq") // legacy, can be removed
          .put("application/json", "JSON")
          .put("application/ld+json", "JSON-LD")
          .put("model/gltf-binary", "glTF")
          .put("text/html", "HTML")
          .put("text/csv", "CSV")
          .put("application/flatgeobuf", "FlatGeobuf")
          .put("application/gml+xml", "GML")
          .put("application/xml", "XML")
          .put("application/vnd.oai.openapi", "YAML")
          // TODO: this should be removed, I think typeLabel should be settable instead so that it
          // could receive ApiMediaType.label e.g. in FeaturesLinksGenerator
          .put("text/plain", "Debug Tokens")
          .build();

  public static final Comparator<Link> COMPARATOR_LINKS =
      Comparator.comparing(Link::getRel).thenComparing(Link::getHref);

  @XmlAttribute
  public abstract String getRel();

  @Nullable
  @XmlAttribute
  public abstract String getType();

  @Nullable
  @XmlAttribute
  public abstract String getAnchor();

  @Nullable
  @XmlAttribute
  public abstract String getTitle();

  @XmlAttribute
  public abstract String getHref();

  @Nullable
  @XmlAttribute
  public abstract String getHreflang();

  @Nullable
  @XmlAttribute
  public abstract Integer getLength();

  @Nullable
  @XmlAttribute
  public abstract Boolean getTemplated();

  @Nullable
  @XmlTransient
  @JsonProperty("var-base")
  public abstract String getVarBase();

  @JsonIgnore
  @XmlTransient
  @Value.Lazy
  public javax.ws.rs.core.Link getLink() {
    javax.ws.rs.core.Link.Builder link = javax.ws.rs.core.Link.fromUri(getHref());

    if (getRel() != null && !getRel().isEmpty()) {
      link.rel(getRel());
    }
    if (getTitle() != null && !getTitle().isEmpty()) {
      link.title(getTitle());
    }
    if (getType() != null && !getType().isEmpty()) {
      link.type(getType());
    }

    return link.build();
  }

  @JsonIgnore
  @XmlTransient
  @Value.Derived
  public String getTypeLabel() {
    if ("application/vnd.ogc.fg+json;compatibility=geojson".equals(getType())) {
      // hide JSON-FG GeoJSON compatibility
      return "";
    }
    String mediaType =
        Objects.requireNonNullElse(getType(), "").toLowerCase(Locale.ROOT).split(";")[0];
    if (LABEL_MAP.containsKey(mediaType)) {
      return LABEL_MAP.get(mediaType);
    } else if (mediaType.endsWith("+json")) {
      return "JSON";
    } else if (mediaType.endsWith("+yaml")) {
      return "YAML";
    } else if (mediaType.endsWith("+xml")) {
      return "XML";
    }

    return mediaType;
  }
}
