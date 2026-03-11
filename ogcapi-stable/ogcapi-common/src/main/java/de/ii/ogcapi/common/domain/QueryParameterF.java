/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpRequestOverrideQueryParameter;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.ws.rs.container.ContainerRequestContext;

public abstract class QueryParameterF extends OgcApiQueryParameterBase
    implements TypedQueryParameter<ApiMediaType>, HttpRequestOverrideQueryParameter {

  private static final List<String> USER_AGENT_BOTS =
      ImmutableList.of(
          "googlebot",
          "bingbot",
          "duckduckbot",
          "yandexbot",
          "baiduspider",
          "slurp",
          "exabot",
          "facebot",
          "ia_archiver");

  protected final ExtensionRegistry extensionRegistry;
  protected final SchemaValidator schemaValidator;

  protected QueryParameterF(ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    this.extensionRegistry = extensionRegistry;
    this.schemaValidator = schemaValidator;
  }

  @Override
  public final String getName() {
    return "f";
  }

  @Override
  public String getDescription() {
    return "Select the output format of the response. If no value is provided, "
        + "the standard HTTP rules apply, i.e., the accept header will be used to determine the format.";
  }

  @Override
  public ApiMediaType parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    return extensionRegistry.getExtensionsForType(getFormatClass()).stream()
        .filter(
            f ->
                optionalCollectionData
                    .map(collectionData -> f.isEnabledForApi(api.getData(), collectionData.getId()))
                    .orElse(f.isEnabledForApi(api.getData())))
        .map(FormatExtension::getMediaType)
        .filter(mt -> Objects.equals(mt.parameter(), value))
        .findFirst()
        .orElse(null);
  }

  @Override
  public void applyTo(ContainerRequestContext requestContext, QueryParameterSet parameters) {
    if (parameters.getTypedValues().containsKey(getName())) {
      ApiMediaType value = (ApiMediaType) parameters.getTypedValues().get(getName());
      requestContext.getHeaders().putSingle("Accept", value.type().toString());
    } else {
      // use crawler user agent headers to trigger an implicit f=html
      String userAgent = requestContext.getHeaders().getFirst("user-agent");
      if (Objects.nonNull(userAgent)) {
        String finalUserAgent = userAgent.toLowerCase(Locale.ROOT);
        if (USER_AGENT_BOTS.stream().anyMatch(finalUserAgent::contains)) {
          requestContext.getHeaders().putSingle("Accept", "text/html");
        }
      }
    }
  }

  protected abstract Class<? extends FormatExtension> getFormatClass();

  protected ConcurrentMap<Integer, ConcurrentMap<String, Schema>> schemaMap =
      new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey("*")) {
      List<String> fEnum = new ArrayList<>();
      extensionRegistry.getExtensionsForType(getFormatClass()).stream()
          .filter(f -> f.isEnabledForApi(apiData))
          .filter(f -> !f.isInternal())
          .filter(f -> !f.getMediaType().parameter().equals("*"))
          .map(f -> f.getMediaType().parameter())
          .distinct()
          .sorted()
          .forEach(fEnum::add);
      schemaMap.get(apiHashCode).put("*", new StringSchema()._enum(fEnum));
    }
    return schemaMap.get(apiHashCode).get("*");
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey(collectionId)) {
      List<String> fEnum = new ArrayList<>();
      extensionRegistry.getExtensionsForType(getFormatClass()).stream()
          .filter(f -> f.isEnabledForApi(apiData, collectionId))
          .filter(f -> !f.isInternal())
          .filter(f -> !f.getMediaType().parameter().equals("*"))
          .map(f -> f.getMediaType().parameter())
          .distinct()
          .sorted()
          .forEach(fEnum::add);
      schemaMap.get(apiHashCode).put(collectionId, new StringSchema()._enum(fEnum));
    }
    return schemaMap.get(apiHashCode).get(collectionId);
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.STABLE_LDPROXY);
  }
}
