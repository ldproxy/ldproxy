/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableApiCatalog.Builder.class)
public abstract class ApiCatalog extends PageRepresentation {

  public abstract URI getCatalogUri();

  public abstract List<ApiCatalogEntry> getApis();

  @JsonIgnore
  public abstract String basePath();

  @JsonIgnore
  public abstract Optional<String> getGoogleSiteVerification();

  @JsonAnyGetter
  public abstract Map<String, Object> getExtensions();
}
