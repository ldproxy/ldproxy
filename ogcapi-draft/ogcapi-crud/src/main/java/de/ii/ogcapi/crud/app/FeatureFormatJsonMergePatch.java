/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.Map;
import javax.ws.rs.core.MediaType;

/**
 * This is not implemented as a FeatureFormat since it is only relevant for PATCH requests and
 * FeatureFormats are not method specific.
 */
public interface FeatureFormatJsonMergePatch {

  ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "merge-patch+json"))
          .label("JSON Merge Patch")
          .build();

  ApiMediaTypeContent MEDIA_TYPE_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new ObjectSchema())
          .schemaRef("#/components/schemas/anyObject")
          .ogcApiMediaType(MEDIA_TYPE)
          .build();

  default void addTo(Map<ApiMediaType, ApiMediaTypeContent> mediaTypes) {
    mediaTypes.put(MEDIA_TYPE, MEDIA_TYPE_CONTENT);
  }
}
