/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import jakarta.validation.constraints.Min;
import org.immutables.value.Value;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/outputDescription.yaml
 *
 * <p>Limitations: ``` - The following extensions are missing: - dataClasses.yaml - dataAccessAPIs
 * ```
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcOutputDescription.Builder.class)
public interface OgcOutputDescription extends OgcDescriptionType, OgcSchemaAndOccurrences {

  @SuppressWarnings("UnstableApiUsage")
  Funnel<OgcOutputDescription> FUNNEL =
      (from, into) -> {
        OgcDescriptionType.FUNNEL.funnel(from, into);
        OgcSchema.FUNNEL.funnel(from.getSchema(), into);
        into.putInt(from.getMinOccurs());
        into.putInt(from.getMaxOccurs());
      };

  @Override
  @Value.Default
  @Min(0)
  default int getMinOccurs() {
    return 1;
  }

  @Override
  @Value.Default
  @Min(1)
  default int getMaxOccurs() {
    return 1;
  }

  @Value.Check
  default OgcOutputDescription validate() {
    if (getMinOccurs() < 0) {
      throw new IllegalStateException("minOccurs (" + getMinOccurs() + ") must be >= 0");
    }

    if (getMaxOccurs() < 1) {
      throw new IllegalStateException("maxOccurs (" + getMaxOccurs() + ") must be >= 1");
    }

    if (getMinOccurs() > getMaxOccurs()) {
      throw new IllegalStateException(
          "minOccurs (" + getMinOccurs() + ") must be <= maxOccurs (" + getMaxOccurs() + ")");
    }
    return this;
  }
}
