/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.xml;

import jakarta.xml.bind.annotation.XmlAttribute;

public interface OgcApiXml {

  @XmlAttribute(name = "service")
  default String getService() {
    return "OGCAPI-Features";
  }

  @XmlAttribute(name = "version")
  default String getVersion() {
    return "1.0.0";
  }

  @XmlAttribute(name = "xmlns:atom")
  default String getNsAtom() {
    return "http://www.w3.org/2005/Atom";
  }

  @XmlAttribute(name = "xmlns:xsi")
  default String getNsXsi() {
    return "http://www.w3.org/2001/XMLSchema-instance";
  }

  @XmlAttribute(name = "xsi:schemaLocation")
  default String getSchemaLocation() {
    return "http://www.opengis.net/ogcapi-features-1/1.0 http://schemas.opengis.net/ogcapi/features/part1/1.0/xml/core.xsd";
  }
}
