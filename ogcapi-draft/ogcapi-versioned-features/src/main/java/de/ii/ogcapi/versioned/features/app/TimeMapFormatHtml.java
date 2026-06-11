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
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.versioned.features.domain.EncodingContextTimeMap;
import de.ii.ogcapi.versioned.features.domain.FeatureEncoderTimeMap;
import de.ii.ogcapi.versioned.features.domain.TimeMapFormatExtension;
import de.ii.xtraplatform.web.domain.MustacheRenderer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * HTML representation of the Time Map. Reuses the standard {@code header}/{@code footer} so the
 * page integrates with the existing breadcrumb / format-link bar above, then lists each version as
 * a row with its start timestamp linking back to {@code items/{id}?datetime=<start>}.
 */
@Singleton
@AutoBind
public class TimeMapFormatHtml implements TimeMapFormatExtension, FormatHtml {

  private final MustacheRenderer mustacheRenderer;

  @Inject
  TimeMapFormatHtml(MustacheRenderer mustacheRenderer) {
    this.mustacheRenderer = mustacheRenderer;
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.HTML_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return FormatExtension.HTML_CONTENT;
  }

  @Override
  public FeatureEncoderTimeMap getFeatureEncoder(EncodingContextTimeMap encodingContext) {
    return new FeatureEncoderTimeMapHtml(
        encodingContext, mustacheRenderer, homeUrl(encodingContext.getApi().getData()));
  }
}
