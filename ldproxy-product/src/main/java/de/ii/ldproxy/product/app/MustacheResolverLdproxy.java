/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.product.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.xtraplatform.web.domain.PartialMustacheResolver;
import de.ii.xtraplatform.web.domain.PerClassMustacheResolver;
import java.io.Reader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class MustacheResolverLdproxy extends PerClassMustacheResolver
    implements PartialMustacheResolver {

  @Inject
  MustacheResolverLdproxy() {
    super();
  }

  @Override
  public int getSortPriority() {
    return 900;
  }

  @Override
  public boolean canResolve(String templateName, Class<?> viewClass) {
    return super.canResolve(templateName, this.getClass());
  }

  @Override
  public Reader getReader(String templateName, Class<?> viewClass) {
    return super.getReader(templateName, this.getClass());
  }
}
