/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import jakarta.validation.constraints.NotNull;

public class ArraySchema extends io.swagger.v3.oas.models.media.ArraySchema {
  public ArraySchema(
      Class<?> clazz,
      @NotNull Class<?> componentClazz,
      @NotNull ClassSchemaCache classSchemaCache) {
    super();
    this.items(classSchemaCache.getSchema(componentClazz, clazz));
  }
}
