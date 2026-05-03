/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.pubsub.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.pubsub.domain.AsyncApiDefinitionFormatExtension;
import de.ii.ogcapi.pubsub.domain.asyncapi.AsyncApi;
import io.swagger.v3.oas.models.media.ObjectSchema;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class AsyncApiJson implements AsyncApiDefinitionFormatExtension {

  private static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(
              new MediaType(
                  "application", "vnd.aai.asyncapi+json", ImmutableMap.of("version", "2.6.0")))
          .label("JSON")
          .parameter("json")
          .build();

  @Inject
  public AsyncApiJson() {}

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  // always active, if PubSub is active, since a service-desc link relation is mandatory
  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return PubSubConfiguration.class;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(new ObjectSchema())
        .schemaRef("#/components/schemas/objectSchema")
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public Object getAsyncApiEntity(AsyncApi asyncApi, ApiRequestContext apiRequestContext) {
    return asyncApi;
  }

  @Override
  public Optional<String> getRel() {
    return Optional.of("service-desc");
  }
}
