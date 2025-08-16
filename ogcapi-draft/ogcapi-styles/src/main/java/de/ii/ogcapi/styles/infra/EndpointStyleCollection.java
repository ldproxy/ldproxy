/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.infra;

import static de.ii.ogcapi.styles.domain.QueriesHandlerStyles.GROUP_STYLES_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.styles.domain.ImmutableQueryInputStyle;
import de.ii.ogcapi.styles.domain.QueriesHandlerStyles;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StyleQueryParameter;
import de.ii.ogcapi.styles.domain.StyleRepository;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Collection Style
 * @path collections/{collectionId}/styles/{styleId}
 * @langEn Fetches the style with identifier `styleId`. The set of available styles can be retrieved
 *     at `/styles`. Not all styles are available in all style encodings.
 * @langDe Holt den Stil mit den Identifikator `styleId`. Die Liste der verfügbaren Stile kann unter
 *     `/styles` abgerufen werden. Nicht alle Stile sind in allen Formaten verfügbar.
 * @ref:formats {@link de.ii.ogcapi.styles.domain.StyleFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStyleCollection extends EndpointSubCollection implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointStyleCollection.class);

  private static final List<String> TAGS = ImmutableList.of("Discover and fetch styles");

  private final StyleRepository styleRepository;
  private final QueriesHandlerStyles queryHandler;

  @Inject
  public EndpointStyleCollection(
      ExtensionRegistry extensionRegistry,
      StyleRepository styleRepository,
      QueriesHandlerStyles queryHandler) {
    super(extensionRegistry);
    this.styleRepository = styleRepository;
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(StyleFormatExtension.class);
    return formats;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = super.onStartup(api, apiValidation);

    if (apiValidation == MODE.NONE) return result;

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    for (String collectionId : api.getData().getCollections().keySet()) {
      builder = styleRepository.validate(builder, api.getData(), Optional.of(collectionId));
    }

    return builder.build();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_STYLESHEET_COLLECTION);
    final String subSubPath = "/styles/{styleId}";
    final String path = "/collections/{collectionId}" + subSubPath;
    final List<OgcApiPathParameter> pathParameters =
        getPathParameters(extensionRegistry, apiData, path);
    final Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
    if (optCollectionIdParam.isEmpty()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          (explode) ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        List<OgcApiQueryParameter> queryParameters =
            getQueryParameters(extensionRegistry, apiData, path, collectionId);
        String operationSummary = "fetch a style for collection `" + collectionId + "`";
        Optional<String> operationDescription =
            Optional.of(
                "Fetches the style with identifier `styleId`. "
                    + "The set of available styles can be retrieved at `/styles`. Not all styles are available in "
                    + "all style encodings.");
        String resourcePath = path.replace("{collectionId}", collectionId);
        ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
            new ImmutableOgcApiResourceAuxiliary.Builder()
                .path(resourcePath)
                .pathParameters(pathParameters);
        Map<MediaType, ApiMediaTypeContent> responseContent = getResponseContent(apiData);
        ApiOperation.getResource(
                apiData,
                resourcePath,
                false,
                queryParameters,
                ImmutableList.of(),
                responseContent,
                operationSummary,
                operationDescription,
                Optional.empty(),
                getOperationId("getStyle", collectionId),
                GROUP_STYLES_READ,
                TAGS,
                StylesBuildingBlock.MATURITY,
                StylesBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }

    return definitionBuilder.build();
  }

  /**
   * Fetch a style by id
   *
   * @param styleId the local identifier of a specific style
   * @return the style in a json file
   */
  @Path("/{collectionId}/styles/{styleId}")
  @GET
  public Response getStyle(
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @PathParam("collectionId") String collectionId,
      @PathParam("styleId") String styleId) {

    OgcApiDataV2 apiData = api.getData();
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}",
        "collectionId",
        collectionId);
    checkPathParameter(
        extensionRegistry,
        apiData,
        "/collections/{collectionId}/styles/{styleId}",
        "styleId",
        styleId);
    checkCollectionExists(apiData, collectionId);

    ImmutableQueryInputStyle.Builder builder =
        new ImmutableQueryInputStyle.Builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .styleId(styleId);

    QueryParameterSet queryParameterSet = requestContext.getQueryParameterSet();
    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof StyleQueryParameter) {
        ((StyleQueryParameter) parameter).applyTo(builder, queryParameterSet);
      }
    }

    return queryHandler.handle(QueriesHandlerStyles.Query.STYLE, builder.build(), requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler, styleRepository);
  }
}
