/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.app;

import static de.ii.ogcapi.foundation.domain.ApiSecurity.GROUP_DISCOVER_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.filter.domain.FunctionsFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.cql.domain.CqlBuiltInFunctions;
import de.ii.xtraplatform.cql.domain.CustomFunction;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * @title Functions
 * @path functions
 * @langEn List of non-standard CQL2 functions supported by this API.
 * @langDe Liste der von dieser API unterstützten nicht-standardisierten CQL2-Funktionen.
 * @ref:formats {@link de.ii.ogcapi.filter.domain.FunctionsFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointFunctions extends Endpoint implements ConformanceClass {

  private static final List<String> TAGS = ImmutableList.of("Capabilities");

  private final FeaturesCoreProviders providers;

  @Inject
  public EndpointFunctions(ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry);
    this.providers = providers;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return de.ii.ogcapi.filter.domain.FilterConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null) {
      formats = extensionRegistry.getExtensionsForType(FunctionsFormatExtension.class);
    }
    return formats;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/cql2/1.0/conf/functions");
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && apiData.getCollections().values().stream()
            .anyMatch(collection -> supportsCql2(apiData, collection));
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("functions")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_SORTABLES + 5);
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, "/functions");
    String operationSummary = "retrieve non-standard CQL2 functions";
    Optional<String> operationDescription =
        Optional.of(
            "Returns all non-standard CQL2 functions supported by this API, "
                + "including built-in functions like UPPER and LOWER.");
    String path = "/functions";
    ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
        new ImmutableOgcApiResourceAuxiliary.Builder().path(path);
    ApiOperation.getResource(
            apiData,
            path,
            false,
            queryParameters,
            ImmutableList.of(),
            getResponseContent(apiData),
            operationSummary,
            operationDescription,
            Optional.empty(),
            getOperationId("getFunctions"),
            GROUP_DISCOVER_READ,
            TAGS,
            FilterBuildingBlock.MATURITY,
            FilterBuildingBlock.SPEC)
        .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));

    definitionBuilder.putResources(path, resourceBuilder.build());
    return definitionBuilder.build();
  }

  @GET
  @Produces({"application/json", "text/html"})
  public Response getFunctions(@Context OgcApi api, @Context ApiRequestContext requestContext) {
    FunctionsFormatExtension outputFormat =
        api.getOutputFormat(
                FunctionsFormatExtension.class, requestContext.getMediaType(), Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    Object entity =
        outputFormat.getEntity(getFunctionDefinitions(api.getData()), api, requestContext);

    return Response.ok(entity).type(outputFormat.getMediaType().type()).build();
  }

  private boolean supportsCql2(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return providers
        .getFeatureProvider(apiData, collectionData, FeatureProvider::queries)
        .map(FeatureQueries::supportsCql2)
        .orElse(false);
  }

  private List<Map<String, Object>> getFunctionDefinitions(OgcApiDataV2 apiData) {
    Map<String, CustomFunction> functions = new LinkedHashMap<>();

    apiData.getCollections().values().stream()
        .filter(collection -> supportsCql2(apiData, collection))
        .map(collection -> getCustomFunctions(apiData, collection))
        .flatMap(Collection::stream)
        .forEach(function -> functions.put(function.getName().toUpperCase(Locale.ROOT), function));

    return functions.values().stream()
        .map(this::toFunctionDefinition)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private List<CustomFunction> getCustomFunctions(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return providers
        .getFeatureProvider(apiData, collectionData)
        .map(FeatureProvider::queries)
        .filter(de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability::isSupported)
        .map(de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability::get)
        .map(this::extractCustomFunctions)
        .orElse(ImmutableList.of());
  }

  private List<CustomFunction> extractCustomFunctions(FeatureQueries queries) {
    Set<String> seen = new HashSet<>();
    ImmutableList.Builder<CustomFunction> builder = ImmutableList.builder();
    for (CustomFunction function :
        CqlBuiltInFunctions.prependBuiltInFunctions(queries.getCql2Functions())) {
      String key = function.getName().toUpperCase(Locale.ROOT);
      if (!seen.contains(key)) {
        seen.add(key);
        builder.add(function);
      }
    }
    return builder.build();
  }

  private Optional<Map<String, Object>> toFunctionDefinition(CustomFunction function) {
    List<Map<String, Object>> arguments = new java.util.ArrayList<>();
    for (de.ii.xtraplatform.cql.domain.Cql2FunctionArgument argument : function.getArguments()) {
      List<String> argumentTypes =
          argument.getType().stream()
              .map(this::toFunctionType)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .toList();

      if (argumentTypes.isEmpty()) {
        return Optional.empty();
      }

      Map<String, Object> argumentDefinition = new LinkedHashMap<>();
      argumentDefinition.put(
          "title",
          argument.getName() != null && !argument.getName().isBlank() ? argument.getName() : "");
      argumentDefinition.put(
          "description",
          argument.getDescription() != null && !argument.getDescription().isBlank()
              ? argument.getDescription()
              : "");
      argumentDefinition.put("type", argumentTypes);
      arguments.add(argumentDefinition);
    }

    List<String> returnTypes =
        function.getReturns().stream()
            .map(this::toFunctionType)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    if (returnTypes.isEmpty()) {
      return Optional.empty();
    }

    Map<String, Object> functionDefinition = new LinkedHashMap<>();
    functionDefinition.put("name", function.getName().toLowerCase(Locale.ROOT));
    functionDefinition.put(
        "description",
        function.getDescription() != null && !function.getDescription().isBlank()
            ? function.getDescription()
            : "");
    functionDefinition.put("arguments", arguments);
    functionDefinition.put("returns", returnTypes);

    return Optional.of(functionDefinition);
  }

  private Optional<String> toFunctionType(String schemaType) {
    if (schemaType == null) {
      return Optional.empty();
    }

    return switch (schemaType.toUpperCase(Locale.ROOT)) {
      case "STRING", "VALUE_ARRAY" -> Optional.of("string");
      case "FLOAT" -> Optional.of("number");
      case "INTEGER" -> Optional.of("integer");
      case "DATE", "DATETIME", "INSTANT", "INTERVAL" -> Optional.of("datetime");
      case "GEOMETRY" -> Optional.of("geometry");
      case "BOOLEAN" -> Optional.of("boolean");
      default -> Optional.empty();
    };
  }
}
