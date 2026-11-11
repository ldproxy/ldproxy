/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument;
import java.util.Optional;

/** The stored Arazzo descriptions in {@code values/workflows}. */
public interface WorkflowRepository {

  /**
   * @return the stored description with the workflow that is the process, if any
   */
  Optional<ArazzoDocument> getByWorkflow(String apiId, String workflowId);

  /**
   * @param documentId the name of the file without the extension
   * @return the stored description of the API, or a global one, if any
   */
  Optional<ArazzoDocument> getDocument(String apiId, String documentId);
}
