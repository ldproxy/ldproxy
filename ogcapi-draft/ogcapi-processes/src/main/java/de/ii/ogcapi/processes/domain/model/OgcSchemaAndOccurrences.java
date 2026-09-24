/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model;

/**
 * See the following link for its OpenAPI 3.0 schema
 * https://raw.githubusercontent.com/opengeospatial/ogcapi-processes/master/openapi/schemas/processes-core/schemaAndOccurrences.yaml
 *
 * <p>Limitations: - No unbounded for maxOccurs
 */
public interface OgcSchemaAndOccurrences {

  OgcSchema getSchema();

  int getMinOccurs();

  int getMaxOccurs();
}
