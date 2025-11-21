/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.app;

import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.tiles3d.domain.Format3dTilesContent;
import de.ii.ogcapi.tiles3d.domain.Tiles3dConfiguration;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Schema;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

// @AutoBind
// @Singleton
public class Format3dTilesContentGltfBinary implements Format3dTilesContent {

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("model", "gltf-binary"))
          .label("glTF-Binary")
          .parameter("glb")
          .build();
  private static final Schema<?> SCHEMA = new BinarySchema();
  private static final String SCHEMA_REF = "#/components/schemas/glTF";

  @Inject
  public Format3dTilesContentGltfBinary() {}

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return Tiles3dConfiguration.class;
  }

  @Override
  public boolean canSupportTransactions() {
    return false;
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(SCHEMA)
        .schemaRef(SCHEMA_REF)
        .ogcApiMediaType(getMediaType())
        .build();
  }
}
