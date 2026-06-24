/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.transactions.domain.TransactionParser;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import org.immutables.value.Value;

public interface CommandHandlerTransactions extends Volatile2 {

  Response processTransaction(QueryInputTransaction queryInput, ApiRequestContext requestContext);

  @Value.Immutable
  interface QueryInputTransaction {

    TransactionParser getParser();

    InputStream getRequestBody();

    MediaType getContentType();

    TransactionsConfiguration getConfig();

    EpsgCrs getRequestCrs();

    HeaderPrefer.Handling getHandling();

    HeaderPrefer.Return getReturnPreference();

    /**
     * Parsed {@code OGC-Mutation-Datetime} request header; empty when the client did not send it.
     */
    Optional<Instant> getMutationDatetime();
  }
}
