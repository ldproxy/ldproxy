/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * The {@code Prefer} header on {@code POST /transactions} when asynchronous execution is enabled;
 * {@link HeaderPreferTransaction} documents the header when it is not.
 */
@Singleton
@AutoBind
public class HeaderPreferTransactionAsync extends HeaderPrefer {

  private final Schema<?> schema =
      new StringSchema().example("respond-async, wait=10, return=minimal");

  @Inject
  HeaderPreferTransactionAsync(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "PreferTransactionAsync";
  }

  @Override
  public String getDescription() {
    return "Controls how the server processes the transaction request. Multiple preferences are "
        + "separated by commas. "
        + "'return=representation' (the default) returns the full Transaction Response document "
        + "with per-action results. 'return=minimal' returns the Transaction Response without the "
        + "per-action details. 'return=none' returns 204 No Content on success (a Transaction "
        + "Response is still returned when the transaction failed, so that exceptions can be "
        + "reported). Malformed transaction envelopes are rejected while parsing. "
        + "'handling=strict' validates each feature payload against its schema before any provider "
        + "write. 'handling=lenient' (the default) skips feature schema validation and only fails "
        + "on malformed requests or errors raised by the provider. 'respond-async' executes the "
        + "transaction asynchronously as a job; the response is 202 Accepted with a Location "
        + "header that points to the job resource. 'wait' (a non-negative integer, in seconds), "
        + "in combination with 'respond-async', delays the response for up to the requested "
        + "number of seconds (the server may lower the effective wait period); if the job "
        + "completes in time, the regular synchronous response is returned instead of 202.";
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData)
                && HeaderPreferTransaction.isAsyncEnabled(apiData)
                && method == HttpMethods.POST
                && "/transactions".equals(definitionPath));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TransactionsConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return TransactionsBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return TransactionsBuildingBlock.SPEC;
  }
}
