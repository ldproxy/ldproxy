/**
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.ImmutableCollectionsConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class CapabilityCollections implements ApiBuildingBlock {

    @Inject
    public CapabilityCollections() {
    }

    @Override
    public ExtensionConfiguration getDefaultConfiguration() {
        return new ImmutableCollectionsConfiguration.Builder().enabled(true)
                                                              .build();
    }
}