/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.common.domain.OgcApiExtent;
import de.ii.ogcapi.foundation.domain.ApiInfo;
import de.ii.ogcapi.foundation.domain.PageRepresentation;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableOgcApiCollection.Builder.class)
@ApiInfo(schemaId = "Collection")
public abstract class OgcApiCollection extends PageRepresentationWithId {

  public static final String SCHEMA_REF = "#/components/schemas/Collection";

  // Core, part 1
  public abstract Optional<OgcApiExtent> getExtent();

  public abstract Optional<String> getItemType();

  public abstract List<String> getCrs();

  // CRS, part 2
  public abstract Optional<String> getStorageCrs();

  public abstract Optional<Float> getStorageCrsCoordinateEpoch();

  // restrict to information in ogcapi-stable, everything else goes into the extensions map

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();

  @JsonIgnore
  @Value.Default
  @Value.Auxiliary
  public String getDataRel() {
    return "items";
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  public Optional<String> getDefaultStyle() {
    return getExtensions().containsKey("defaultStyle")
        ? Optional.of((String) getExtensions().get("defaultStyle"))
        : Optional.empty();
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  public Optional<Long> getItemCount() {
    return getExtensions().containsKey("itemCount")
        ? Optional.of((Long) getExtensions().get("itemCount"))
        : Optional.empty();
  }

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<OgcApiCollection> FUNNEL =
      (from, into) -> {
        PageRepresentation.FUNNEL.funnel(from, into);
        from.getExtent().ifPresent(val -> OgcApiExtent.FUNNEL.funnel(val, into));
        from.getItemType().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getCrs().stream()
            .sorted()
            .forEachOrdered(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getStorageCrs().ifPresent(val -> into.putString(val, StandardCharsets.UTF_8));
        from.getStorageCrsCoordinateEpoch().ifPresent(val -> into.putFloat(val));
        from.getExtensions().keySet().stream()
            .sorted()
            .forEachOrdered(key -> into.putString(key, StandardCharsets.UTF_8));
        // we cannot encode the generic extension object
      };
}
