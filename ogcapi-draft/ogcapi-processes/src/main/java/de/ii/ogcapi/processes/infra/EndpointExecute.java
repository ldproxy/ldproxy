/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.infra;

import static de.ii.ogcapi.processes.domain.ExecutionQueriesHandler.GROUP_PROCESSES_EXECUTE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.Endpoint;
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
import de.ii.ogcapi.processes.app.ProcessesCoreBuildingBlock;
import de.ii.ogcapi.processes.domain.ExecutionQueriesHandler;
import de.ii.ogcapi.processes.domain.ImmutableQueryInputExecution;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.format.ExecuteFormatExtension;
import de.ii.ogcapi.processes.domain.format.ResultsFormatExtension;
import de.ii.ogcapi.processes.domain.format.StatusInfoFormatExtension;
import de.ii.ogcapi.processes.domain.format.ValuesFormatExtension;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Execute
 * @path processes/{processId}/execution
 * @langEn Triggers the execution of a process. The inputs, output selection and possible
 *     subscribers are sent in the request. The respond depends on the number of outputs requested,
 *     the negotiated content type for the response, the mode of execution, and whether an output is
 *     single- or multi-valued. For more information refer to the [draft
 *     document](https://docs.ogc.org/DRAFTS/18-062r3.html#_cacf1be8-0e26-1ccf-7dad-11c93a1e9427).
 * @langDe Löst die Ausführung eines Prozesses aus. Die Eingaben, die Ausgabeauswahl und mögliche
 *     Subscriber werden im Request-body übermittelt. Die Antwort hängt von der Anzahl der
 *     angeforderten Ausgaben, dem ausgehandelten Inhaltstyp, dem Ausführungsmodus und davon ab, ob
 *     eine Ausgabe ein- oder mehrwertig ist. Weitere Informationen finden Sie im
 *     [Entwurfsdokument](https://docs.ogc.org/DRAFTS/18-062r3.html#_cacf1be8-0e26-1ccf-7dad-11c93a1e9427).
 */
@Singleton
@AutoBind
public class EndpointExecute extends Endpoint implements ApiExtensionHealth, ConformanceClass {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndpointExecute.class);
  private static final List<String> TAGS = ImmutableList.of("Processes");

  private final ExecutionQueriesHandler queryHandler;
  private List<? extends FormatExtension> requestFormats;
  private List<? extends FormatExtension> resourceFormats;

  @Inject
  public EndpointExecute(
      ExtensionRegistry extensionRegistry, ExecutionQueriesHandler queryHandler) {
    super(extensionRegistry);
    this.queryHandler = queryHandler;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    if (isEnabledForApi(apiData)) {
      return ImmutableList.of(
          "https://www.opengis.net/spec/ogcapi-processes-1/2.0/conf/callback",
          "http://www.opengis.net/spec/ogcapi-processes-3/0.0/req/nested-processes");
    }

    return ImmutableList.of();
  }

  @Override
  protected ApiEndpointDefinition computeDefinition(OgcApiDataV2 apiData) {

    ImmutableApiEndpointDefinition.Builder definitionBuilder =
        new ImmutableApiEndpointDefinition.Builder()
            .apiEntrypoint("processes")
            .sortPriority(ApiEndpointDefinition.SORT_PRIORITY_EXECUTE);

    String path = "/processes/{processId}/execution";
    HttpMethods method = HttpMethods.POST;

    List<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);

    if (pathParameters.stream().noneMatch(param -> "processId".equals(param.getName()))) {
      LOGGER.error(
          "Path parameter 'processId' missing for resource at path '{}'. The POST method will not be available.",
          path);
    } else {
      List<OgcApiQueryParameter> queryParameters =
          getQueryParameters(extensionRegistry, apiData, path);

      String operationSummary = "The endpoint used to trigger execution of a process";
      Optional<String> operationDescription =
          Optional.of(
              """
                  Trigger the execution of a process with specific inputs and an output selection. \

                  Certain processes can be executed asynchronously. If this is desired, `respond-async` should be included in the `Prefer` header. \

                  The response depends on the number of outputs requested, the negotiated content type for the response, the mode of execution, and whether an output is single- or multi-valued. For more information refer to the [draft document](https://docs.ogc.org/DRAFTS/18-062r3.html#_cacf1be8-0e26-1ccf-7dad-11c93a1e9427). """);
      ImmutableOgcApiResourceAuxiliary.Builder resourceBuilder =
          new ImmutableOgcApiResourceAuxiliary.Builder().path(path).pathParameters(pathParameters);

      Map<MediaType, ApiMediaTypeContent> requestContent = getRequestContent(apiData);

      ApiOperation.of(
              path,
              HttpMethods.POST,
              requestContent,
              queryParameters,
              ImmutableList.of(),
              operationSummary,
              operationDescription,
              Optional.empty(),
              getOperationId("executeProcess"),
              GROUP_PROCESSES_EXECUTE,
              TAGS,
              ProcessesCoreBuildingBlock.MATURITY,
              ProcessesCoreBuildingBlock.SPEC,
              false)
          .ifPresent(operation -> resourceBuilder.putOperations(method.name(), operation));

      definitionBuilder.putResources(path, resourceBuilder.build());
    }

    return definitionBuilder.build();
  }

  @POST
  @Path("/{processId}/execution")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response executeProcess(
      @PathParam("processId") String processId,
      @Context OgcApi api,
      @Context ApiRequestContext requestContext,
      @Context HttpHeaders headers,
      InputStream requestBody) {

    if (!isEnabledForApi(api.getData()))
      throw new NotFoundException("Processes are not available in this API.");

    checkPathParameter(
        extensionRegistry,
        api.getData(),
        "/processes/{processId}/execution",
        "processId",
        processId);

    String prefer = headers.getHeaderString("Prefer");
    boolean preferAsync =
        prefer != null
            && Arrays.stream(prefer.split(","))
                .anyMatch(p -> ("respond-async".equals(p.trim()) || "wait".equals(p.trim())));

    ExecutionQueriesHandler.QueryInputExecution queryInput =
        new ImmutableQueryInputExecution.Builder()
            .from(getGenericQueryInput(api.getData()))
            .processId(processId)
            .requestBody(requestBody)
            .preferAsync(preferAsync)
            .build();

    return queryHandler.handle(ExecutionQueriesHandler.Query.EXECUTE, queryInput, requestContext);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }

  @Override
  public List<? extends FormatExtension> getRequestFormats() {
    if (requestFormats == null)
      requestFormats = extensionRegistry.getExtensionsForType(ExecuteFormatExtension.class);
    return requestFormats;
  }

  @Override
  public List<? extends FormatExtension> getResourceFormats() {
    if (resourceFormats == null)
      resourceFormats =
          ImmutableList.<FormatExtension>builder()
              .addAll(extensionRegistry.getExtensionsForType(ResultsFormatExtension.class))
              .addAll(extensionRegistry.getExtensionsForType(StatusInfoFormatExtension.class))
              .addAll(extensionRegistry.getExtensionsForType(ValuesFormatExtension.class))
              .build();
    return resourceFormats;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(ProcessesCoreConfiguration.class)
        .filter(ProcessesCoreConfiguration::isEnabled)
        .isPresent();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queryHandler);
  }
}
