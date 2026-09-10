/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.Map;
import java.util.Optional;

public interface ProcessRepository extends Volatile2 {

  OgcProcess getDirect(OgcApiDataV2 apiData, String processId);

  Optional<OgcProcess> get(OgcApiDataV2 apiData, String processId);

  Map<String, OgcProcess> getAll(OgcApiDataV2 apiData);
}
