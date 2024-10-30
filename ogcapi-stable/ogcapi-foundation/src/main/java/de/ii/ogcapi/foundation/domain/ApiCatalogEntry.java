/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableApiCatalogEntry.Builder.class)
public abstract class ApiCatalogEntry extends PageRepresentation {

  public abstract String getId();

  public abstract URI getLandingPageUri();

  public abstract List<String> getTags();

  @Value.Default
  public boolean isDataset() {
    return true;
  }

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();
}
