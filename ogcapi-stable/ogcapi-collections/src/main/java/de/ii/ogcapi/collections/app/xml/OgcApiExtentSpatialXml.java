/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(propOrder = {"lowerCorner", "upperCorner"})
public class OgcApiExtentSpatialXml {

  @XmlAttribute public String crs = null;

  private String LowerCorner = null;
  private String UpperCorner = null;

  public OgcApiExtentSpatialXml() {}

  public OgcApiExtentSpatialXml(String lowerCorner, String upperCorner) {
    this.LowerCorner = lowerCorner;
    this.UpperCorner = upperCorner;
    this.crs = "http://www.opengis.net/def/crs/OGC/1.3/CRS84";
  }

  public OgcApiExtentSpatialXml(String lowerCorner, String upperCorner, String crs) {
    this.LowerCorner = lowerCorner;
    this.UpperCorner = upperCorner;
    this.crs = crs;
  }

  @XmlElement(name = "LowerCorner", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getLowerCorner() {
    return LowerCorner;
  }

  @XmlElement(name = "UpperCorner", namespace = "http://www.opengis.net/ogcapi-features-1/1.0")
  public String getUpperCorner() {
    return UpperCorner;
  }
}
