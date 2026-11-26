/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;

@AutoMultiBind
public interface ExceptionFormatExtension extends FormatExtension {

  @Override
  default boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return true;
  }

  Object getExceptionEntity(ApiErrorMessage errorMessage);

  /**
   * @param errorMessage the error
   * @param apiPath the external path of the API addressed by the request, or of the web
   *     application, if the request does not address an API; e.g. to reference static resources
   * @return the representation of the error
   */
  default Object getExceptionEntity(ApiErrorMessage errorMessage, String apiPath) {
    return getExceptionEntity(errorMessage);
  }
}
