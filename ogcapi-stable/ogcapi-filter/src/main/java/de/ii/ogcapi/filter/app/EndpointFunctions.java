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
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.xtraplatform.cql.domain.CustomFunction;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @title Functions
 * @path functions
 * @langEn List of non-standard CQL2 functions supported by this API.
 * @langDe Liste der von dieser API unterstützten nicht-standardisierten CQL2-Funktionen.
 */
@Singleton
@AutoBind
public class EndpointFunctions extends Endpoint implements ConformanceClass {

  private static final List<String> TAGS = ImmutableList.of("Capabilities");

  private static final List<FunctionDef> BUILT_IN_NON_STANDARD_FUNCTIONS =
      ImmutableList.of(
          new FunctionDef(
              "UPPER", ImmutableList.of(ImmutableList.of("STRING")), ImmutableList.of("STRING")),
          new FunctionDef(
              "LOWER", ImmutableList.of(ImmutableList.of("STRING")), ImmutableList.of("STRING")),
          new FunctionDef("POSITION", ImmutableList.of(), ImmutableList.of("INTEGER")),
          new FunctionDef(
              "DIAMETER2D",
              ImmutableList.of(ImmutableList.of("GEOMETRY")),
              ImmutableList.of("FLOAT")),
          new FunctionDef(
              "DIAMETER3D",
              ImmutableList.of(ImmutableList.of("GEOMETRY")),
              ImmutableList.of("FLOAT")),
          new FunctionDef(
              "ALIKE",
              ImmutableList.of(ImmutableList.of("VALUE_ARRAY"), ImmutableList.of("STRING")),
              ImmutableList.of("BOOLEAN")));

  private static final ApiMediaTypeContent FUNCTIONS_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(
              new ObjectSchema()
                  .addRequiredItem("functions")
                  .addProperty(
                      "functions",
                      new ArraySchema()
                          .items(
                              new ObjectSchema()
                                  .addRequiredItem("name")
                                  .addRequiredItem("returns")
                                  .addProperty(
                                      "name", new io.swagger.v3.oas.models.media.StringSchema())
                                  .addProperty(
                                      "arguments",
                                      new ArraySchema()
                                          .items(
                                              new ObjectSchema()
                                                  .addRequiredItem("type")
                                                  .addProperty("type", new ArraySchema())))
                                  .addProperty("returns", new ArraySchema()))))
          .schemaRef(FormatExtension.OBJECT_SCHEMA_REF)
          .ogcApiMediaType(ApiMediaType.JSON_MEDIA_TYPE)
          .build();

  private static final FormatExtension JSON_FORMAT =
      new FormatExtension() {
        @Override
        public ApiMediaType getMediaType() {
          return ApiMediaType.JSON_MEDIA_TYPE;
        }

        @Override
        public ApiMediaTypeContent getContent() {
          return FUNCTIONS_CONTENT;
        }
      };

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
    return ImmutableList.of(JSON_FORMAT);
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
            ImmutableMap.of(MediaType.APPLICATION_JSON_TYPE, FUNCTIONS_CONTENT),
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
  public Response getFunctions(@Context OgcApi api, @Context ApiRequestContext requestContext) {
    return Response.ok(ImmutableMap.of("functions", getFunctionDefinitions(api.getData())))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  private boolean supportsCql2(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return providers
        .getFeatureProvider(apiData, collectionData, FeatureProvider::queries)
        .map(FeatureQueries::supportsCql2)
        .orElse(false);
  }

  private List<Map<String, Object>> getFunctionDefinitions(OgcApiDataV2 apiData) {
    Map<String, FunctionDef> functions =
        BUILT_IN_NON_STANDARD_FUNCTIONS.stream()
            .collect(
                LinkedHashMap::new,
                (map, function) -> map.put(function.name().toUpperCase(Locale.ROOT), function),
                Map::putAll);

    apiData.getCollections().values().stream()
        .filter(collection -> supportsCql2(apiData, collection))
        .map(collection -> getCustomFunctions(apiData, collection))
        .flatMap(Collection::stream)
        .forEach(function -> functions.put(function.name().toUpperCase(Locale.ROOT), function));

    return functions.values().stream()
        .map(this::toFunctionDefinition)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  private List<FunctionDef> getCustomFunctions(
      OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData) {
    return providers
        .getFeatureProvider(apiData, collectionData)
        .map(FeatureProvider::queries)
        .filter(de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability::isSupported)
        .map(de.ii.xtraplatform.base.domain.resiliency.OptionalVolatileCapability::get)
        .map(this::extractCustomFunctions)
        .orElse(ImmutableList.of());
  }

  private List<FunctionDef> extractCustomFunctions(FeatureQueries queries) {
    Set<String> seen = new HashSet<>();
    ImmutableList.Builder<FunctionDef> builder = ImmutableList.builder();
    for (CustomFunction function : queries.getCql2Functions()) {
      FunctionDef def = mapCustomFunction(function);
      String key = def.name().toUpperCase(Locale.ROOT);
      if (!seen.contains(key)) {
        seen.add(key);
        builder.add(def);
      }
    }
    return builder.build();
  }

  private FunctionDef mapCustomFunction(CustomFunction function) {
    List<List<String>> argTypes =
        function.getArguments().stream()
            .map(arg -> arg.getType().stream().map(String::toLowerCase).toList())
            .toList();
    List<String> returnTypes = function.getReturns().stream().map(String::toLowerCase).toList();
    return new FunctionDef(function.getName(), argTypes, returnTypes);
  }

  private Optional<Map<String, Object>> toFunctionDefinition(FunctionDef function) {
    List<Map<String, List<String>>> arguments =
        function.argumentTypes().stream()
            .map(
                typeList ->
                    Map.of(
                        "type",
                        typeList.stream()
                            .map(this::toFunctionType)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList()))
            .toList();

    List<String> returnTypes =
        function.returnTypes().stream()
            .map(this::toFunctionType)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    if (returnTypes.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        ImmutableMap.of(
            "name",
            function.name().toLowerCase(Locale.ROOT),
            "arguments",
            arguments,
            "returns",
            returnTypes));
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

  private record FunctionDef(
      String name, List<List<String>> argumentTypes, List<String> returnTypes) {}
}
