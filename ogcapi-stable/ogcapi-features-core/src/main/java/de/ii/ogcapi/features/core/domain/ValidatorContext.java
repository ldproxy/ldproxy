/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.immutables.value.Value;

/**
 * Inputs to {@link FeatureFormatExtension#validate(java.io.InputStream, ValidatorContext)}. Carries
 * format-agnostic request material: the API and collection identity, the parsed request media type,
 * the request context, the raw HTTP {@code Link} header values, and the list of default profiles
 * relevant to validation. Each format interprets these inputs as it sees fit.
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true, builder = "new")
public interface ValidatorContext {

  enum Type {
    RETURNABLES,
    RECEIVABLES
  }

  OgcApiDataV2 getApiData();

  String getCollectionId();

  MediaType getMediaType();

  @Value.Default
  default ValidatorContext.Type getType() {
    return Type.RETURNABLES;
  }

  ApiRequestContext getRequestContext();

  List<Profile> getDeclaredProfiles();

  List<Profile> getDefaultProfiles();
}
