/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
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

@Singleton
@AutoBind
public class HeaderPreferTransaction extends HeaderPrefer {

  private final Schema<?> schema =
      new StringSchema()
          ._enum(
              ImmutableList.of(
                  "respond-async",
                  "return=minimal",
                  "return=none",
                  "return=representation",
                  "handling=strict",
                  "handling=lenient"))
          ._default("return=representation");

  @Inject
  HeaderPreferTransaction(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "PreferTransaction";
  }

  @Override
  public String getDescription() {
    return "Controls how the server processes the transaction request. "
        + "'return=representation' (the default) returns the full Transaction Response document "
        + "with per-action results. 'return=minimal' returns the Transaction Response without the "
        + "per-action details. 'return=none' returns 204 No Content on success (a Transaction "
        + "Response is still returned when the transaction failed, so that exceptions can be "
        + "reported). Malformed transaction envelopes are rejected while parsing. "
        + "'handling=strict' validates each feature payload against its schema before any provider "
        + "write. 'handling=lenient' (the default) skips feature schema validation and only fails "
        + "on malformed requests or errors raised by the provider. 'respond-async' is not "
        + "supported and results in 501 Not Implemented.";
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
