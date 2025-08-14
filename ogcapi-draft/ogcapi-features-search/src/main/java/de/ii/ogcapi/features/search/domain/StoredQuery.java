/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.features.core.domain.SchemaProperty;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true, deepImmutablesDetection = true, builder = "new")
@JsonDeserialize(builder = ImmutableStoredQuery.Builder.class)
public abstract class StoredQuery extends PageRepresentationWithId {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<StoredQuery> FUNNEL =
      (from, into) -> {
        PageRepresentationWithId.FUNNEL.funnel(from, into);
        from.getParameters()
            .forEach(
                (name, schema) -> {
                  into.putString(name, StandardCharsets.UTF_8);
                  into.putString(schema.toString(), StandardCharsets.UTF_8);
                });
      };

  public abstract Map<String, JsonNode> getParameters();

  @JsonIgnore
  public abstract List<Entry<String, String>> getFormats();

  @JsonIgnore
  @Value.Derived
  public List<SchemaProperty> getParameterList() {
    ImmutableList.Builder<SchemaProperty> builder = ImmutableList.builder();
    getParameters().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEachOrdered(
            entry ->
                builder.add(
                    SchemaProperty.of(
                        entry.getKey(),
                        (ObjectNode) entry.getValue(),
                        SchemaType.STORED_QUERY_PARAMETER)));
    return builder.build();
  }

  @JsonIgnore
  @Value.Derived
  public String getObjectId() {
    return getId();
  }

  @JsonIgnore
  @Value.Derived
  public boolean hasParameters() {
    return !getParameters().isEmpty();
  }

  @JsonIgnore
  @Value.Derived
  public boolean hasParametersWithoutDefault() {
    return getParameterList().stream().anyMatch(param -> param.getDefaultValue().isEmpty());
  }
}
