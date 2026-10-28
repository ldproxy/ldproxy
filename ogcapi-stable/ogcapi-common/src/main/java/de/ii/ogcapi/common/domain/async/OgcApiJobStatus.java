/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import de.ii.ogcapi.common.domain.OgcApiException;
import de.ii.xtralink.jobs.Identifiers;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/statusInfo.yaml
 */
public interface OgcApiJobStatus {

  enum EntityType {
    OGC_API_PROCESSES(
        "ogc-api-processes", "https://www.opengis.net/def/exceptions/ogcapi-processes-1/1.0/"),
    OGC_API_FEATURES(
        "ogc-api-features", "http://www.opengis.net/def/exceptions/ogcapi-features-11/1.0/"),
    UNKNOWN("unknown", null),
    ;

    private final String value;
    private final String exceptionTypePrefix;

    EntityType(String value, String exceptionTypePrefix) {
      this.value = value;
      this.exceptionTypePrefix = exceptionTypePrefix;
    }

    /** The exception type URI the specification of this entity type defines for {@code name}. */
    public Optional<String> exceptionType(String name) {
      return Optional.ofNullable(exceptionTypePrefix).map(prefix -> prefix + name);
    }

    static EntityType fromValue(String value) {
      for (EntityType type : EntityType.values()) {
        if (type.value.equals(value)) {
          return type;
        }
      }
      return UNKNOWN;
    }

    @JsonValue
    public String getValue() {
      return value;
    }
  }

  String getId();

  @JsonIgnore
  Identifiers.Status getStatus();

  /** The status as encoded in the specification, e.g. {@code successful}. */
  @JsonProperty("status")
  default String getStatusCode() {
    return getStatus().name().toLowerCase(Locale.ROOT);
  }

  EntityType getProcessingEntityType();

  @JsonProperty("processID")
  Optional<String> getProcessId();

  Optional<Object> getRequest();

  Optional<String> getMessage();

  Optional<OgcApiException> getException();

  Optional<Instant> getCreated();

  Optional<Instant> getStarted();

  Optional<Instant> getFinished();

  Optional<Instant> getUpdated();

  @Min(0)
  @Max(100)
  Optional<Integer> getProgress();

  /** The media type of the result document, if the job has one, see {@link JobResultFile}. */
  @JsonIgnore
  Optional<String> getResultMediaType();
}
