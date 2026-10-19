/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ii.xtraplatform.xtralink.domain.JobContext.JobContextEntity;
import java.util.Map;
import java.util.Optional;

public class JobContextAsync extends JobContextEntity {

  public static boolean is(Map<String, Object> context) {
    return JobContextEntity.is(context) && context.containsKey("entityType");
  }

  public static OgcApiJobStatus.EntityType entityTypeFrom(Map<String, Object> context) {
    return OgcApiJobStatus.EntityType.fromValue((String) context.getOrDefault("entityType", ""));
  }

  public static Optional<String> processIdFrom(Map<String, Object> context) {
    return Optional.ofNullable((String) context.get("processId"));
  }

  public static Optional<AsyncSubscriber> asyncSubscriberFrom(
      Map<String, Object> context, ObjectMapper mapper) {
    if (!is(context) || !context.containsKey("subscriber")) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(
          mapper.convertValue(context.get("subscriber"), AsyncSubscriber.class));
    } catch (Throwable e) {
      return Optional.empty();
    }
  }

  private final OgcApiJobStatus.EntityType entityType;
  private final Optional<String> processId;
  private final Optional<AsyncSubscriber> subscriber;

  public JobContextAsync(
      String entity,
      OgcApiJobStatus.EntityType entityType,
      Optional<String> processId,
      Optional<AsyncSubscriber> subscriber) {
    super(entity);
    this.entityType = entityType;
    this.processId = processId;
    this.subscriber = subscriber;
  }

  public OgcApiJobStatus.EntityType getEntityType() {
    return entityType;
  }

  public Optional<String> getProcessId() {
    return processId;
  }

  public Optional<AsyncSubscriber> getSubscriber() {
    return subscriber;
  }
}
