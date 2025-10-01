/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import static de.ii.ogcapi.foundation.domain.ApiSecurity.GROUP_PUBLIC;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.ApiSecurity.ScopeGranularity;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Immutable
public interface ApiOperation {

  Logger LOGGER = LoggerFactory.getLogger(ApiOperation.class);
  String STATUS_200 = "200";
  String STATUS_201 = "201";
  String STATUS_204 = "204";

  enum OperationType {
    RESOURCE,
    PROCESS
  }

  Map<HttpMethods, String> SUCCESS_STATUS_RESOURCE =
      ImmutableMap.of(
          HttpMethods.GET, STATUS_200,
          HttpMethods.POST, STATUS_201,
          HttpMethods.PUT, STATUS_204,
          HttpMethods.PATCH, STATUS_204,
          HttpMethods.DELETE, STATUS_204);

  Map<HttpMethods, String> SUCCESS_STATUS_PROCESSING =
      ImmutableMap.of(
          HttpMethods.GET, STATUS_200,
          HttpMethods.POST, STATUS_200);

  String getSummary();

  Optional<String> getDescription();

  Optional<ExternalDocumentation> getExternalDocs();

  Set<String> getTags();

  String getOperationId();

  PermissionGroup getPermissionGroup();

  List<OgcApiQueryParameter> getQueryParameters();

  Optional<ApiRequestBody> getRequestBody();

  List<ApiHeader> getHeaders();

  Optional<ApiResponse> getSuccess();

  @Value.Default
  default boolean ignoreUnknownQueryParameters() {
    return false;
  }

  @Value.Default
  default boolean hideInOpenAPI() {
    return false;
  }

  Optional<SpecificationMaturity> getSpecificationMaturity();

  Optional<ExternalDocumentation> getSpecificationRef();

  @Value.Derived
  @Value.Auxiliary
  default String getOperationIdWithoutPrefix() {
    return getOperationId().contains(".")
        ? getOperationId().substring(getOperationId().lastIndexOf('.') + 1)
        : getOperationId();
  }

