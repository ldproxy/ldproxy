/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.infra;

import de.ii.ogcapi.foundation.domain.ApiEndpointDefinition;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.Endpoint;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.processes.domain.model.ProcessRepository;
import java.util.Objects;

/**
 * An endpoint with the path parameter `processId`. The ids of the processes are part of the
 * definition, which is therefore derived again when the processes of the API change, for example
 * after a reload of the values.
 */
abstract class EndpointProcessIds extends Endpoint implements ApiExtensionHealth {

  private final ProcessRepository processRepository;

  EndpointProcessIds(ExtensionRegistry extensionRegistry, ProcessRepository processRepository) {
    super(extensionRegistry);
    this.processRepository = processRepository;
  }

  @Override
  public ApiEndpointDefinition getDefinition(OgcApiDataV2 apiData) {
    if (!isEnabledForApi(apiData) || !getComposedVolatile(apiData).isAvailable()) {
      return super.getDefinition(apiData);
    }

    int key = Objects.hash(apiData.hashCode(), processRepository.getAll(apiData.getId()).keySet());
    Integer previous = apiHashes.put(apiData.getId(), key);
    if (Objects.nonNull(previous) && previous != key) {
      apiDefinitions.remove(previous);
    }

    return apiDefinitions.computeIfAbsent(key, ignore -> computeDefinition(apiData));
  }
}
