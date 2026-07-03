/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import de.ii.ogcapi.processes.app.model.ProcessImpl;
import de.ii.ogcapi.processes.domain.model.io.InputDescription;
import de.ii.ogcapi.processes.domain.model.io.OutputDescription;
import java.util.Optional;

public interface Process extends ProcessSummary {

  static Process custom(ProcessData data) {
    return new ProcessImpl(data);
  }

  Optional<InputDescription> getInputDescription();

  Optional<OutputDescription> getOutputDescription();
}