  // Construct a standard fetch operation (GET, or URL-encoded POST)
  static Optional<ApiOperation> getResource(
      OgcApiDataV2 apiData,
      String path,
      boolean postUrlEncoded,
      List<OgcApiQueryParameter> queryParameters,
      List<ApiHeader> headers,
      Map<MediaType, ApiMediaTypeContent> responseContent,
      String operationSummary,
      Optional<String> operationDescription,
      Optional<ExternalDocumentation> externalDocs,
      String operationId,
      PermissionGroup permissionGroup,
      List<String> tags,
      Optional<SpecificationMaturity> specMaturity,
      Optional<ExternalDocumentation> spec) {
    if (responseContent.isEmpty()) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(
            "No media type supported for resource at path '{}'. The {} method will not be available.",
            path,
            postUrlEncoded ? "URL-encoded POST" : "GET");
      }
      return Optional.empty();
    }

    ApiRequestBody body = null;
    if (postUrlEncoded) {
      // convert the query parameters to a request body
      Optional<String> collectionId =
          path.startsWith("/collections/") ? Optional.of(path.split("/", 4)[2]) : Optional.empty();
      Schema<Object> formSchema = new ObjectSchema();
      queryParameters.forEach(
          param -> {
            Schema<?> paramSchema =
                param.getSchema(apiData, collectionId).description(param.getDescription());
            formSchema.addProperties(param.getName(), paramSchema);
            if (param.getRequired(apiData, collectionId)) {
              formSchema.addRequiredItem(param.getName());
            }
          });
      body =
          new ImmutableApiRequestBody.Builder()
              .description("The query parameters of the GET request encoded in the request body.")
              .content(
                  ImmutableMap.of(
                      MediaType.APPLICATION_FORM_URLENCODED_TYPE,
                      new ImmutableApiMediaTypeContent.Builder()
                          .ogcApiMediaType(
                              new ImmutableApiMediaType.Builder()
                                  .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                                  .label("Form")
                                  .parameter("form")
                                  .build())
                          .schema(formSchema)
                          .schemaRef(
                              "#/components/schemas/form"
                                  + path.replace("/", "_").replace("{", "").replace("}", ""))
                          .build()))
              .build();
    }

    return Optional.of(
        new ImmutableApiOperation.Builder()
            .summary(operationSummary)
            .description(operationDescription)
            .externalDocs(externalDocs)
            .operationId(operationId)
            .permissionGroup(permissionGroup)
            .tags(tags)
            .specificationMaturity(specMaturity)
            .specificationRef(spec)
            .queryParameters(postUrlEncoded ? ImmutableList.of() : queryParameters)
            .headers(
                headers.stream()
                    .filter(ApiHeader::isRequestHeader)
                    .collect(Collectors.toUnmodifiableList()))
            .success(
                new ImmutableApiResponse.Builder()
                    .statusCode(SUCCESS_STATUS_RESOURCE.get(HttpMethods.GET))
                    .description("The operation was executed successfully.")
                    .headers(
                        headers.stream()
                            .filter(ApiHeader::isResponseHeader)
                            .collect(Collectors.toUnmodifiableList()))
                    .content(responseContent)
                    .build())
            .requestBody(Optional.ofNullable(body))
            .build());
  }

  // Construct a Create (POST), Replace (PUT), Delete (DELETE) or Update (PATCH) operation
  static Optional<ApiOperation> of(
      String path,
      HttpMethods method,
      Map<MediaType, ApiMediaTypeContent> requestContent,
      List<OgcApiQueryParameter> queryParameters,
      List<ApiHeader> headers,
      String operationSummary,
      Optional<String> operationDescription,
      Optional<ExternalDocumentation> externalDocs,
      String operationId,
      PermissionGroup permissionGroup,
      List<String> tags,
      Optional<SpecificationMaturity> specMaturity,
      Optional<ExternalDocumentation> spec,
      boolean putAllowsCreate) {
    if ((method == HttpMethods.POST || method == HttpMethods.PUT || method == HttpMethods.PATCH)
        && requestContent.isEmpty()) {
      if (LOGGER.isErrorEnabled()) {
        LOGGER.error(
            "No media type supported for resource at path '{}'. The {} method will not be available.",
            path,
            method.name());
      }
      return Optional.empty();
    }

    ImmutableApiOperation.Builder operationBuilder =
        new ImmutableApiOperation.Builder()
            .summary(operationSummary)
            .description(operationDescription)
            .externalDocs(externalDocs)
            .operationId(operationId)
            .permissionGroup(permissionGroup)
            .tags(tags)
            .specificationMaturity(specMaturity)
            .specificationRef(spec)
            .queryParameters(queryParameters)
            .headers(
                headers.stream()
                    .filter(ApiHeader::isRequestHeader)
                    .collect(Collectors.toUnmodifiableList()))
            .success(
                new ImmutableApiResponse.Builder()
                    .statusCode(SUCCESS_STATUS_RESOURCE.get(method))
                    .description("The operation was executed successfully.")
                    .headers(
                        headers.stream()
                            .filter(ApiHeader::isResponseHeader)
                            .collect(Collectors.toUnmodifiableList()))
                    .build());
    if (!requestContent.isEmpty()) {
      operationBuilder.requestBody(
          new ImmutableApiRequestBody.Builder()
              .content(requestContent)
              .description(
                  method == HttpMethods.POST
                      ? "The new resource to be created."
                      : method == HttpMethods.PUT && putAllowsCreate
                          ? "The resource to be created or updated."
                          : "The resource to be updated.")
              .build());
    }
    return Optional.of(operationBuilder.build());
  }

  // Construct an asynchronous Process (POST) operation
  static Optional<ApiOperation> of(
      Map<MediaType, ApiMediaTypeContent> requestContent,
      Map<MediaType, ApiMediaTypeContent> responseContent,
      List<OgcApiQueryParameter> queryParameters,
      List<ApiHeader> headers,
      String operationSummary,
      Optional<String> operationDescription,
      Optional<ExternalDocumentation> externalDocs,
      String operationId,
      PermissionGroup permissionGroup,
      List<String> tags,
      Optional<SpecificationMaturity> specMaturity,
      Optional<ExternalDocumentation> spec) {
    ImmutableApiResponse.Builder responseBuilder =
        new ImmutableApiResponse.Builder()
            .statusCode(SUCCESS_STATUS_PROCESSING.get(HttpMethods.POST))
            .description("The operation was executed successfully.")
            .headers(
                headers.stream()
                    .filter(ApiHeader::isResponseHeader)
                    .collect(Collectors.toUnmodifiableList()));
    if (Objects.nonNull(responseContent) && !responseContent.isEmpty()) {
      responseBuilder.content(responseContent).description("The process result.");
    }
    ImmutableApiOperation.Builder operationBuilder =
        new ImmutableApiOperation.Builder()
            .summary(operationSummary)
            .description(operationDescription)
            .externalDocs(externalDocs)
            .operationId(operationId)
            .permissionGroup(permissionGroup)
            .tags(tags)
            .specificationMaturity(specMaturity)
            .specificationRef(spec)
            .queryParameters(queryParameters)
            .headers(
                headers.stream()
                    .filter(ApiHeader::isRequestHeader)
                    .collect(Collectors.toUnmodifiableList()))
            .success(responseBuilder.build());
    if (Objects.nonNull(requestContent) && !requestContent.isEmpty()) {
      operationBuilder.requestBody(
          new ImmutableApiRequestBody.Builder()
              .content(requestContent)
              .description("The information to process.")
              .build());
    }
    return Optional.of(operationBuilder.build());
  }

  default void updateOpenApiDefinition(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      OpenAPI openAPI,
      OgcApiResource resource,
      String method,
      Set<Integer> errorCodes) {
    Operation op = new Operation();
    op.summary(getSummary());
    setOpenApiDescriptionAndExternalDoc(apiData, op);

    getTags().forEach(op::addTagsItem);
    op.operationId(getOperationId());

    resource
        .getPathParameters()
        .forEach(
            param -> {
              if (param.isExplodeInOpenApi(apiData)) {
                return;
              }
              param.updateOpenApiDefinition(apiData, collectionId, openAPI, op);
              errorCodes.add(404);
            });

    getQueryParameters()
        .forEach(
            param -> {
              param.updateOpenApiDefinition(apiData, collectionId, openAPI, op);
              errorCodes.add(400);
            });

    boolean isMutation = addPathItem(openAPI, op, resource, method, errorCodes);

    getRequestBody()
        .ifPresent(
            reqBody -> {
              reqBody.updateOpenApiDefinition(openAPI, op);
              errorCodes.add(400);
            });

    getHeaders()
        .forEach(
            header -> {
              header.updateOpenApiDefinition(apiData, openAPI, op);
              errorCodes.add(400);
            });

    getSuccess().ifPresent(success -> success.updateOpenApiDefinition(apiData, openAPI, op));

    addErrorResponses(op, errorCodes);

    if (apiData.getAccessControl().isPresent()) {
      Set<String> groups = getPermissionGroup().setOf();

      if (apiData.getAccessControl().get().isRestricted(groups)) {
        Set<String> scopes =
            getPermissionGroup().setOf(apiData.getAccessControl().get().getScopes());

        if (scopes.isEmpty()) {
          op.addSecurityItem(new SecurityRequirement().addList("Default"));
        } else {
          scopes.forEach(
              scope -> op.addSecurityItem(new SecurityRequirement().addList("Default", scope)));
          if (apiData.getAccessControl().get().getScopes().contains(ScopeGranularity.CUSTOM)) {
            apiData.getAccessControl().get().getGroupsWith(groups).stream()
                .filter(group1 -> !Objects.equals(group1, GROUP_PUBLIC))
                .distinct()
                .forEach(
                    group ->
                        op.addSecurityItem(new SecurityRequirement().addList("Default", group)));
          }
        }
      }
    }
  }

  private void setOpenApiDescriptionAndExternalDoc(OgcApiDataV2 apiData, Operation op) {
    if (apiData
        .getExtension(FoundationConfiguration.class)
        .map(FoundationConfiguration::includesSpecificationInformation)
        .orElse(false)) {
      getSpecificationMaturity()
          .ifPresentOrElse(
              maturity -> {
                op.description(
                    getDescription()
                        .map(
                            desc ->
                                String.format(
                                    "%s\n\n_%s_",
                                    desc, String.format(maturity.toString(), "operation")))
                        .orElse(String.format(maturity.toString(), "operation")));
                op.setExtensions(ImmutableMap.of("x-maturity", maturity.name()));
                if (Objects.equals(maturity, SpecificationMaturity.DEPRECATED)) {
                  op.setDeprecated(true);
                }
              },
              () -> op.description(getDescription().orElse(null)));
      getExternalDocs()
          .ifPresentOrElse(
              externalDocs -> {
                io.swagger.v3.oas.models.ExternalDocumentation docs =
                    new io.swagger.v3.oas.models.ExternalDocumentation().url(externalDocs.getUrl());
                externalDocs.getDescription().ifPresent(docs::description);
                op.externalDocs(docs);
              },
              () ->
                  getSpecificationRef()
                      .ifPresent(
                          spec -> {
                            io.swagger.v3.oas.models.ExternalDocumentation docs =
                                new io.swagger.v3.oas.models.ExternalDocumentation()
                                    .url(spec.getUrl());
                            spec.getDescription()
                                .ifPresentOrElse(
                                    desc ->
                                        docs.description(
                                            String.format(
                                                "The specification that describes this operation: %s",
                                                desc)),
                                    () ->
                                        docs.description(
                                            "The specification that describes this operation."));
                            op.externalDocs(docs);
                          }));

    } else {
      op.description(getDescription().orElse(null));
      getExternalDocs()
          .ifPresent(
              externalDocs -> {
                io.swagger.v3.oas.models.ExternalDocumentation docs =
                    new io.swagger.v3.oas.models.ExternalDocumentation().url(externalDocs.getUrl());
                externalDocs.getDescription().ifPresent(docs::description);
                op.externalDocs(docs);
              });
    }
  }

  private boolean addPathItem(
      OpenAPI openAPI,
      Operation op,
      OgcApiResource resource,
      String method,
      Set<Integer> errorCodes) {
    String path = resource.getPath();
    PathItem pathItem = openAPI.getPaths().get(path);
    if (Objects.isNull(pathItem)) {
      pathItem = new PathItem();
      openAPI.path(path, pathItem);
    }

    boolean isMutation = false;
    switch (method) {
      case "GET":
        pathItem.get(op);
        break;
      case "POST":
        pathItem.post(op);
        isMutation = true;
        if (getRequestBody().isPresent()) {
          Set<MediaType> mediaTypes = getRequestBody().get().getContent().keySet();
          if (mediaTypes.size() == 1
              && mediaTypes.iterator().next().equals(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
            // URL-encoded form
            isMutation = false;
            errorCodes.add(406);
          } else if (getSuccess().stream()
              .anyMatch(
                  response ->
                      ImmutableSet.of(STATUS_200, STATUS_201, STATUS_204)
                          .contains(response.getStatusCode()))) {
            // Processing request
            isMutation = false;
            errorCodes.add(406);
          }
        }
        break;
      case "PUT":
        pathItem.put(op);
        isMutation = true;
        break;
      case "DELETE":
        pathItem.delete(op);
        isMutation = true;
        break;
      case "PATCH":
        pathItem.patch(op);
        isMutation = true;
        break;
      default:
        // skip HEAD and OPTIONS, these are not included in the OpenAPI definition
    }
    return isMutation;
  }

  private void addErrorResponses(Operation op, Set<Integer> errorCodes) {
    ApiResponses responses = op.getResponses();
    if (Objects.isNull(responses)) {
      responses = new ApiResponses();
      op.responses(responses);
    }

    if (errorCodes.contains(400)) {
      responses.addApiResponse("400", newErrorResponse("Bad Request"));
    }
    if (errorCodes.contains(404)) {
      responses.addApiResponse("404", newErrorResponse("Not Found"));
    }
    if (errorCodes.contains(405)) {
      responses.addApiResponse("405", newErrorResponse("Method Not Allowed"));
    }
    if (errorCodes.contains(406)) {
      responses.addApiResponse("406", newErrorResponse("Not Acceptable"));
    }
    if (errorCodes.contains(415)) {
      responses.addApiResponse("415", newErrorResponse("Unsupported Media Type"));
    }
    if (errorCodes.contains(422)) {
      responses.addApiResponse("422", newErrorResponse("Unprocessable Entity"));
    }
    responses.addApiResponse("500", newErrorResponse("Server Error"));
  }

  private io.swagger.v3.oas.models.responses.ApiResponse newErrorResponse(String description) {
    return new io.swagger.v3.oas.models.responses.ApiResponse().description(description);
  }
}
