/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface QueryParameterTemplateParameterView {
  String getApiId();

  String getQueryId();

  Schema<?> getSchema();

  String getName();

  String getDescription();

  boolean getExplode();

  Optional<SpecificationMaturity> getSpecificationMaturity();

  Optional<ExternalDocumentation> getSpecificationRef();
}
