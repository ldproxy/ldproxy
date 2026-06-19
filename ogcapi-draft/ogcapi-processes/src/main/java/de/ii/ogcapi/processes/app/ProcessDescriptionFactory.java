/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.processes.domain.ProcessDescriptionData;
import de.ii.xtraplatform.values.domain.ValueFactoryAuto;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@AutoBind
public class ProcessDescriptionFactory extends ValueFactoryAuto {

  @Inject
  protected ProcessDescriptionFactory() {
    super(ProcessDescriptionData.class);
  }
}
