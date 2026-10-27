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
import java.util.Optional;
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
        null,
        Optional.of(3600));
  }

  String getApiId();

  /**
   * Path of the transaction document in the resource store, relative to {@code
   * jobs/feature-transaction}. It may also name a folder: then all documents in it are applied in
   * the order of their names, each as a transaction of its own, and the first rejected document
   * stops the job. Documents may be compressed with gzip.
   */
  String getDocumentPath();

  String getMediaType();

  @Nullable
  String getCrs();

  @Nullable
  String getMutationDatetime();

  HeaderPrefer.Handling getHandlingPrefer();

  HeaderPrefer.Return getReturnPrefer();

  /**
   * Write the response document to {@code <api>/result_<job>.json} instead of returning it as the
   * job output; the path is the job output {@code resultPath}. The document is also written if the
   * transaction is rejected. For a folder, the response documents are always written to the result
   * folder of the job, named after the documents ({@code 001.xml.gz} → {@code
   * <api>/result_<job>/001.result.json}), and reported as the job output {@code resultPaths}.
   */
  boolean getResultAsFile();
}
