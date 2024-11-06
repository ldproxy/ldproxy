/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.infra;

import static de.ii.ogcapi.common.domain.QueriesHandlerCommon.GROUP_COLLECTIONS_READ;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.collections.app.CollectionsBuildingBlock;
import de.ii.ogcapi.collections.app.ImmutableQueryInputFeatureCollection;
import de.ii.ogcapi.collections.app.QueriesHandlerCollectionsImpl;
import de.ii.ogcapi.collections.domain.CollectionFormatExtension;
import de.ii.ogcapi.collections.domain.CollectionsConfiguration;
import de.ii.ogcapi.collections.domain.EndpointSubCollection;
import de.ii.ogcapi.collections.domain.QueriesHandlerCollections;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.CollectionExtent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.FoundationValidator;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiResourceAuxiliary;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.TemporalExtent;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import io.dropwizard.auth.Auth;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Feature Collection
 * @path collections/{collectionId}
 * @langEn Information about the feature collection with id `collectionId`. The response contains a
 *     link to the items in the collection (path `/collections/{collectionId}/items`,link relation
 *     `items`) as well as key information about the collection.
 * @langDe Informationen über die Feature-Collection mit der ID `collectionIdì. Die Antwort enthält
 *     einen Link zu den Elementen der Collection (Pfad `/collections/{collectionId}/items`,link
 *     relation `items`) sowie Schlüsselinformationen über die Collection.
 * @ref:formats {@link de.ii.ogcapi.collections.domain.CollectionsFormatExtension}
 */
@Singleton
@AutoBind
public class EndpointCollection extends EndpointSubCollection {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointCollection.class);
  private static final List<String> TAGS = ImmutableList.of("Discover data collections");

  private final QueriesHandlerCollections queryHandler;

  @Inject
  public EndpointCollection(
      ExtensionRegistry extensionRegistry, QueriesHandlerCollections queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CollectionsConfiguration.class;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    ValidationResult result = super.onStartup(api, apiValidation);

    if (apiValidation == MODE.NONE) return result;

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().from(result).mode(apiValidation);

    for (FeatureTypeConfigurationOgcApi collectionData : api.getData().getCollections().values()) {
      builder =
          FoundationValidator.validateLinks(
              builder,
              collectionData.getAdditionalLinks(),
              "/collections/" + collectionData.getId());

      Optional<String> persistentUriTemplate = collectionData.getPersistentUriTemplate();
      if (persistentUriTemplate.isPresent()) {
        Pattern valuePattern = Pattern.compile("\\{\\{[\\w\\.]+( ?\\| ?[\\w]+(:'[^']*')*)*\\}\\}");
        Matcher matcher = valuePattern.matcher(persistentUriTemplate.get());
        if (!matcher.find()) {
          builder.addStrictErrors(
              MessageFormat.format(
                  "Persistent URI template ''{0}'' in collection ''{1}'' does not have a valid value pattern.",
                  persistentUriTemplate.get(), collectionData.getId()));
        }
      }

      Optional<CollectionExtent> extent = api.getData().getExtent(collectionData.getId());
      if (extent.isPresent()) {
        Optional<BoundingBox> spatial = extent.get().getSpatial();
        if (spatial.isPresent() && Objects.nonNull(spatial.get())) {
          BoundingBox bbox = spatial.get();
          if (!ImmutableSet.of(4326, 4979).contains(bbox.getEpsgCrs().getCode())
              || bbox.getEpsgCrs().getForceAxisOrder() != EpsgCrs.Force.LON_LAT) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The spatial extent in collection ''{0}'' must be in CRS84 or CRS84h. Found: ''{1}, {2}''.",
                    collectionData.getId(),
                    bbox.getEpsgCrs().toSimpleString(),
                    bbox.getEpsgCrs().getForceAxisOrder()));
          }
          if (bbox.getXmin() < -180.0 || bbox.getXmin() > 180.0) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The spatial extent in collection ''{0}'' has a longitude value that is not between -180 and 180. Found: ''{1}''.",
                    collectionData.getId(), bbox.getXmin()));
          }
          if (bbox.getXmax() < -180.0 || bbox.getXmax() > 180.0) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The spatial extent in collection ''{0}'' has a longitude value that is not between -180 and 180. Found: ''{1}''.",
                    collectionData.getId(), bbox.getXmax()));
          }
          if (bbox.getYmin() < -90.0 || bbox.getYmin() > 90.0) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The spatial extent in collection ''{0}'' has a latitude value that is not between -90 and 90. Found: ''{1}''.",
                    collectionData.getId(), bbox.getYmin()));
          }
          if (bbox.getYmax() < -90.0 || bbox.getYmax() > 90.0) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The spatial extent in collection ''{0}'' has a latitude value that is not between -90 and 90. Found: ''{1}''.",
                    collectionData.getId(), bbox.getYmax()));
          }
          if (bbox.getYmax() < bbox.getYmin()) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The spatial extent in collection ''{0}'' has a maxmimum latitude value ''{1}'' that is lower than the minimum value ''{2}''.",
                    collectionData.getId(), bbox.getYmax(), bbox.getYmin()));
          }
        }
        Optional<TemporalExtent> temporal = extent.get().getTemporal();
        if (temporal.isPresent() && Objects.nonNull(temporal.get())) {
          Long start =
              Objects.nonNull(temporal.get().getStart()) ? temporal.get().getStart() : null;
          Long end = Objects.nonNull(temporal.get().getEnd()) ? temporal.get().getEnd() : null;
          if (start != null && end != null && end < start) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The temporal extent in collection ''{0}'' has an end ''{1}'' before the start ''{2}''.",
                    collectionData.getId(),
                    Instant.ofEpochMilli(end).truncatedTo(ChronoUnit.SECONDS).toString(),
                    Instant.ofEpochMilli(start).truncatedTo(ChronoUnit.SECONDS).toString()));
          }
        }
      }
    }

    return builder.build();
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (formats == null)
      formats = extensionRegistry.getExtensionsForType(CollectionFormatExtension.class);
    return formats;
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {
    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("collections")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_COLLECTION);
    String path = "/collections/{collectionId}";
    List<OgcApiQueryParameter> queryParameters =
        getQueryParameters(extensionRegistry, apiData, path);
    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
    Optional<OgcApiPathParameter> optCollectionIdParam =
        pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
    if (!optCollectionIdParam.isPresent()) {
      LOGGER.error(
          "Path parameter 'collectionId' missing for resource at path '"
              + path
              + "'. The GET method will not be available.");
    } else {
      final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
      final boolean explode = collectionIdParam.isExplodeInOpenApi(apiData);
      final List<String> collectionIds =
          (explode) ? collectionIdParam.getValues(apiData) : ImmutableList.of("{collectionId}");
      for (String collectionId : collectionIds) {
        FeatureTypeConfigurationOgcApi featureType = apiData.getCollections().get(collectionId);
        String operationSummary =
            "feature collection '"
                + (Objects.nonNull(featureType) ? featureType.getLabel() : collectionId)
                + "'";
        Optional<String> operationDescription =
            Optional.of(
                "Information about the feature collection with "
                    + "id '"
                    + collectionId
                    + "'. The response contains a link to the items in the collection "
                    + "(path `/collections/{collectionId}/items`,link relation `items`) as well as key "
                    + "information about the collection. This information includes:\n\n"
                    + "* A local identifier for the collection that is unique for the dataset;\n"
                    + "* A title and description for the collection;\n"
                    + "* An indication of the spatial and temporal extent of the data in the collection;\n"
                    + "* A list of coordinate reference systems (CRS) in which geometries may be returned by the server. "
                    + "The first CRS is the default coordinate reference system (the default is always WGS 84 with "
                    + "axis order longitude/latitude);\n"
                    + "* The CRS in which the spatial geometries are stored in the data source (if data is requested in "
                    + "this CRS, the geometries are returned without any coordinate conversion);\n"
                    + "* An indicator about the type of the items in the collection (the default value is 'feature').");
        String resourcePath = "/collections/" + collectionId;
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
                getOperationId("getCollection", collectionId),
                GROUP_COLLECTIONS_READ,
                TAGS,
                CollectionsBuildingBlock.MATURITY,
                CollectionsBuildingBlock.SPEC)
            .ifPresent(
                operation -> resourceBuilder.putOperations(HttpMethods.GET.name(), operation));
        definitionBuilder.putResources(resourcePath, resourceBuilder.build());
      }
    }

    return definitionBuilder.build();
  }

  @GET
  @Path("/{collectionId}")
  public Response getCollection(
      @Auth Optional<User> optionalUser,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @PathParam("collectionId") String collectionId) {

    if (!api.getData().isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }

    List<Link> additionalLinks =
        api.getData().getCollections().get(collectionId).getAdditionalLinks();

    QueriesHandlerCollectionsImpl.QueryInputFeatureCollection queryInput =
        ImmutableQueryInputFeatureCollection.builder()
            .from(getGenericQueryInput(api.getData()))
            .collectionId(collectionId)
            .additionalLinks(additionalLinks)
            .build();

    return queryHandler.handle(
        QueriesHandlerCollectionsImpl.Query.FEATURE_COLLECTION, queryInput, requestContext);
  }
}
