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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

@Singleton
@AutoBind
public class HeaderPreferTransaction extends HeaderPrefer {

  public enum PreferReturn {
    NONE("none"),
    MINIMAL("minimal"),
    REPRESENTATION("representation");

    private final String header;

    PreferReturn(String header) {
      this.header = header;
    }

    public String headerValue() {
      return header;
    }

    static Optional<PreferReturn> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(Locale.ROOT);
      for (PreferReturn r : values()) {
        if (r.header.equals(v)) return Optional.of(r);
      }
      return Optional.empty();
    }
  }

  public enum PreferHandling {
    STRICT("strict"),
    LENIENT("lenient");

    private final String header;

    PreferHandling(String header) {
      this.header = header;
    }

    public String headerValue() {
      return header;
    }

    static Optional<PreferHandling> fromHeader(String value) {
      if (value == null) return Optional.empty();
      String v = value.trim().toLowerCase(Locale.ROOT);
      for (PreferHandling h : values()) {
        if (h.header.equals(v)) return Optional.of(h);
      }
      return Optional.empty();
    }
  }

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

  public static PreferReturn parseReturn(List<String> preferHeaders, PreferReturn fallback) {
    return parseParameterised(preferHeaders, "return", PreferReturn::fromHeader, fallback);
  }

  public static PreferHandling parseHandling(List<String> preferHeaders, PreferHandling fallback) {
    return parseParameterised(preferHeaders, "handling", PreferHandling::fromHeader, fallback);
  }

  public static boolean containsToken(List<String> preferHeaders, String token) {
    if (preferHeaders == null) return false;
    for (String header : preferHeaders) {
      for (String t : header.split(",")) {
        if (token.equalsIgnoreCase(t.trim())) return true;
      }
    }
    return false;
  }

  private static <T> T parseParameterised(
      List<String> preferHeaders, String name, Function<String, Optional<T>> parser, T fallback) {
    if (preferHeaders == null) return fallback;
    for (String header : preferHeaders) {
      for (String token : header.split(",")) {
        String t = token.trim();
        if (t.regionMatches(true, 0, name, 0, name.length())) {
          int eq = t.indexOf('=');
          if (eq > 0 && t.substring(name.length(), eq).trim().isEmpty()) {
            Optional<T> r = parser.apply(t.substring(eq + 1).trim());
            if (r.isPresent()) return r.get();
          }
        }
      }
    }
    return fallback;
  }
}
