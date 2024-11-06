/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(as = ImmutableCodelistEntry.class)
public abstract class CodelistEntry extends PageRepresentationWithId {

  public static final String SCHEMA_REF = "#/components/schemas/CodelistEntry";

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  public String label() {
    return getTitle().orElse(getId());
  }

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  public String uri() {
    return getLinks().stream()
        .filter(link -> "self".equals(link.getRel()))
        .findFirst()
        .map(Link::getHref)
        .orElse(null);
  }

  @JsonIgnore
  public abstract boolean isUsedInNonEmptyCollection();

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<CodelistEntry> FUNNEL = PageRepresentationWithId.FUNNEL::funnel;
}
