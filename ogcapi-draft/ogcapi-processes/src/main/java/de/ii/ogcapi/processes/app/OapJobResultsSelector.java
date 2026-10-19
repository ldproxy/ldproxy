/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.async.JobResultsSelector;
import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.ogcapi.processes.domain.OapJob.OapJobContext;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcFormat;
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@AutoBind
public class OapJobResultsSelector implements JobResultsSelector {

  private final ProcessRepository processRepository;
  private final ObjectMapper mapper;

  @Inject
  OapJobResultsSelector(ProcessRepository processRepository, Jackson jackson) {
    this.processRepository = processRepository;
    this.mapper = jackson.getDefaultObjectMapper();
  }

  @Override
  public boolean canHandle(Map<String, Object> context) {
    return OapJobContext.is(context);
  }

  @Override
  public Map<String, Object> selectResults(
      Map<String, Object> results, Map<String, Object> context) {
    OgcExecute executeRequest;
    try {
      executeRequest = mapper.convertValue(context.get("executeRequest"), OgcExecute.class);
    } catch (Throwable e) {
      return results;
    }

    return selectOutputs(results, executeRequest.getOutputSelections());
  }

  @Override
  public Object selectSpecificResult(
      Map<String, Object> results, Map<String, Object> context, String kind, String resultKey) {
    String apiId = JobContextEntity.from(context);
    String processId = OapJob.processId(kind);
    OgcProcess process = processRepository.getDirect(apiId, processId);

    validateOutputSelection(process, resultKey);

    Map<String, Object> selectedResults = selectResults(results, context);

    validateOutput(selectedResults, resultKey);

    return selectedResults.get(resultKey);
  }

  @Override
  public Object selectSpecificResultN(
      Map<String, Object> results,
      Map<String, Object> context,
      String kind,
      String resultKey,
      int resultIndex) {
    String apiId = JobContextEntity.from(context);
    String processId = OapJob.processId(kind);
    OgcProcess process = processRepository.getDirect(apiId, processId);

    validateOutputSelection(process, resultKey);

    int maxOccurs = process.getOutputs().get(resultKey).getMaxOccurs();
    if (maxOccurs <= 1) {
      throw new IllegalArgumentException(
          "Output '" + resultKey + "' is not multi-valued (MaxOccurs: " + maxOccurs + ")");
    }

    if (maxOccurs <= resultIndex) {
      throw new IllegalArgumentException(
          "Out of bound for '" + resultIndex + "' (MaxOccurs: " + maxOccurs + ")");
    }

    Object value = selectSpecificResult(results, context, kind, resultKey);

    if (!(value instanceof List list)) {
      throw new BadRequestException("The output '%s' is not an Array".formatted(resultKey));
    }

    int size = list.size();
    if (size <= resultIndex) {
      throw new IllegalArgumentException(
          "Out of bound for '%d' (MaxOccurs: %d, list size: %d)"
              .formatted(resultIndex, maxOccurs, size));
    }

    return list.get(resultIndex);
  }

  // Limitation: MediaType, Encoding and Schema are not used in the selection!
  static Map<String, Object> selectOutputs(
      Map<String, Object> outputs, Optional<Map<String, OgcFormat>> outputSelections) {

    // Requirement 27 and Requirement 33
    if (outputSelections.isEmpty()) {
      return outputs;
    }

    Map<String, OgcFormat> selection = outputSelections.get();

    // Requirement 28 and Requirement 34
    if (selection.isEmpty()) {
      return Map.of();
    }

    Map<String, Object> selectedOutput = new LinkedHashMap<>();
    selection
        .keySet()
        .forEach(
            outputId -> {
              // Requirement ???
              validateOutput(outputs, outputId);

              selectedOutput.put(outputId, outputs.get(outputId));
            });

    return selectedOutput;
  }

  private static void validateOutput(Map<String, Object> outputs, String outputKey) {
    if (!outputs.containsKey(outputKey)) {
      throw new NotFoundException("The results do not contain an output '" + outputKey + "'.");
    }
  }

  static void validateOutputSelections(
      OgcProcess process, Optional<Map<String, OgcFormat>> outputSelections) {
    if (outputSelections.isEmpty()) {
      return;
    }

    outputSelections
        .get()
        .keySet()
        .forEach(
            outputId -> {
              validateOutputSelection(process, outputId);
            });
  }

  private static void validateOutputSelection(OgcProcess process, String outputId) {

    if (!process.getOutputs().containsKey(outputId)) {
      throw new NotFoundException(
          "The output of the process '"
              + process.getId()
              + "', does not contain an output '"
              + outputId
              + "'.");
    }
  }
}
