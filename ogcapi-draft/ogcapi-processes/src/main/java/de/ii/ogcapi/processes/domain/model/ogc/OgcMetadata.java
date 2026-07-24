/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.ogc;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import java.util.Optional;
import org.immutables.value.Value;

@ApiInfo(schemaId = "Metadata")
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableOgcMetadata.Builder.class)
public interface OgcMetadata {

  String SCHEMA_REF = "#/components/schemas/Metadata";

  Optional<String> getRole();

  Optional<String> getTitle();

  Optional<String> getLang();

  Optional<Object> getValue();
}
