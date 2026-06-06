/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiExtensionCache;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * The {@code OGC-Mutation-Datetime} request header on {@code POST /transactions}: a fallback
 * mutation timestamp for client-supplied {@code mutationTime} when an action has no body-side
 * primary-interval-start value (notably {@code Delete} in any encoding).
 *
 * <p>Advertised in the OpenAPI definition only when the {@code VERSIONED_FEATURES} building block
 * is enabled for the API.
 */
@Singleton
@AutoBind
public class HeaderOgcMutationDatetime extends ApiExtensionCache implements ApiHeader {

  private static final Schema<String> SCHEMA = new StringSchema().format("date-time");

  private final SchemaValidator schemaValidator;

  @Inject
  HeaderOgcMutationDatetime(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getName() {
    return "OGC-Mutation-Datetime";
  }

  @Override
  public String getDescription() {
    return "Client-supplied mutation timestamp (RFC 3339 date-time, UTC). Used as a fallback for"
        + " actions that have no body-side primary-interval-start value (notably Delete). When"
        + " mutationTime is configured as 'client', the precedence is: body-supplied value >"
        + " header > 400 Bad Request.";
  }

  @Override
  public boolean isRequestHeader() {
    return true;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return SCHEMA;
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
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return VersionedFeaturesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return VersionedFeaturesBuildingBlock.SPEC;
  }

  /**
   * Parse the {@code OGC-Mutation-Datetime} request header into an {@link Instant}; returns empty
   * when the header is absent or blank. Rejects values that are not RFC 3339 date-times with {@code
   * BadRequestException}.
   */
  public static Optional<Instant> parse(String header) {
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Instant.parse(header.trim()));
    } catch (DateTimeParseException e) {
      throw new BadRequestException(
          "Invalid OGC-Mutation-Datetime header (expected an RFC 3339 date-time in UTC): "
              + header);
    }
  }
}
