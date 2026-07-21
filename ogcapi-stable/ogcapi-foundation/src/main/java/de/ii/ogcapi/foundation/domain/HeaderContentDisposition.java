/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface HeaderContentDisposition {
  boolean getAttachment();

  Optional<String> getFilename();

  static HeaderContentDisposition of() {
    return new ImmutableHeaderContentDisposition.Builder().attachment(false).build();
  }

  static HeaderContentDisposition of(boolean attachment) {
    return new ImmutableHeaderContentDisposition.Builder().attachment(attachment).build();
  }

  static HeaderContentDisposition of(String filename) {
    return new ImmutableHeaderContentDisposition.Builder()
        .attachment(false)
        .filename(filename)
        .build();
  }

  static HeaderContentDisposition of(String filename, boolean attachment) {
    return new ImmutableHeaderContentDisposition.Builder()
        .attachment(attachment)
        .filename(filename)
        .build();
  }
}
