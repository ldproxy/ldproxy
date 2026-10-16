/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * A link in the OpenAPI definition from a response of another operation (the source) to the
 * operation that declares the link: the parameters of the operation can be taken from the request
 * or the response of the source operation. The link is added to the response of the source
 * operation, if the definition contains both operations.
 */
@Value.Immutable
public interface ApiOperationLink {

  /**
   * @return the name of the link in the response of the source operation
   */
  String getName();

  /**
   * @return the id of the source operation
   */
  String getOperationId();

  /**
   * @return the status code of the response of the source operation
   */
  @Value.Default
  default String getStatusCode() {
    return ApiOperation.STATUS_200;
  }

  /**
   * @return the parameters of the operation that declares the link, the values are runtime
   *     expressions, e.g. {@code $response.body#/features/0/id}
   */
  Map<String, String> getParameters();

  Optional<String> getDescription();
}
