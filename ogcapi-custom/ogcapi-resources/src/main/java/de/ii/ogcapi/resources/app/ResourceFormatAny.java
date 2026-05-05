/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.resources.domain.ResourceFormatExtension;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @title Any
 */
@Singleton
@AutoBind
public class ResourceFormatAny implements ResourceFormatExtension {

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder().type(MediaType.WILDCARD_TYPE).build();
  public static final String SCHEMA_REF_RESOURCE = "#/components/schemas/Resource";

  private final Schema schemaResource;
  private final ResourceStore resourcesStore;

  @Inject
  ResourceFormatAny(ResourceStore blobStore) {
    this.schemaResource = new BinarySchema();
    this.resourcesStore = blobStore.with(ResourcesBuildingBlock.STORE_RESOURCE_TYPE);
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaResource)
        .schemaRef(SCHEMA_REF_RESOURCE)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public Object getResourceEntity(
      byte[] resource, String resourceId, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    return resource;
  }

  @Override
  public Response putResource(
      byte[] resource, String resourceId, OgcApiDataV2 apiData, ApiRequestContext requestContext) {

    try {
      resourcesStore.put(Path.of(apiData.getId(), resourceId), new ByteArrayInputStream(resource));
    } catch (IOException e) {
      throw new RuntimeException("Could not PUT resource: " + resourceId);
    }

    return Response.noContent().build();
  }
}
