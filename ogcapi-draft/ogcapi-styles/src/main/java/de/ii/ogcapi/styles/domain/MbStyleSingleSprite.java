/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.ii.ogcapi.styles.app.MbStyleSpritesSerializer;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(using = MbStyleSpritesSerializer.class)
public non-sealed interface MbStyleSingleSprite extends MbStyleSprites {
  @Value.Parameter
  String getValue();
}
