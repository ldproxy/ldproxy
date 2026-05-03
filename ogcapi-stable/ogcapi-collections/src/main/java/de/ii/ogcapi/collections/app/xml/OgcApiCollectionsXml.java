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
import de.ii.ogcapi.collections.domain.Collections;
import de.ii.ogcapi.common.domain.xml.OgcApiXml;
import de.ii.ogcapi.foundation.domain.Link;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@JsonInclude(Include.NON_NULL)
@XmlRootElement(name = "Collections", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
@XmlType(propOrder = {"links", "collections"})
public class OgcApiCollectionsXml implements OgcApiXml {

  private final Collections collections;

  public OgcApiCollectionsXml(Collections collections) {
    this.collections = collections;
  }

  @XmlElement(name = "atom:link", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  @JacksonXmlElementWrapper(useWrapping = false)
  public List<Link> getLinks() {
    return collections.getLinks();
  }

  @XmlElement(name = "Collection", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  @JacksonXmlElementWrapper(useWrapping = false)
  public List<OgcApiCollectionXml> getCollections() {
    return collections.getCollections().stream()
        .map(OgcApiCollectionXml::new)
        .collect(Collectors.toList());
  }
}
