/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.styles.app.MbStyleStylesheetFactory;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityDataStore;
import de.ii.xtraplatform.values.domain.ValueFactory;
import java.util.Set;

public interface ValueFactories {

  static Set<ValueFactory> factories(EntityDataStore<EntityData> entityDataStore) {

    return ImmutableSet.<ValueFactory>builder()
        .add(new MbStyleStylesheetFactory(entityDataStore))
        .build();
  }
}
