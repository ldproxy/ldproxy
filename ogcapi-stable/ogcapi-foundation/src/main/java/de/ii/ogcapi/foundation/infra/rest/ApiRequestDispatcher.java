/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import static de.ii.ogcapi.foundation.domain.ApiEndpointDefinition.SORT_PRIORITY_DUMMY;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.EndpointExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HttpRequestOverrideQueryParameter;
import de.ii.ogcapi.foundation.domain.ImmutableRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.OgcApiResource;
import de.ii.ogcapi.foundation.domain.ParameterExtension;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.ogcapi.foundation.domain.RequestInjectableContext;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.services.domain.ServiceEndpoint;
import io.dropwizard.auth.Auth;
import io.dropwizard.jetty.HttpConnectorFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import org.glassfish.jersey.message.internal.FormProvider;
import org.glassfish.jersey.server.internal.routing.UriRoutingContext;

@Singleton
@AutoBind
// @PermitAll
public class ApiRequestDispatcher implements ServiceEndpoint {

  private static final Set<String> NOCONTENT_METHODS =
      ImmutableSet.of("POST", "PUT", "DELETE", "PATCH");
  private static final ApiMediaType DEFAULT_MEDIA_TYPE = ApiMediaType.JSON_MEDIA_TYPE;

  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final RequestInjectableContext ogcApiInjectableContext;
  private final ContentNegotiationMediaType contentNegotiationMediaType;
  private final ContentNegotiationLanguage contentNegotiationLanguage;
  private final ApiRequestAuthorizer apiRequestAuthorizer;
  private final int maxResponseLinkHeaderSize;

