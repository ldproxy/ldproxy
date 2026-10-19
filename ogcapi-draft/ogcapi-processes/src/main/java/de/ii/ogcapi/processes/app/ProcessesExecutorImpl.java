/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.async.AsyncCallbacks;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatus;
import de.ii.ogcapi.common.domain.async.OgcApiJobStatusInfo;
import de.ii.ogcapi.foundation.domain.CompiledJsonSchema;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.processes.domain.OapJob;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.OgcBbox;
import de.ii.ogcapi.processes.domain.model.OgcBbox.CRS;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcFormat;
import de.ii.ogcapi.processes.domain.model.OgcInputDescription;
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.OgcSchema;
import de.ii.xtralink.jobs.Job;
import de.ii.xtralink.jobs.JobConfiguration;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.xtralink.domain.Jobs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessesExecutorImpl.class);

  private final ObjectMapper mapper;
  private final Jobs jobs;
  private final SchemaValidator schemaValidator;
  private final AsyncCallbacks asyncCallbacks;
  private final Map<String, Map<String, CompiledJsonSchema>> inputsSchemaCache;

  @Inject
  ProcessesExecutorImpl(
      Jackson jackson, Jobs jobs, SchemaValidator schemaValidator, AsyncCallbacks asyncCallbacks) {
    this.mapper = jackson.getDefaultObjectMapper();
    this.jobs = jobs;
    this.schemaValidator = schemaValidator;
    this.inputsSchemaCache = new ConcurrentHashMap<>();
    this.asyncCallbacks = asyncCallbacks;
  }

  @Override
  public Map<String, Object> executeSync(
      String apiId, OgcProcess process, OgcExecute executeRequest) {

    Map<String, Object> inputs = parseInputs(process, executeRequest.getInputs());

    Optional<Map<String, OgcFormat>> outputSelections = executeRequest.getOutputSelections();
    OapJobResultsSelector.validateOutputSelections(process, outputSelections);

    List<JobControlOptions> options = process.getJobControlOptions();
    if (options.contains(JobControlOptions.ASYNC_EXECUTE)
        && !options.contains(JobControlOptions.SYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + process.getId() + "' only supports async execution.");
    }

    // Create job
    JobConfiguration oapJob = OapJob.of(apiId, process, executeRequest);

    // Push it and wait for its results
    Job job = jobs.push(oapJob);
    LOGGER.debug("Created job for process '{}', job: {}", process.getId(), job);
    Job finalJob = jobs.waitFor(job.id()).join();

    // Return only selected results
    return OapJobResultsSelector.selectOutputs(finalJob.outputs(), outputSelections);
  }

  @Override
  public OgcApiJobStatus executeAsync(
      String apiId, OgcProcess process, OgcExecute executeRequest, int callbackRetries) {

    Map<String, Object> inputs = parseInputs(process, executeRequest.getInputs());

    Optional<Map<String, OgcFormat>> outputSelections = executeRequest.getOutputSelections();
    OapJobResultsSelector.validateOutputSelections(process, outputSelections);

    List<JobControlOptions> options = process.getJobControlOptions();
    if (!options.contains(JobControlOptions.ASYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + process.getId() + "' does not support async execution.");
    }

    // Create job
    JobConfiguration oapJob = OapJob.of(apiId, process, executeRequest);

    // Put job with callbacks in queue
    Job job = jobs.push(oapJob, asyncCallbacks);

    return OgcApiJobStatusInfo.of(job);
  }

  // Limitation: The draft allows for an input to have multiple schemas, but this implementation
  // only allows a single schema!
  private Map<String, Object> parseInputs(OgcProcess process, Map<String, Object> providedInputs) {
    Map<String, Object> parsedInputs = new LinkedHashMap<>();

    Map<String, OgcInputDescription> inputDescriptions = process.getInputs();
    inputDescriptions.forEach(
        (inputId, inputDescription) -> {
          int minOccurs = inputDescription.getMinOccurs();
          int maxOccurs = inputDescription.getMaxOccurs();
          validateOccurs(inputId, providedInputs, minOccurs, maxOccurs);
        });

    providedInputs.forEach(
        (inputId, value) -> {
          OgcInputDescription inputDescription = inputDescriptions.get(inputId);
          if (inputDescription == null) {
            throw new IllegalArgumentException(
                "Invalid execute request: input '%s' is not defined for this process."
                    .formatted(inputId));
          }

          OgcSchema schema = inputDescription.getSchema();

          // A schema describes a single instance. If the input is multi-valued, validate each
          // instance separately, see Requirement 78.
          if (inputDescription.getMaxOccurs() > 1) {
            List<Object> parsedInstances = new ArrayList<>();
            // Value must be a list, since we validated maxOccurs beforehand
            List<Object> instances = (List<Object>) value;
            for (int i = 0; i < instances.size(); i++) {
              int index = i;
              Object instance = instances.get(i);
              if (instance == null) {
                throw new IllegalArgumentException(
                    "Invalid execute request: An instance [%d] of input '%s' is null"
                        .formatted(i, inputId));
              }
              parsedInstances.add(parseInputValue(process.getId(), inputId, schema, instance));
            }
            parsedInputs.put(inputId, parsedInstances);
          } else {
            parsedInputs.put(inputId, parseInputValue(process.getId(), inputId, schema, value));
          }
        });
    return parsedInputs;
  }

  private void validateOccurs(
      String inputId, Map<String, Object> providedInputs, int minOccurs, int maxOccurs) {
    if (!providedInputs.containsKey(inputId)) {
      if (minOccurs > 0) {
        throw new IllegalArgumentException(
            "Invalid execute request: input '" + inputId + "' must be provided!");
      }
      return;
    }

    Object value = providedInputs.get(inputId);
    if (value == null) {
      throw new IllegalArgumentException(
          "Invalid execute request: input '" + inputId + "' is null");
    }

    if (maxOccurs > 1) {
      if (!(value instanceof List)) {
        throw new IllegalArgumentException(
            "Invalid execute request: input '" + inputId + "' must be an array!");
      }

      int listSize = ((List<Object>) value).size();
      if (listSize > maxOccurs) {
        if (maxOccurs == minOccurs) {
          throw new IllegalArgumentException(
              "Invalid execute request: input '"
                  + inputId
                  + "' must have "
                  + maxOccurs
                  + " entries!");
        }
        throw new IllegalArgumentException(
            "Invalid execute request: input '"
                + inputId
                + "' must have a maximum of "
                + maxOccurs
                + " entries!");
      }

      if (listSize < minOccurs) {
        if (maxOccurs == minOccurs) {
          throw new IllegalArgumentException(
              "Invalid execute request: input '"
                  + inputId
                  + "' must have "
                  + maxOccurs
                  + " entries!");
        }
        throw new IllegalArgumentException(
            "Invalid execute request: input '"
                + inputId
                + "' must have at least "
                + minOccurs
                + " entries!");
      }
    }
  }

  private Optional<String> validateInputValue(
      String processId, String inputId, OgcSchema schema, Object value) {
    Map<String, CompiledJsonSchema> schemaCache =
        inputsSchemaCache.computeIfAbsent(processId, k -> new ConcurrentHashMap<>());

    CompiledJsonSchema compiledSchema =
        schemaCache.computeIfAbsent(
            inputId,
            k -> {
              String schemaString;
              try {
                schemaString = mapper.writeValueAsString(schema);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(
                    "Could not serialize Schema of input '%s'.".formatted(inputId), e);
              }

              try {
                return schemaValidator.compile(schemaString);
              } catch (IOException e) {
                throw new RuntimeException(
                    "Could not compile schema for String '%s'.".formatted(schemaString), e);
              }
            });

    String jsonContent;
    try {
      jsonContent = mapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new RuntimeException("Could not serialize input '" + inputId + "'.", e);
    }

    try {
      return schemaValidator.validate(compiledSchema, jsonContent);
    } catch (IOException e) {
      throw new RuntimeException("Could not validate input '" + inputId + "'.", e);
    }
  }

  // Limitation: Not all standard JSON Schema formats and custom formats from OGC-Processes are
  // supported
  private Object parseInputValue(String processId, String inputId, OgcSchema schema, Object value) {
    validateInputValue(processId, inputId, schema, value)
        .ifPresent(
            message -> {
              throw new IllegalArgumentException(
                  "Invalid execute request: input '%s' is invalid: %s".formatted(inputId, message));
            });

    if (schema.getFormat().isEmpty()) {
      return value;
    }

    String format = schema.getFormat().get();
    switch (format) {
      case "ogc-bbox":
      case "https://www.opengis.net/def/format/ogcapi-processes/0/ogc-bbox":
        OgcBbox bbox;
        try {
          bbox = mapper.convertValue(value, OgcBbox.class);

        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Invalid execute request: Content of input '%s' cannot be converted to a bbox: %s"
                  .formatted(inputId, e));
        }

        EpsgCrs epsgCrs = Objects.equals(bbox.getCrs(), CRS.CRS84h) ? OgcCrs.CRS84h : OgcCrs.CRS84;

        List<Double> coordinates = bbox.getBbox();
        int coordinatesCount = coordinates.size();

        BoundingBox boundingBox;
        if (coordinatesCount == 4) {
          boundingBox =
              BoundingBox.of(
                  coordinates.get(0),
                  coordinates.get(1),
                  coordinates.get(2),
                  coordinates.get(3),
                  epsgCrs);
        } else {
          if (coordinatesCount == 6) {
            boundingBox =
                BoundingBox.of(
                    coordinates.get(0),
                    coordinates.get(1),
                    coordinates.get(2),
                    coordinates.get(3),
                    coordinates.get(4),
                    coordinates.get(5),
                    epsgCrs);
          } else {
            throw new IllegalArgumentException(
                "Invalid execute request: Content of input '%s' cannot be converted to a bbox: Bbox must contain either 4 (2D) or 6 (3D) coordinates"
                    .formatted(inputId));
          }
        }
        return boundingBox;
      case "date-time":
        Instant time;
        try {
          time = mapper.convertValue(value, Instant.class);
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Invalid execute request: Content of input '%s' cannot be converted to an internal time representation.: %s"
                  .formatted(inputId, e.getMessage()));
        }
        if (time == null) {
          throw new IllegalArgumentException(
              "Invalid execute request: Content of input '%s' cannot be converted to an internal time representation."
                  .formatted(inputId));
        }
        return time;
      default:
        return value;
    }
  }
}
