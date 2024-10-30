/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import java.util.Optional;

@AutoMultiBind(exclude = FormatHtml.class)
public interface FormatHtml {
  default Optional<String> homeUrl(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .flatMap(cfg -> Optional.ofNullable(cfg.getHomeUrl()));
  }
}
