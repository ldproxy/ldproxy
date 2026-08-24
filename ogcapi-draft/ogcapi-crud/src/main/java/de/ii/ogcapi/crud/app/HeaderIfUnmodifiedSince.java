/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.foundation.domain.ApiExtensionCache;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * The {@code If-Unmodified-Since} header of a request that changes a feature. Only applicable to
 * collections with {@code optimisticLockingLastModified}, where the header is required.
 */
@Singleton
@AutoBind
public class HeaderIfUnmodifiedSince extends ApiExtensionCache implements ApiHeader {

  private final Schema<?> schema = new StringSchema().format("date-time");
  private final SchemaValidator schemaValidator;

  @Inject
  HeaderIfUnmodifiedSince(SchemaValidator schemaValidator) {
    this.schemaValidator = schemaValidator;
  }

  @Override
  public String getId() {
    return "IfUnmodifiedSinceCrudFeature";
  }

  @Override
  public String getName() {
    return "If-Unmodified-Since";
  }

  @Override
  public String getDescription() {
    return "The last modification time of the feature that is known to the client, as reported in "
        + "the header `Last-Modified` of a previous response. The request is only executed, if the "
        + "feature has not been changed since; otherwise the response is `412`. This header is "
        + "required for this collection, a request without it is answered with `428`.";
  }

  @Override
  public boolean isRequestHeader() {
    return true;
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData)
                && (method == HttpMethods.PUT
                    || method == HttpMethods.PATCH
                    || method == HttpMethods.DELETE)
                && definitionPath.endsWith("/items/{featureId}"));
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(
        apiData, CrudConfiguration.class, CrudConfiguration::supportsLastModified);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData.isCollectionEnabled(collectionId)
        && isExtensionEnabled(
            apiData.getCollections().get(collectionId),
            CrudConfiguration.class,
            CrudConfiguration::supportsLastModified);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return CrudBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return CrudBuildingBlock.SPEC;
  }
}
