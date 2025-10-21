/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.base.domain.AppConfiguration;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.Constants.ENV;
import java.net.URI;
import java.nio.file.Path;

public class AppContextTest implements AppContext {

  @Override
  public String getName() {
    return "ldproxy-cfg";
  }

  @Override
  public String getVersion() {
    return "";
  }

  @Override
  public ENV getEnvironment() {
    return null;
  }

  @Override
  public Path getDataDir() {
    return null;
  }

  @Override
  public Path getTmpDir() {
    return null;
  }

  @Override
  public AppConfiguration getConfiguration() {
    return new AppConfigurationTest();
  }

  @Override
  public URI getUri() {
    return URI.create("http://localhost:7080");
  }

  @Override
  public String getInstanceName() {
    return "";
  }
}
