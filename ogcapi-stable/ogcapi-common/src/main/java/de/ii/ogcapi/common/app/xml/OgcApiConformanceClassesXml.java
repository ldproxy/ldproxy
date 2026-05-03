/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import de.ii.ogcapi.common.domain.ConformanceDeclaration;
import de.ii.ogcapi.common.domain.xml.OgcApiXml;
import java.util.List;
import java.util.stream.Collectors;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ConformsTo", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
public class OgcApiConformanceClassesXml implements OgcApiXml {
  private final ConformanceDeclaration ogcApiConformanceDeclaration;

  public OgcApiConformanceClassesXml(ConformanceDeclaration ogcApiConformanceDeclaration) {
    this.ogcApiConformanceDeclaration = ogcApiConformanceDeclaration;
  }

  @XmlElement(name = "atom:link", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  @JacksonXmlElementWrapper(useWrapping = false)
  public List<OgcApiLinkXml> getConformsToAsXml() {
    return ogcApiConformanceDeclaration.getConformsTo().stream()
        .map(OgcApiLinkXml::new)
        .collect(Collectors.toList());
  }
}
