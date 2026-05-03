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
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.styles.domain.ImmutableQueryInputStyle;
import de.ii.ogcapi.styles.domain.QueriesHandlerStyles;
import de.ii.ogcapi.styles.domain.StyleMetadataFormatExtension;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Style Metadata
 * @path styles/{styleId}/metadata
 * @langEn Style metadata is essential information about a style in order to support users to
 *     discover and select styles for rendering their data and for visual style editors to create
 *     user interfaces for editing a style. This operation returns the metadata for the requested
 *     style as a single document. The style metadata is derived from the style and the stylesheet.
 * @langDe Style-Metadaten sind wichtige Informationen über einen Style, damit Benutzer Styles für
 *     die Darstellung ihrer Daten erkennen und auswählen können und visuelle Style-Editoren
 *     Benutzeroberflächen für die Bearbeitung eines Styles bereitstellen können. Diese Operation
 *     gibt die Metadaten für den angeforderten Style als ein einziges Dokument zurück. Die
 *     Metadaten werden aus dem Style und dem Stylesheet abgeleitet.
 * @ref:formats {@link de.ii.ogcapi.styles.domain.StyleMetadataFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointStyleMetadata extends Endpoint implements ApiExtensionHealth {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointStyleMetadata.class);

  protected static final List<String> TAGS = ImmutableList.of("Discover and fetch styles");

  private final QueriesHandlerStyles queryHandler;

  @Inject
  public EndpointStyleMetadata(
      ExtensionRegistry extensionRegistry, QueriesHandlerStyles queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return StylesConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(StyleMetadataFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("styles")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_STYLE_METADATA);
    String path = "/styles/{styleId}/metadata";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    if (pathParameters.stream().noneMatch(param -> param.getName().equals("styleId"))) {
      LOGGER.error(
          "Path parameter 'styleId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      String operationSummary = "fetch metadata about the style `{styleId}`";
      Optional<String> operationDescription =
          Optional.of(
              "Style metadata is essential information about a style in order to "
                  + "support users to discover and select styles for rendering their data and for visual style editors "
                  + "to create user interfaces for editing a style. This operations returns the metadata for the "
                  + "requested style as a single document. The style metadata is derived from the style and the "
                  + "stylesheet.");
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);
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
              getOperationId("getStyleMetadata"),
              GROUP_STYLES_READ,
              TAGS,
              StylesBuildingBlock.MATURITY,
              StylesBuildingBlock.SPEC)
          .ifPresent(operation -> resourceBuilder.putOperations("GET", operation));
      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  /**
   * Fetch metadata for a style
   *
   * @param styleId the local identifier of a specific style
   * @return the style in a json file
   */
  @Path("/{styleId}/metadata")
  @GET
  @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_HTML})
  public Response getStyleMetadata(
      @PathParam("styleId") String styleId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext) {

    OgcApiDataV2 apiData = api.getData();
    checkPathParameter(
        extensionRegistry, apiData, "/styles/{styleId}/metadata", "styleId", styleId);

    QueriesHandlerStyles.QueryInputStyle queryInput =
        new ImmutableQueryInputStyle.Builder()
            .from(getGenericQueryInput(api.getData()))
            .styleId(styleId)
            .build();

    return queryHandler.handle(
        QueriesHandlerStyles.Query.STYLE_METADATA, queryInput, requestContext);
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
