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
import de.ii.ogcapi.foundation.domain.CompiledJsonSchema;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.processes.domain.ProcessesExecutor;
import de.ii.ogcapi.processes.domain.model.OgcBbox;
import de.ii.ogcapi.processes.domain.model.OgcBbox.CRS;
import de.ii.ogcapi.processes.domain.model.OgcExecute;
import de.ii.ogcapi.processes.domain.model.OgcFormat;
import de.ii.ogcapi.processes.domain.model.OgcInputDescription;
import de.ii.ogcapi.processes.domain.model.OgcProcess;
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions;
import de.ii.ogcapi.processes.domain.model.OgcSchema;
import de.ii.ogcapi.processes.domain.model.OgcStatusInfo;
import de.ii.ogcapi.processes.domain.model.OgcSubscriber;
import de.ii.ogcapi.processes.domain.model.web.ImmutableResultsResponse;
import de.ii.ogcapi.processes.domain.model.web.ImmutableStatusInfoResponse;
import de.ii.ogcapi.processes.domain.model.web.ResultsResponse;
import de.ii.ogcapi.processes.domain.model.web.StatusInfoResponse;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.jobs.domain.JobQueueV2;
import de.ii.xtraplatform.jobs.domain.JobV2;
import de.ii.xtraplatform.jobs.domain.JobV2.Status;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProcessesExecutorImpl implements ProcessesExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessesExecutorImpl.class);

  private final ObjectMapper mapper;
  private final JobQueueV2 jobQueue;
  private final SchemaValidator schemaValidator;
  private final Map<String, Map<String, CompiledJsonSchema>> inputsSchemaCache;

  private final HttpClient httpClient;

  @Inject
  ProcessesExecutorImpl(
      Http http, Jackson jackson, JobQueueV2 jobQueue, SchemaValidator schemaValidator) {
    this.httpClient = http.getDefaultClient();
    this.mapper = jackson.getDefaultObjectMapper();
    this.jobQueue = jobQueue;
    this.schemaValidator = schemaValidator;
    inputsSchemaCache = new ConcurrentHashMap<>();
  }

  @Override
  public Map<String, Object> executeSync(OgcProcess process, OgcExecute executeRequest) {

    Map<String, Object> inputs = new LinkedHashMap<>(executeRequest.getInputs());
    validateAndUpdateInputs(process, inputs);

    Optional<Map<String, OgcFormat>> outputSelections = executeRequest.getOutputSelections();
    validateOutputSelections(process, outputSelections);

    List<JobControlOptions> options = process.getJobControlOptions();
    if (options.contains(JobControlOptions.ASYNC_EXECUTE)
        && !options.contains(JobControlOptions.SYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + process.getId() + "' only supports async execution.");
    }

    // Create job
    JobV2 job = jobQueue.createJob(process.getId(), inputs);

    // Push it and wait for its results
    Map<String, Object> jobResults = jobQueue.push(job).join().getOutputs();
    validateResults(jobResults, outputSelections);

    // Return only selected results
    return selectOutputs(jobResults, outputSelections);
  }

  @Override
  public OgcStatusInfo executeAsync(
      OgcProcess process, OgcExecute executeRequest, int callbackRetries) {

    Map<String, Object> inputs = new LinkedHashMap<>(executeRequest.getInputs());
    validateAndUpdateInputs(process, inputs);

    Optional<Map<String, OgcFormat>> outputSelections = executeRequest.getOutputSelections();
    validateOutputSelections(process, outputSelections);

    List<JobControlOptions> options = process.getJobControlOptions();
    if (!options.contains(JobControlOptions.ASYNC_EXECUTE)) {
      throw new IllegalArgumentException(
          "Process '" + process.getId() + "' does not support async execution.");
    }

    // Create job
    Map<String, Object> jobDetails = new LinkedHashMap<>();
    jobDetails.put("process", process);
    jobDetails.put("callbackRetries", callbackRetries);
    jobDetails.put("subscriber", executeRequest.getSubscriber());
    jobDetails.put("outputSelections", outputSelections);
    JobV2 job = jobQueue.createJob(process.getId(), inputs, jobDetails);

    // Put job with callBack in queue
    jobQueue.push(job, this::callBack);

    return StatusInfoResponse.of(job);
  }

  @Override
  public Optional<OgcStatusInfo> getStatusInfo(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null) {
      return Optional.empty();
    }
    return Optional.of(StatusInfoResponse.of(job));
  }

  @Override
  public Map<String, Object> getResults(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null) {
      throw new NotFoundException("No job found with job id '" + jobId + "'.");
    }

    if (job.getStatus() != JobV2.Status.SUCCESSFUL) {
      throw new IllegalStateException("Status of job '" + jobId + "' is not SUCCESSFUL");
    }

    Optional<Map<String, OgcFormat>> outputsSelections =
        (Optional<Map<String, OgcFormat>>) job.getDetails().get("outputSelections");
    Map<String, Object> jobResults = job.getOutputs();
    validateResults(jobResults, outputsSelections);
    return selectOutputs(jobResults, outputsSelections);
  }

  @Override
  public Object getResultsSpecific(String jobId, String outputId) {
    OgcProcess process = getProcessDirect(jobId);
    validateOutputSelection(process, outputId);
    Map<String, Object> jobResults = getResults(jobId);
    validateResult(jobResults, outputId);
    return jobResults.get(outputId);
  }

  @Override
  public Object getResultsSpecificN(String jobId, String outputId, int index) {
    OgcProcess process = getProcessDirect(jobId);
    validateOutputSelection(process, outputId);

    int maxOccurs = process.getOutputs().get(outputId).getMaxOccurs();
    if (maxOccurs <= 1) {
      throw new IllegalArgumentException(
          "Output '" + outputId + "' is not multi-valued (MaxOccurs: " + maxOccurs + ")");
    }

    if (maxOccurs <= index) {
      throw new IllegalArgumentException(
          "Out of bound for '" + index + "' (MaxOccurs: " + maxOccurs + ")");
    }

    Object value = getResultsSpecific(jobId, outputId);

    if (!(value instanceof List list)) {
      throw new IllegalStateException(
          "The output '" + outputId + "' of job '" + jobId + "' is not an Array");
    } else {
      int size = list.size();
      if (size <= index) {
        throw new IllegalArgumentException(
            "Out of bound for '"
                + index
                + "' (MaxOccurs: "
                + maxOccurs
                + ", list size: "
                + size
                + ")");
      }
      return list.get(index);
    }
  }

  @Override
  public Optional<OgcStatusInfo> dismissJob(String jobId) {
    JobV2.Status currentStatus = getStatusInfoDirect(jobId).getStatus();

    // Only cancel job if it's accepted or running. Successful, dismissed and failed jobs keep their
    // status. Note: this behavior is intentionally different from Requirement 114!
    if (Status.ACCEPTED.equals(currentStatus) || Status.RUNNING.equals(currentStatus)) {
      jobQueue.cancel(jobId);
    }

    return getStatusInfo(jobId);
  }

  /*
  input validation and format apply
   */
  // Limitation: The draft allows for an input to have multiple schemas, but this implementation
  // only allows a single schema!
  private void validateAndUpdateInputs(OgcProcess process, Map<String, Object> providedInputs) {
    Map<String, CompiledJsonSchema> schemaCache =
        inputsSchemaCache.computeIfAbsent(process.getId(), k -> new ConcurrentHashMap<>());

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
                "Invalid execute request: input '"
                    + inputId
                    + "' is not defined for this process.");
          }

          OgcSchema schema = inputDescription.getSchema();

          CompiledJsonSchema compiledSchema =
              schemaCache.computeIfAbsent(
                  inputId,
                  k -> {
                    String schemaString;
                    try {
                      schemaString = mapper.writeValueAsString(schema);
                    } catch (JsonProcessingException e) {
                      throw new RuntimeException(
                          "Could not serialize Schema of input '" + inputId + "'.", e);
                    }

                    try {
                      return schemaValidator.compile(schemaString);
                    } catch (IOException e) {
                      throw new RuntimeException(
                          "Could not compile schema for String '" + schemaString + "'.", e);
                    }
                  });

          // A schema describes a single instance. If the input is multi-valued, validate each
          // instance separately, see Requirement 78.
          if (inputDescription.getMaxOccurs() > 1) {
            // Value must be a list, since we validated maxOccurs beforehand
            List<Object> instances = (List<Object>) value;
            for (int i = 0; i < instances.size(); i++) {
              int index = i;
              Object instance = instances.get(i);
              if (instance == null) {
                throw new IllegalArgumentException(
                    "Invalid execute request: An instance ["
                        + i
                        + "] of input '"
                        + inputId
                        + "' is null");
              }
              validateInstance(inputId, compiledSchema, instance)
                  .ifPresent(
                      message -> {
                        throw new IllegalArgumentException(
                            "Invalid execute request: An instance of input '"
                                + inputId
                                + "' is invalid: "
                                + message);
                      });

              applyFormat(inputId, schema, instance)
                  .ifPresent(updatedObject -> instances.set(index, updatedObject));
            }
          } else {
            validateInstance(inputId, compiledSchema, value)
                .ifPresent(
                    message -> {
                      throw new IllegalArgumentException(
                          "Invalid execute request: input '"
                              + inputId
                              + "' is invalid: "
                              + message);
                    });
            applyFormat(inputId, schema, value)
                .ifPresent(updatedObject -> providedInputs.put(inputId, updatedObject));
          }
        });
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

  private Optional<String> validateInstance(
      String inputId, CompiledJsonSchema compiledSchema, Object value) {
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

  private Optional<Object> applyFormat(String inputId, OgcSchema schema, Object value) {
    if (schema.getFormat().isEmpty()) {
      return Optional.empty();
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
              "Invalid execute request: Content of input '"
                  + inputId
                  + "' cannot be converted to a bbox: "
                  + e);
        }

        EpsgCrs epsgCrs;
        if (bbox.getCrs().equals(CRS.CRS84h)) {
          epsgCrs = EpsgCrs.fromString("http://www.opengis.net/def/crs/OGC/0/CRS84h");
        } else {
          epsgCrs = EpsgCrs.fromString("http://www.opengis.net/def/crs/OGC/1.3/CRS84");
        }

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
                "Invalid execute request: Content of input '"
                    + inputId
                    + "' cannot be converted to a bbox: "
                    + "Bbox must contain either 4 (2D) or 6 (3D) coordinates");
          }
        }
        return Optional.of(boundingBox);
      case "date-time":
        Instant time;
        try {
          time = mapper.convertValue(value, Instant.class);
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Invalid execute request: Content of input '"
                  + inputId
                  + "' cannot be converted to an internal time representation.: "
                  + e);
        }
        if (time == null) {
          throw new IllegalArgumentException(
              "Invalid execute request: Content of input '"
                  + inputId
                  + "' cannot be converted to an internal time representation.");
        }
        return Optional.of(time);
      default:
        // Limitation : Not all standard JSON Schema formats are supported
        return Optional.empty();
    }
  }

  /*
  output validation and selection
   */
  private void validateOutputSelections(
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

  private void validateOutputSelection(OgcProcess process, String outputId) {

    if (!process.getOutputs().containsKey(outputId)) {
      throw new NotFoundException(
          "The output of the process '"
              + process.getId()
              + "', does not contain an output '"
              + outputId
              + "'.");
    }
  }

  private void validateResults(
      Map<String, Object> jobResults, Optional<Map<String, OgcFormat>> outputSelections) {
    if (outputSelections.isEmpty()) {
      return;
    }

    outputSelections
        .get()
        .keySet()
        .forEach(
            outputId -> {
              validateResult(jobResults, outputId);
            });
  }

  private void validateResult(Map<String, Object> jobResults, String outputId) {

    if (!jobResults.containsKey(outputId)) {
      throw new NotFoundException("The results do not contain an output '" + outputId + "'.");
    }
  }

  // Limitation: MediaType, Encoding and Schema are not used in the selection!
  private Map<String, Object> selectOutputs(
      Map<String, Object> output, Optional<Map<String, OgcFormat>> outputSelections) {

    // Requirement 27 and Requirement 33
    if (outputSelections.isEmpty()) {
      return output;
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
              selectedOutput.put(outputId, output.get(outputId));
            });

    return selectedOutput;
  }

  /*
  callBack
   */
  private void callBack(JobV2 job) {
    Optional<OgcSubscriber> subscriber =
        (Optional<OgcSubscriber>) job.getDetails().get("subscriber");
    if (subscriber.isEmpty()) {
      return;
    }

    String jobId = job.getId();
    JobV2.Status status = job.getStatus();
    int callbackRetries = (Integer) (job.getDetails().get("callbackRetries"));
    switch (status) {
      case SUCCESSFUL -> callBackOnSuccess(jobId, subscriber.get(), callbackRetries);
      case FAILED -> callBackOnFailure(jobId, subscriber.get(), callbackRetries);
      case RUNNING -> callBackOnProgress(jobId, subscriber.get(), callbackRetries);
    }
  }

  private void callBackOnSuccess(String jobId, OgcSubscriber subscriber, int callbackRetries) {
    if (subscriber.getSuccessUri().isEmpty()) {
      return;
    }

    Map<String, Object> results = getResults(jobId);

    byte[] payload = new byte[0];
    if (!results.isEmpty()) {
      ResultsResponse resultsResponse =
          new ImmutableResultsResponse.Builder().additionalProperties(results).build();

      try {
        payload = mapper.writeValueAsBytes(resultsResponse);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    int currentRetries = 0;
    do {
      try {
        httpClient.postAsInputStream(
            subscriber.getSuccessUri().get(),
            payload,
            MediaType.APPLICATION_JSON_TYPE,
            Map.of("Accept", MediaType.APPLICATION_JSON));
        break;

      } catch (Exception e) {
        if (currentRetries < callbackRetries) {
          int delay = 100 * (currentRetries + 1);
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "Failed send success callback for job '{}', retrying in {}ms", jobId, delay);
          }
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ex) {
            // ignore
          }
        } else {
          LogContext.error(
              LOGGER,
              e,
              "Giving up sending success callback for job '{}' after {} retries",
              jobId,
              currentRetries);
          LOGGER.error(
              "Failed sending the success callback for {}: {}",
              jobId,
              new String(payload, StandardCharsets.UTF_8));
        }
      }
      currentRetries++;
    } while (currentRetries <= callbackRetries);
  }

  private void callBackOnFailure(String jobId, OgcSubscriber subscriber, int callbackRetries) {
    subscriber
        .getFailedUri()
        .ifPresent(uri -> postStatusInfo(jobId, uri, "failed", callbackRetries));
  }

  private void callBackOnProgress(String jobId, OgcSubscriber subscriber, int callbackRetries) {
    subscriber
        .getInProgressUri()
        .ifPresent(uri -> postStatusInfo(jobId, uri, "inProgress", callbackRetries));
  }

  private void postStatusInfo(String jobId, String uri, String callbackType, int callbackRetries) {

    byte[] response;
    try {
      OgcStatusInfo statusInfo = getStatusInfoDirect(jobId);
      StatusInfoResponse statusInfoResponse =
          new ImmutableStatusInfoResponse.Builder().from(statusInfo).build();
      response = mapper.writeValueAsBytes(statusInfoResponse);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    int currentRetries = 0;
    do {
      try {
        httpClient.postAsInputStream(
            uri,
            response,
            MediaType.APPLICATION_JSON_TYPE,
            Map.of("Accept", MediaType.APPLICATION_JSON));
        break;
      } catch (Exception e) {
        if (currentRetries < callbackRetries) {
          int delay = 100 * (currentRetries + 1);
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "Failed send {} callback for job '{}', retrying in {}ms",
                callbackType,
                jobId,
                delay);
          }
          try {
            Thread.sleep(delay);
          } catch (InterruptedException ex) {
            // ignore
          }
        } else {
          LogContext.error(
              LOGGER,
              e,
              "Giving up sending {} callback for job '{}' after {} retries",
              callbackType,
              jobId,
              currentRetries);
          LOGGER.error(
              "Failed sending the {} callback for {}: {}",
              callbackType,
              jobId,
              new String(response, StandardCharsets.UTF_8));
        }
      }
      currentRetries++;
    } while (currentRetries <= callbackRetries);
  }

  /*
  misc
   */
  private OgcStatusInfo getStatusInfoDirect(String jobId) {
    return getStatusInfo(jobId)
        .orElseThrow(() -> new NotFoundException("No job found with job id '" + jobId + "'."));
  }

  private OgcProcess getProcessDirect(String jobId) {
    JobV2 job = jobQueue.get(jobId);
    if (job == null) {
      throw new NotFoundException("No job found with job id '" + jobId + "'.");
    }
    return (OgcProcess) (job.getDetails().get("process"));
  }
}
