/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(as = ImmutableStyleLayer.class)
public abstract class StyleLayer {

  public abstract String getId();

  public abstract Optional<String> getDescription();

  public abstract Optional<String> getType();

  public abstract Map<String, JsonSchema> getAttributes();

  public abstract Optional<Link> getSampleData();

  @JsonIgnore
  @Value.Lazy
  public Optional<String> getAttributeList() {
    return getAttributes().isEmpty()
        ? Optional.empty()
        : Optional.of(
            String.join(
                ", ",
                getAttributes().keySet().stream()
                    .sorted()
                    .collect(Collectors.toUnmodifiableList())));
  }

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<StyleLayer> FUNNEL =
      (from, into) -> {
        into.putString(from.getId(), StandardCharsets.UTF_8);
        from.getDescription().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getType().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getAttributes().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> JsonSchema.FUNNEL.funnel(entry.getValue(), into));
        from.getSampleData()
            .ifPresent(
                link ->
                    into.putString(link.getHref(), StandardCharsets.UTF_8)
                        .putString(
                            Objects.requireNonNullElse(link.getRel(), ""), StandardCharsets.UTF_8));
      };
}
