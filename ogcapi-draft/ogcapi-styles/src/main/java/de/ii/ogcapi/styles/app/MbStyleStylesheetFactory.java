/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.styles.domain.MbStyleStylesheet;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityDataStore;
import de.ii.xtraplatform.values.domain.AutoValue;
import de.ii.xtraplatform.values.domain.AutoValueFactory;
import de.ii.xtraplatform.values.domain.ValueFactoryAuto;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class MbStyleStylesheetFactory extends ValueFactoryAuto {

  private final EntityDataStore<EntityData> entityDataStore;

  @Inject
  public MbStyleStylesheetFactory(EntityDataStore<?> entityDataStore) {
    super(MbStyleStylesheet.class);
    this.entityDataStore = (EntityDataStore<EntityData>) entityDataStore;
  }

  @Override
  public Optional<AutoValueFactory<? extends AutoValue, ?, ?>> auto() {
    return Optional.of(new MbStyleStylesheetGenerator(entityDataStore));
  }
}