  @Inject
  ApiRequestDispatcher(
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      RequestInjectableContext ogcApiInjectableContext,
      ContentNegotiationMediaType contentNegotiationMediaType,
      ContentNegotiationLanguage contentNegotiationLanguage,
      ApiRequestAuthorizer apiRequestAuthorizer) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.ogcApiInjectableContext = ogcApiInjectableContext;
    this.contentNegotiationMediaType = contentNegotiationMediaType;
    this.contentNegotiationLanguage = contentNegotiationLanguage;
    this.apiRequestAuthorizer = apiRequestAuthorizer;
    this.maxResponseLinkHeaderSize = getMaxResponseHeaderSize(appContext) / 4;
  }

  @Override
  public String getServiceType() {
    return OgcApiDataV2.SERVICE_TYPE;
  }

  @Path("")
  public EndpointExtension dispatchLandingPageWithoutSlash(
      @PathParam("entrypoint") String entrypoint,
      @Context OgcApi service,
      @Context ContainerRequestContext requestContext,
      @Context Request request,
      @Auth Optional<User> optionalUser) {
    return dispatch("", service, requestContext, request, optionalUser);
  }

  @Path("/{entrypoint: [^/]*}")
  public EndpointExtension dispatch(
      @PathParam("entrypoint") String entrypoint,
      @Context OgcApi api,
      @Context ContainerRequestContext requestContext,
      @Context Request request,
      @Auth Optional<User> optionalUser) {

    String subPath = ((UriRoutingContext) requestContext.getUriInfo()).getFinalMatchingGroup();
    String method = requestContext.getMethod();

    OgcApiDataV2 apiData = api.getData();
    EndpointExtension ogcApiEndpoint = findEndpoint(apiData, entrypoint, subPath, method);

    if (!api.isAvailable(ogcApiEndpoint, true)) {
      throw new ServiceUnavailableException(
          "The requested resource is currently not available. Please try again later.");
    }

    // determine the feature collection, if this is a collection resource or sub-resource
    Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData =
        getCollectionData(apiData, entrypoint, subPath);

    // read body for authorization and for form requests
    Optional<byte[]> body = Optional.empty();
    if (requestContext.hasEntity() && !ogcApiEndpoint.skipBodyParsing()) {
      try {
        body = Optional.of(requestContext.getEntityStream().readAllBytes());
      } catch (IOException e) {
        throw new IllegalStateException("Could not read request body.", e);
      }
    }

    // determine the allowed query parameters
    List<OgcApiQueryParameter> knownParameters =
        getKnownQueryParameters(
            apiData,
            entrypoint,
            subPath,
            method,
            requestContext.getMediaType(),
            ogcApiEndpoint,
            body);

    // determine the query parameters of the request
    MultivaluedMap<String, String> actualParameters =
        getActualQueryParameters(requestContext, body);

    // Validate request
    ApiOperation apiOperation =
        validateRequest(
            requestContext,
            apiData,
            ogcApiEndpoint,
            knownParameters,
            actualParameters,
            entrypoint,
            subPath,
            method);

    // Evaluate query parameters
    QueryParameterSet queryParameterSet =
        QueryParameterSet.of(knownParameters, actualParameters)
            .evaluate(api, optionalCollectionData);

    // Content negotiation
    ImmutableSet<ApiMediaType> supportedMediaTypes =
        ogcApiEndpoint.getMediaTypes(apiData, subPath, method);

    for (OgcApiQueryParameter parameter : queryParameterSet.getDefinitions()) {
      if (parameter instanceof HttpRequestOverrideQueryParameter) {
        ((HttpRequestOverrideQueryParameter) parameter).applyTo(requestContext, queryParameterSet);
      }
    }

    ApiMediaType selectedMediaType = selectMediaType(requestContext, supportedMediaTypes, method);
    Locale selectedLanguage =
        contentNegotiationLanguage.negotiateLanguage(requestContext).orElse(Locale.ENGLISH);

    ApiRequestContext apiRequestContext =
        new ImmutableRequestContext.Builder()
            .webContext(appContext)
            .requestContext(requestContext)
            .queryParameterSet(queryParameterSet)
            .mediaType(selectedMediaType)
            .alternateMediaTypes(getAlternateMediaTypes(selectedMediaType, supportedMediaTypes))
            .language(selectedLanguage)
            .api(api)
            .maxResponseLinkHeaderSize(maxResponseLinkHeaderSize)
            .user(optionalUser)
            .build();

    // might return a new ApiRequestContext with policy obligations applied
    apiRequestContext =
        apiRequestAuthorizer.checkAuthorization(
            apiRequestContext, apiOperation, optionalUser, body);

    // reset body for downstream endpoints
    body.ifPresent(bytes -> requestContext.setEntityStream(new ByteArrayInputStream(bytes)));

    ogcApiInjectableContext.inject(requestContext, apiRequestContext);

    return ogcApiEndpoint;
  }

  private static MultivaluedMap<String, String> getActualQueryParameters(
      ContainerRequestContext requestContext, Optional<byte[]> body) {

    if (requestContext.getMethod().equals("POST")
        && requestContext.getMediaType().equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        && body.isPresent()) {
      // for a form request, get the query parameters from the body
      try {
        return new FormProvider()
            .readFrom(
                Form.class,
                Form.class,
                new Annotation[] {},
                MediaType.APPLICATION_FORM_URLENCODED_TYPE,
                new MultivaluedHashMap<>(),
                new ByteArrayInputStream(body.get()))
            .asMap();
      } catch (IOException e) {
        throw new IllegalStateException("Could not parse request body into a form.", e);
      }
    }
    return requestContext.getUriInfo().getQueryParameters();
  }

  private List<OgcApiQueryParameter> getKnownQueryParameters(
      OgcApiDataV2 apiData,
      String entrypoint,
      String subPath,
      String method,
      MediaType mediaType,
      EndpointExtension ogcApiEndpoint,
      Optional<byte[]> body) {
    if (method.equals("POST")
        && mediaType.equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        && body.isPresent()) {
      // get allowed query parameters from the associated GET request
      return extensionRegistry.getExtensionsForType(EndpointExtension.class).stream()
          .filter(endpoint -> endpoint.isEnabledForApi(apiData))
          .map(endpoint -> endpoint.getDefinition(apiData))
          .map(
              endpointDef ->
                  endpointDef.getOperation(String.format("/%s%s", entrypoint, subPath), method))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .map(ApiOperation::getQueryParameters)
          .findFirst()
          .orElse(ImmutableList.of());
    }
    return ogcApiEndpoint.getParameters(apiData, subPath, method);
  }

  private static Optional<FeatureTypeConfigurationOgcApi> getCollectionData(
      OgcApiDataV2 apiData, String entrypoint, String subPath) {
    Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData = Optional.empty();
    if ("collections".equals(entrypoint) && subPath.length() > 1) {
      int idx = subPath.substring(1).indexOf('/');
      String collectionId = idx != -1 ? subPath.substring(1, idx + 1) : subPath.substring(1);
      optionalCollectionData = apiData.getCollectionData(collectionId);
    }
    return optionalCollectionData;
  }

  private void checkParameterNames(
      ContainerRequestContext requestContext,
      OgcApiDataV2 apiData,
      EndpointExtension ogcApiEndpoint,
      List<OgcApiQueryParameter> knownParameters,
      Set<String> parameters,
      @SuppressWarnings("unused") String entrypoint,
      String subPath,
      String method) {
    if ("OPTIONS".equals(method)) {
      return;
    }
    if (ogcApiEndpoint.shouldIgnoreParameters(apiData, subPath, method)) {
      return;
    }
    Set<String> unknownParameters =
        parameters.stream()
            .filter(
                parameter ->
                    knownParameters.stream()
                        .noneMatch(param -> param.getName().equalsIgnoreCase(parameter)))
            .collect(Collectors.toSet());
    if (!unknownParameters.isEmpty()) {
      throw new BadRequestException(
          "The following query parameters are rejected: "
              + String.join(", ", unknownParameters)
              + ". Valid parameters for this request are: "
              + knownParameters.stream()
                  .map(ParameterExtension::getName)
                  .collect(Collectors.joining(", ")));
    }
  }

  private ApiOperation validateRequest(
      ContainerRequestContext requestContext,
      OgcApiDataV2 apiData,
      EndpointExtension ogcApiEndpoint,
      List<OgcApiQueryParameter> knownParameters,
      MultivaluedMap<String, String> actualParameters,
      String entrypoint,
      String subPath,
      String method) {
    checkParameterNames(
        requestContext,
        apiData,
        ogcApiEndpoint,
        knownParameters,
        actualParameters.keySet(),
        entrypoint,
        subPath,
        method);

    ApiEndpointDefinition apiDef = ogcApiEndpoint.getDefinition(apiData);
    if (!apiDef.getResources().isEmpty()) {
      // check that the subPath is valid
      OgcApiResource resource = apiDef.getResource("/" + entrypoint + subPath).orElse(null);
      if (resource == null) {
        throw new NotFoundException("The requested path is not a resource in this API.");
      }

      // no need to check the path parameters here, only the parent path parameters (service,
      // endpoint) are available;
      // path parameters in the sub-path have to be checked later
      ApiOperation operation = apiDef.getOperation(resource, method).orElse(null);
      if (operation == null) {
        throw notAllowedOrNotFound(getMethods(apiData, entrypoint, subPath));
      }

      Optional<String> collectionId = resource.getCollectionId(apiData);

      // validate query parameters
      actualParameters.forEach(
          (name, values) ->
              operation.getQueryParameters().stream()
                  .filter(param -> param.getName().equalsIgnoreCase(name))
                  .forEach(
                      param -> {
                        Optional<String> result = param.validate(apiData, collectionId, values);
                        if (result.isPresent()) {
                          throw new BadRequestException(result.get());
                        }
                      }));
      return operation;
    }

    return null;
  }

  private RuntimeException notAllowedOrNotFound(Set<String> methods) {
    if (methods.isEmpty()) {
      return new NotFoundException("The requested path is not a resource in this API.");
    } else {
      String first = methods.stream().findFirst().get();
      String[] more =
          methods.stream().filter(method -> !method.equals(first)).toArray(String[]::new);
      return new NotAllowedException(first, more);
    }
  }

  private ApiMediaType selectMediaType(
      ContainerRequestContext requestContext,
      Set<ApiMediaType> supportedMediaTypes,
      String method) {
    if (supportedMediaTypes.isEmpty() && NOCONTENT_METHODS.contains(method)) {
      return DEFAULT_MEDIA_TYPE;
    }

    return contentNegotiationMediaType
        .negotiateMediaType(requestContext, supportedMediaTypes)
        .orElseThrow(
            () ->
                new NotAcceptableException(
                    MessageFormat.format(
                        "The Accept header ''{0}'' does not match any of the supported media types for this resource: {1}.",
                        requestContext.getHeaderString("Accept"),
                        supportedMediaTypes.stream()
                            .map(mediaType -> mediaType.type().toString())
                            .collect(Collectors.toList()))));
  }

  private Set<ApiMediaType> getAlternateMediaTypes(
      ApiMediaType selectedMediaType, Set<ApiMediaType> mediaTypes) {
    return mediaTypes.stream()
        .filter(mediaType -> !Objects.equals(mediaType, selectedMediaType))
        .collect(ImmutableSet.toImmutableSet());
  }

  private EndpointExtension findEndpoint(
      OgcApiDataV2 dataset,
      @PathParam("entrypoint") String entrypoint,
      String subPath,
      String method) {
    if ("OPTIONS".equals(method)) {
      // special treatment for OPTIONS
      // check that the resource exists and in that case use the general endpoint for all OPTIONS
      // requests
      boolean resourceExists =
          getEndpoints().stream()
              .filter(endpoint -> endpoint.isEnabledForApi(dataset))
              .anyMatch(
                  endpoint -> {
                    ApiEndpointDefinition apiDef = endpoint.getDefinition(dataset);
                    if (apiDef.getSortPriority() != SORT_PRIORITY_DUMMY) {
                      return apiDef.matches("/" + entrypoint + subPath, null);
                    }
                    return false;
                  });
      if (!resourceExists) {
        throw new NotFoundException("The requested path is not a resource in this API.");
      }

      return getEndpoints().stream()
          .filter(endpoint -> endpoint.getClass() == OptionsEndpoint.class)
          .findAny()
          .orElseThrow(() -> notAllowedOrNotFound(getMethods(dataset, entrypoint, subPath)));
    }

    return getEndpoints().stream()
        .filter(endpoint -> endpoint.isEnabledForApi(dataset))
        .filter(
            endpoint -> {
              ApiEndpointDefinition apiDef = endpoint.getDefinition(dataset);
              if (apiDef != null && apiDef.getSortPriority() != SORT_PRIORITY_DUMMY) {
                return apiDef.matches("/" + entrypoint + subPath, method);
              }
              return false;
            })
        .findFirst()
        .orElseThrow(() -> notAllowedOrNotFound(getMethods(dataset, entrypoint, subPath)));
  }

  private Set<String> getMethods(
      OgcApiDataV2 dataset, @PathParam("entrypoint") String entrypoint, String subPath) {
    return getEndpoints().stream()
        .map(
            endpoint -> {
              ApiEndpointDefinition apiDef = endpoint.getDefinition(dataset);
              if (!apiDef.getResources().isEmpty()) {
                Optional<OgcApiResource> resource = apiDef.getResource("/" + entrypoint + subPath);
                return resource
                    .map(ogcApiResource -> ogcApiResource.getOperations().keySet())
                    .orElse(null);
              }
              return null;
            })
        .filter(Objects::nonNull)
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  private List<EndpointExtension> getEndpoints() {
    return extensionRegistry.getExtensionsForType(EndpointExtension.class);
  }

  private static int getMaxResponseHeaderSize(AppContext appContext) {
    HttpConnectorFactory httpConnectorFactory =
        (HttpConnectorFactory)
            appContext.getConfiguration().getServerFactory().getApplicationConnectors().get(0);

    return (int) httpConnectorFactory.getMaxResponseHeaderSize().toBytes();
  }
}
