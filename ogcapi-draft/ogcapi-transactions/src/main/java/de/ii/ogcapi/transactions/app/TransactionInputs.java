/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.transactions.app.CommandHandlerTransactions.QueryInputTransaction;
import java.io.InputStream;

public interface TransactionInputs {

  QueryInputTransaction createQueryInput(
      OgcApi api,
      String contentTypeHeader,
      String contentCrsHeader,
      String mutationDatetimeHeader,
      HeaderPrefer.Handling handling,
      HeaderPrefer.Return ret,
      InputStream transactionDocument);
}
