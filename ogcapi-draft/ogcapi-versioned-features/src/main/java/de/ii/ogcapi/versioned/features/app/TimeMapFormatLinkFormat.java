/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * RFC 6690 CoRE Link Format representation of the Time Map. Each link is one comma-separated entry
 * of the form {@code <uri>;rel="...";title="...";datetime="..."}; the {@code datetime} link
 * attribute (RFC 7089, RFC 1123 HTTP-date) is added to every {@code memento} entry. The Versioned
 * Features draft (Time Map Link Format requirements class) expects this media type alongside JSON.
 */
@Singleton
@AutoBind
public class TimeMapFormatLinkFormat implements TimeMapFormatExtension {

  public static final ApiMediaType LINK_FORMAT_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "link-format"))
          .label("CoRE Link Format")
          .parameter("linkformat")
          .build();

  private static final ApiMediaTypeContent CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new StringSchema().example("</...>;rel=\"self\"\n</...>;rel=\"memento\"..."))
          .schemaRef("#/components/schemas/linkFormat")
          .ogcApiMediaType(LINK_FORMAT_MEDIA_TYPE)
          .build();

  @Inject
  TimeMapFormatLinkFormat() {}

  @Override
  public ApiMediaType getMediaType() {
    return LINK_FORMAT_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return CONTENT;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    // No format-specific config; gate the format on the building block enabling the resource.
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public FeatureEncoderTimeMap getFeatureEncoder(EncodingContextTimeMap encodingContext) {
    return new FeatureEncoderTimeMapLinkFormat(encodingContext);
  }
}
