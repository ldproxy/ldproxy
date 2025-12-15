/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import static de.ii.ogcapi.foundation.domain.ApiEndpointDefinition.SORT_PRIORITY_DUMMY;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;

@AutoMultiBind
public interface EndpointExtension extends ApiExtension {

  ApiEndpointDefinition DEFAULT_DEFINITION =
      new ImmutableApiEndpointDefinition.Builder()
          .apiEntrypoint("")
          .sortPriority(SORT_PRIORITY_DUMMY)
          .build();

  default ApiEndpointDefinition getDefinition(OgcApiDataV2 apiData) {
    return DEFAULT_DEFINITION;
  }

  default ImmutableSet<ApiMediaType> getMediaTypes(OgcApiDataV2 apiData, String requestSubPath) {
    return getMediaTypes(apiData, requestSubPath, "GET");
  }

  default ImmutableSet<ApiMediaType> getMediaTypes(
      OgcApiDataV2 apiData, String requestSubPath, String method) {
    ApiEndpointDefinition apiDef = getDefinition(apiData);
    if (apiDef.getResources().isEmpty()) {
      return ImmutableSet.of();
    }

    OgcApiResource resource = apiDef.getResource(apiDef.getPath(requestSubPath)).orElse(null);
    if (resource != null) {
      ApiOperation operation = apiDef.getOperation(resource, method).orElse(null);
      if (operation != null && operation.getSuccess().isPresent()) {
        return operation.getSuccess().get().getContent().values().stream()
            .map(ApiMediaTypeContent::getOgcApiMediaType)
            .collect(ImmutableSet.toImmutableSet());
      }
      return ImmutableSet.of();
    }

    throw new ServerErrorException("Invalid sub path: " + requestSubPath, 500);
  }

  default List<OgcApiQueryParameter> getParameters(OgcApiDataV2 apiData, String requestSubPath) {
    return getParameters(apiData, requestSubPath, "GET");
  }

  default List<OgcApiQueryParameter> getParameters(
      OgcApiDataV2 apiData, String requestSubPath, String method) {
    ApiEndpointDefinition apiDef = getDefinition(apiData);
    if (apiDef.getResources().isEmpty()) {
      return ImmutableList.of();
    }

    OgcApiResource resource = apiDef.getResource(apiDef.getPath(requestSubPath)).orElse(null);
    if (resource != null) {
      ApiOperation operation = apiDef.getOperation(resource, method).orElse(null);
      if (operation != null && operation.getSuccess().isPresent()) {
        return operation.getQueryParameters();
      }
    }
    return ImmutableList.of();
  }

  default boolean shouldIgnoreParameters(
      OgcApiDataV2 apiData, String requestSubPath, String method) {
    ApiEndpointDefinition apiDef = getDefinition(apiData);
    if (apiDef.getResources().isEmpty()) {
      return false;
    }

    OgcApiResource resource = apiDef.getResource(apiDef.getPath(requestSubPath)).orElse(null);
    if (resource != null) {
      ApiOperation operation = apiDef.getOperation(resource, method).orElse(null);
      if (operation != null && operation.getSuccess().isPresent()) {
        return operation.ignoreUnknownQueryParameters();
      }
    }
    return false;
  }

  default ImmutableList<OgcApiPathParameter> getPathParameters(
      ExtensionRegistry extensionRegistry, OgcApiDataV2 apiData, String definitionPath) {
    return extensionRegistry.getExtensionsForType(OgcApiPathParameter.class).stream()
        .filter(param -> param.isApplicable(apiData, definitionPath))
        .sorted(Comparator.comparing(ParameterExtension::getName))
        .collect(ImmutableList.toImmutableList());
  }

  default List<OgcApiQueryParameter> getQueryParameters(
      ExtensionRegistry extensionRegistry, OgcApiDataV2 apiData, String definitionPath) {
    return getQueryParameters(extensionRegistry, apiData, definitionPath, HttpMethods.GET);
  }

  default List<OgcApiQueryParameter> getQueryParameters(
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData,
      String definitionPath,
      HttpMethods method) {
    return Stream.concat(
            extensionRegistry.getExtensionsForType(OgcApiQueryParameter.class).stream()
                .filter(param -> param.isApplicable(apiData, definitionPath, method))
                .sorted(Comparator.comparing(ParameterExtension::getName)),
            extensionRegistry.getExtensionsForType(RuntimeQueryParametersExtension.class).stream()
                .map(
                    extension ->
                        extension.getRuntimeParameters(
                            apiData, Optional.empty(), definitionPath, method))
                .flatMap(Collection::stream))
        .collect(ImmutableList.toImmutableList());
  }

  default ImmutableList<ApiHeader> getHeaders(
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData,
      String definitionPath,
      HttpMethods method) {
    return extensionRegistry.getExtensionsForType(ApiHeader.class).stream()
        .filter(param -> param.isApplicable(apiData, definitionPath, method))
        .sorted(Comparator.comparing(ApiHeader::getName))
        .collect(ImmutableList.toImmutableList());
  }

  default void checkPathParameter(
      ExtensionRegistry extensionRegistry,
      OgcApiDataV2 apiData,
      String definitionPath,
      String parameterName,
      String parameterValue) {
    getPathParameters(extensionRegistry, apiData, definitionPath).stream()
        .filter(param -> param.getName().equalsIgnoreCase(parameterName))
        .map(
            param ->
                param
                    .validate(apiData, Optional.empty(), ImmutableList.of(parameterValue))
                    .orElse(null))
        .filter(Objects::nonNull)
        .findFirst()
        .ifPresent(
            message -> {
              throw new NotFoundException(message);
            });
  }

  default boolean skipBodyParsing() {
    return false;
  }
}
