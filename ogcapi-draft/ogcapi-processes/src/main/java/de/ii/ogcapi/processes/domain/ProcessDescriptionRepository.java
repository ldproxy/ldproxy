/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain;

import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.Map;
import java.util.Optional;

public interface ProcessDescriptionRepository extends Volatile2 {

  Optional<ProcessDescription> get(String processId);

  Map<String, ProcessDescription> getAll();
}
