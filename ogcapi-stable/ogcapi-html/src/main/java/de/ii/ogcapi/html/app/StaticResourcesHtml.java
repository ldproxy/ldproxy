/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.xtraplatform.web.domain.StaticResources;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class StaticResourcesHtml implements StaticResources {

  @Inject
  StaticResourcesHtml() {}

  @Override
  public String getResourcePath() {
    return "/de/ii/ogcapi/html/assets";
  }

  @Override
  public String getUrlPath() {
    return "/ogcapi-html/assets";
  }
}
