/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.ii.ogcapi.processes.domain.model.ogc.OgcMetadata;
import java.util.List;
import java.util.Optional;

public interface DescriptionType {

  Optional<String> getTitle();

  Optional<String> getDescription();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<String> getKeywords();

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<OgcMetadata> getMetadata();
}
