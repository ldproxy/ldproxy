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

  /**
   * Strict-mode envelope validation hook. Called by {@code EndpointTransactions} before {@link
   * #parse} when the client sent {@code Prefer: handling=strict}; the endpoint buffers the full
   * request body and passes it here so the parser can validate it against its own envelope schema
   * (JSON Schema for {@code application/ogc-tx+json}, XSD for {@code wfs:Transaction}, …) without
   * consuming the stream the parser will later read from. Per-feature payload validation is not in
   * scope here — that runs inside the executor against the per-format {@code
   * FeatureFormatExtension.validate}.
   *
   * <p>Default: no-op. Override in parsers that have a schema to enforce.
   *
   * @param body the buffered request body
   * @param mediaType media type from the {@code Content-Type} header
   * @throws IllegalArgumentException if the envelope violates this parser's schema. The endpoint
   *     maps this to {@code 400 Bad Request}.
   */
  default void validateEnvelope(byte[] body, MediaType mediaType) {}
}
