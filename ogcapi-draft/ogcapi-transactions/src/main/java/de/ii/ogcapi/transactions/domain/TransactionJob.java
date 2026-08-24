/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import de.ii.xtraplatform.xtralink.domain.JobInputs;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableTransactionJob.Builder.class)
public interface TransactionJob extends JobInputs {

  String KIND = "feature-transaction";
  String LABEL = "Feature transaction";

  static JobConfiguration of(
      String apiId,
      Path documentPath,
      String mediaType,
      @Nullable String crs,
      @Nullable String mutationDatetime,
      HeaderPrefer.Handling handlingPrefer,
      HeaderPrefer.Return returnPrefer,
      boolean resultAsFile) {
    ImmutableTransactionJob transactionJob =
        new ImmutableTransactionJob.Builder()
            .apiId(apiId)
            .documentPath(documentPath.toString())
            .mediaType(mediaType)
            .crs(crs)
            .mutationDatetime(mutationDatetime)
            .handlingPrefer(handlingPrefer)
            .returnPrefer(returnPrefer)
            .resultAsFile(resultAsFile)
            .build();
    return Jobs.create(
        KIND,
        1000,
        LABEL,
        String.format(" (Document: %s)", documentPath),
        transactionJob,
        new JobContextEntity(apiId),
        null);
  }

  String getApiId();

  String getDocumentPath();

  String getMediaType();

  @Nullable
  String getCrs();

  @Nullable
  String getMutationDatetime();

  HeaderPrefer.Handling getHandlingPrefer();

  HeaderPrefer.Return getReturnPrefer();

  boolean getResultAsFile();
}
