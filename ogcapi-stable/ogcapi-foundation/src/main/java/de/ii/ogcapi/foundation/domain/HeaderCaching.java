/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import java.util.Date;
import java.util.Optional;
import jakarta.ws.rs.core.EntityTag;
import org.immutables.value.Value;

@Value.Immutable
public interface HeaderCaching {
  Optional<Date> getLastModified();

  Optional<EntityTag> getEtag();

  Optional<String> cacheControl();

  Optional<Date> expires();

  static HeaderCaching of(Date lastModified, EntityTag etag, QueryInput queryInput) {
    return new ImmutableHeaderCaching.Builder()
        .lastModified(Optional.ofNullable(lastModified))
        .etag(Optional.ofNullable(etag))
        .cacheControl(queryInput.getCacheControl())
        .expires(queryInput.getExpires())
        .build();
  }
}
