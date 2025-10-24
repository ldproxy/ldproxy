/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.styles.app.MbStyleSpritesDeserializer;
import org.immutables.value.Value;

@JsonDeserialize(using = MbStyleSpritesDeserializer.class)
public sealed interface MbStyleSprites permits MbStyleSingleSprite, MbStyleArrayOfSprites {

  @Value.Derived
  @Value.Auxiliary
  default boolean isTemplated() {
    if (this instanceof MbStyleSingleSprite singleSprite) {
      return singleSprite.getValue().matches("^.*\\{serviceUrl}.*$");
    } else if (this instanceof MbStyleArrayOfSprites arrayOfSprites) {
      return arrayOfSprites.getValue().stream()
          .anyMatch(spriteObject -> spriteObject.getUrl().matches("^.*\\{serviceUrl}.*$"));
    }
    return false;
  }

  default MbStyleSprites withServiceUrl(String serviceUrl) {
    if (this instanceof MbStyleSingleSprite singleSprite) {
      return ImmutableMbStyleSingleSprite.of(
          singleSprite.getValue().replace("{serviceUrl}", serviceUrl));
    } else if (this instanceof MbStyleArrayOfSprites arrayOfSprites) {
      return ImmutableMbStyleArrayOfSprites.of(
          arrayOfSprites.getValue().stream()
              .map(
                  spriteObject ->
                      ImmutableMbStyleSpriteObject.builder()
                          .id(spriteObject.getId())
                          .url(spriteObject.getUrl().replace("{serviceUrl}", serviceUrl))
                          .build())
              .toList());
    }
    return this;
  }
}
