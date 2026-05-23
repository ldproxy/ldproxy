/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;

/** Parses a transaction request body into the in-memory {@link Transaction} model. */
@AutoMultiBind
public interface TransactionParser {

  /**
   * @return true if this parser handles the given request body media type
   */
  boolean canParse(MediaType mediaType);

  /**
   * @param body request body input stream (caller closes)
   * @param mediaType media type from the {@code Content-Type} header (may carry parameters such as
   *     {@code profile})
   * @return parsed transaction
   * @throws IllegalArgumentException if the body is malformed or violates the transaction schema
   */
  Transaction parse(InputStream body, MediaType mediaType);
}
