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
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import io.swagger.v3.oas.models.media.ObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * JSON representation of the Time Map. Encodes the version intervals as {@code memento} link
 * entries with their RFC 7089 {@code datetime} attribute (RFC 1123 HTTP-date), plus {@code self} /
 * {@code original} / {@code latest-version} entries.
 */
@Singleton
@AutoBind
public class TimeMapFormatJson implements TimeMapFormatExtension {

  private static final ApiMediaTypeContent CONTENT =
      new ImmutableApiMediaTypeContent.Builder()
          .schema(new ObjectSchema())
          .schemaRef("#/components/schemas/TimeMap")
          .ogcApiMediaType(ApiMediaType.JSON_MEDIA_TYPE)
          .build();

  @Inject
  TimeMapFormatJson() {}

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    // Gate on Versioned Features being enabled rather than the (sometimes absent) JsonConfiguration
    // — the format is logically a Time Map representation, not a generic JSON encoder.
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return CONTENT;
  }

  @Override
  public FeatureEncoderTimeMap getFeatureEncoder(EncodingContextTimeMap encodingContext) {
    return new FeatureEncoderTimeMapJson(encodingContext);
  }
}
