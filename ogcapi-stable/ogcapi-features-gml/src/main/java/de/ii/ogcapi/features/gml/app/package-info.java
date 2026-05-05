/**
 * Copyright 2022 interactive instruments GmbH
 *
 * <p>This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy
 * of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
/**
 * @author zahnen
 */
@XmlSchema(
    namespace = "http://www.opengis.net/ogcapi-features-1/1.0",
    elementFormDefault = XmlNsForm.QUALIFIED,
    xmlns = {
      @XmlNs(prefix = "core", namespaceURI = "http://www.opengis.net/ogcapi-features-1/1.0"),
      @XmlNs(prefix = "atom", namespaceURI = "http://www.w3.org/2005/Atom"),
      @XmlNs(prefix = "xsi", namespaceURI = "http://www.w3.org/2001/XMLSchema-instance")
    })
package de.ii.ogcapi.features.gml.app;

import jakarta.xml.bind.annotation.XmlNs;
import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
