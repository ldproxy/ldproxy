/**
 * Copyright 2025 interactive instruments GmbH
 *
 * <p>This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy
 * of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
@Value.Style(deepImmutablesDetection = true, attributeBuilderDetection = true, builder = "new")
@BuildableMapEncodingEnabled
@AutoModule(single = true, encapsulate = true)
package de.ii.ogcapi.collections.schema.validation.domain;

import com.github.azahnen.dagger.annotations.AutoModule;
import de.ii.xtraplatform.entities.domain.maptobuilder.encoding.BuildableMapEncodingEnabled;
import org.immutables.value.Value;
