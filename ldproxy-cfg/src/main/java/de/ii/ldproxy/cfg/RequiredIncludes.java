/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.fasterxml.jackson.databind.ext.Java7HandlersImpl;
import java.util.List;
import org.apache.hc.core5.http.NameValuePair;

class RequiredIncludes {

  private final Java7HandlersImpl java7Handlers;
  private final List<NameValuePair> nameValuePairs;

  RequiredIncludes() {
    this.java7Handlers = new Java7HandlersImpl();
    this.nameValuePairs = List.of();
  }
}
