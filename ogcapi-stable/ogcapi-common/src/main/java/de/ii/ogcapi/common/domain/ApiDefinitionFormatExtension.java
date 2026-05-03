/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import java.util.Optional;
import jakarta.ws.rs.core.Response;

@AutoMultiBind
public interface ApiDefinitionFormatExtension extends FormatExtension {

  Response getResponse(OgcApiDataV2 apiData, ApiRequestContext apiRequestContext);

  default Optional<String> getRel() {
    return Optional.empty();
  }
}
