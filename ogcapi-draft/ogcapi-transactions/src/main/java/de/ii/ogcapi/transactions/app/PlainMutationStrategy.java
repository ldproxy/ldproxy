/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.transactions.domain.MutationStrategy;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Default mutation strategy: write actions are executed verbatim against the feature provider
 * session. Binds to {@code TransactionsConfiguration}, so it is enabled exactly for those
 * collections on which the Transactions building block itself is enabled, at priority {@code 0} —
 * the fallback whenever no specialised strategy is registered for a collection.
 */
@Singleton
@AutoBind
public class PlainMutationStrategy implements MutationStrategy {

  @Inject
  public PlainMutationStrategy() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TransactionsConfiguration.class;
  }
}
