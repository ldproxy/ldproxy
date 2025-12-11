/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import io.swagger.v3.oas.models.media.Schema;
import javax.ws.rs.core.MediaType;

public class FormatAny implements FormatExtension {
  public static final FormatAny INSTANCE = new FormatAny();

  ApiMediaType ANY =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.WILDCARD_TYPE)
          .label("Any")
          .parameter("any")
          .build();

  ApiMediaTypeContent ANY_CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new Schema<>())
          .schemaRef("#/components/schemas/any")
          .ogcApiMediaType(ANY)
          .build();

  @Override
  public ApiMediaType getMediaType() {
    return ANY;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return ANY_CONTENT;
  }
}
