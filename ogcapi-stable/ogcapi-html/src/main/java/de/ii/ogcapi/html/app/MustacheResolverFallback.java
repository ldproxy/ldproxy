/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.html.domain.OgcApiView;
import de.ii.xtraplatform.web.domain.PartialMustacheResolver;
import de.ii.xtraplatform.web.domain.PerClassMustacheResolver;
import java.io.Reader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Reuse the per class loader, but switch to the HTML module context. */
@Singleton
@AutoBind
public class MustacheResolverFallback extends PerClassMustacheResolver
    implements PartialMustacheResolver {

  @Inject
  MustacheResolverFallback() {}

  // TODO sortPriority is higher than PerClassMustacheResolver; in case of a name collision or an
  // attempt to overload
  //      a template that exists in the HTML module locally, the HTML module template will always be
  // used

  @Override
  public int getSortPriority() {
    return 100;
  }

  @Override
  public boolean canResolve(String templateName, Class<?> viewClass) {
    return super.canResolve(templateName, OgcApiView.class);
  }

  @Override
  public Reader getReader(String templateName, Class<?> viewClass) {
    return super.getReader(templateName, OgcApiView.class);
  }
}
