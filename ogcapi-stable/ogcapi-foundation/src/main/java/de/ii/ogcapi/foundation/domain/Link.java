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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableLink.Builder.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@XmlType(propOrder = {"rel", "type", "profile", "title", "href", "hreflang", "length", "templated"})
public abstract class Link {

  // TODO remove once mediaType is set instead of type in all builders
  private static final Map<String, String> LABEL_MAP =
      new ImmutableMap.Builder<String, String>()
          .put("application/geo+json", "GeoJSON")
          .put("application/schema+json", "JSON Schema")
          .put("application/city+json", "CityJSON")
          .put("application/city+json-seq", "CityJSON-Seq")
          .put("application/json", "JSON")
          .put("application/ld+json", "JSON-LD")
          .put("model/gltf-binary", "glTF")
          .put("text/html", "HTML")
          .put("text/csv", "CSV")
          .put("application/flatgeobuf", "FlatGeobuf")
          .put("application/gml+xml", "GML")
          .put("application/xml", "XML")
          .put("application/vnd.oai.openapi", "YAML")
          .put("text/plain", "Debug Tokens")
          .build();

  public static final Comparator<Link> COMPARATOR_LINKS =
      Comparator.comparing(Link::getRel).thenComparing(Link::getHref);

  @XmlAttribute
  public abstract String getRel();

  @Nullable
  @XmlAttribute
  @Value.Default
  public String getType() {
    if (getMediaType() != null) {
      return getMediaType().type().toString();
    }
    return null;
  }

  @Nullable
  @JsonIgnore
  @XmlTransient
  public abstract ApiMediaType getMediaType();

  @Nullable
  @XmlAttribute
  @Value.Default
  public String getProfile() {
    return getProfiles().isEmpty()
        ? null
        : getProfiles().stream().map(ProfileExtension::getUri).collect(Collectors.joining(" "));
  }

  @JsonIgnore
  @XmlTransient
  public abstract List<Profile> getProfiles();

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
    if (getProfile() != null && !getProfile().isEmpty()) {
      link.param("profile", getProfile());
    }
    if (getHreflang() != null && !getHreflang().isEmpty()) {
      link.param("hreflang", getHreflang());
    }
    if (getAnchor() != null && !getAnchor().isEmpty()) {
      link.param("anchor", getAnchor());
    }
    if (getVarBase() != null && !getVarBase().isEmpty()) {
      link.param("var-base", getVarBase());
    }
    if (getLength() != null) {
      link.param("length", String.valueOf(getLength()));
    }

    return link.build();
  }

  @JsonIgnore
  @XmlTransient
  @Value.Derived
  public String getTypeLabel() {
    if (!getProfiles().isEmpty()) {
      return getProfiles().stream().map(Profile::getLabel).collect(Collectors.joining("/"));
    }
    if (getMediaType() != null) {
      return getMediaType().label();
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
