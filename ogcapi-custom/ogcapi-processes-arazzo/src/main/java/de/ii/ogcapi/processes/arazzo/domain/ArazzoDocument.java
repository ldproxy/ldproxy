/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * An Arazzo description (versions 1.0 and 1.1) with the members of the subset that is supported.
 * Stored in {@code values/workflows/{apiId}}, each of its workflows is a process of the API; as the
 * input of the process {@code execute-workflow}, it is executed ad hoc. Unknown members, e.g.
 * specification extensions, are ignored; members that are known, but not supported, are rejected
 * when the description is checked.
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
@FromValueStore(type = "workflows")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = ImmutableArazzoDocument.Builder.class)
public interface ArazzoDocument extends StoredValue {

  String getArazzo();

  @JsonProperty("$self")
  Optional<String> getSelf();

  ArazzoInfo getInfo();

  List<ArazzoSource> getSourceDescriptions();

  List<ArazzoWorkflow> getWorkflows();

  Optional<ArazzoComponents> getComponents();

  abstract class Builder implements ValueBuilder<ArazzoDocument> {}
}
