/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.xml;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import de.ii.ogcapi.collections.domain.OgcApiCollection;
import de.ii.ogcapi.common.domain.OgcApiExtent;
import de.ii.ogcapi.foundation.domain.Link;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "Collection", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
@XmlType(propOrder = {"id", "title", "description", "links", "extent"})
public class OgcApiCollectionXml {
  private final OgcApiCollection ogcApiCollection;

  public OgcApiCollectionXml(OgcApiCollection ogcApiCollection) {
    this.ogcApiCollection = ogcApiCollection;
  }

  @XmlElement(name = "Id", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getId() {
    return ogcApiCollection.getId();
  }

  @XmlElement(name = "Title", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getTitle() {
    return ogcApiCollection.getTitle().orElse(null);
  }

  @XmlElement(name = "Description", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getDescription() {
    return ogcApiCollection.getDescription().orElse(null);
  }

  @XmlElement(name = "Extent", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public OgcApiExtentXml getExtent() {
    OgcApiExtent extent = ogcApiCollection.getExtent().orElse(null);
    if (Objects.nonNull(extent)) {
      return new OgcApiExtentXml(extent);
    }
    return null;
  }

  @XmlElement(name = "atom:link", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  @JacksonXmlElementWrapper(useWrapping = false)
  public List<Link> getLinks() {
    return ogcApiCollection.getLinks();
  }
}
