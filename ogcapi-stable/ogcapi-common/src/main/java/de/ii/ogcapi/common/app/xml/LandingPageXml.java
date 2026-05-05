/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.common.domain.xml.OgcApiXml;
import de.ii.ogcapi.foundation.domain.Link;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;

@XmlRootElement(name = "LandingPage", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
@XmlType(propOrder = {"title", "description", "links"})
public class LandingPageXml implements OgcApiXml {

  private List<Link> links;
  private String title;
  private String description;

  public LandingPageXml() {}

  public LandingPageXml(List<Link> links, String title, String description) {
    this.links = ImmutableList.copyOf(links);
    this.title = title;
    this.description = description;
  }

  @XmlElement(name = "Title", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getTitle() {
    return title;
  }

  @XmlElement(name = "Description", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getDescription() {
    return description;
  }

  @XmlElement(name = "atom:link", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  @JacksonXmlElementWrapper(useWrapping = false)
  public List<Link> getLinks() {
    return links;
  }
}
