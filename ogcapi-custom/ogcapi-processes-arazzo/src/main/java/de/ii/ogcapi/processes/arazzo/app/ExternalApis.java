/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

/** The external APIs that the steps of a workflow call over HTTP. */
interface ExternalApis {

  /**
   * @param url the absolute URL of an OpenAPI document
   * @param maximumResponseSize the maximum size of the document in bytes
   * @return the API that the document describes
   * @throws WorkflowException if the URL is not allowed or the document cannot be read
   */
  ExternalApi api(String url, int maximumResponseSize);

  /**
   * @param request a request to an operation of an external API
   * @param maximumResponseSize the maximum size of the content of the response in bytes
   * @return the response; a request that cannot be sent or a response that cannot be read is a
   *     response with the status code 502
   * @throws WorkflowException if the URL of the request is not allowed
   */
  StepResponse dispatch(StepRequest request, int maximumResponseSize);
}
