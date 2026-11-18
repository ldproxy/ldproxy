/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.tiles.domain.TileSetsLinkScope.Level;
import de.ii.ogcapi.tiles.domain.TileSetsLinkScope.TileType;
import java.util.Optional;
import org.immutables.value.Value;

/** A link for the tileset lists, optionally restricted to some of them. */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDeserialize(builder = ImmutableTileSetsLink.Builder.class)
public abstract class TileSetsLink extends Link {

  /**
   * @langEn Restricts the tileset lists that receive the link. Without a scope, every tileset list
   *     receives the link.
   * @langDe Beschränkt die Listen von Kachelsätzen, die den Link erhalten. Ohne Angabe erhält jede
   *     Liste von Kachelsätzen den Link.
   * @default null
   * @since v4.9
   */
  public abstract Optional<TileSetsLinkScope> getScope();

  public boolean appliesTo(Level level, Optional<TileType> tileType, Optional<String> styleId) {
    return getScope().map(scope -> scope.appliesTo(level, tileType, styleId)).orElse(true);
  }

  /** The link as it is published, without the scope. */
  public Link toLink() {
    return new ImmutableLink.Builder().from(this).build();
  }
}
