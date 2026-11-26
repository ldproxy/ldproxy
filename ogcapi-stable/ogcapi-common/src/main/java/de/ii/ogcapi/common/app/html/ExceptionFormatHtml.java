/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.app.html;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiErrorMessage;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExceptionFormatExtension;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.xtraplatform.base.domain.AppContext;
import io.dropwizard.jersey.errors.ErrorEntityWriter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

@Singleton
@AutoBind(
    interfaces = {
      ExceptionFormatExtension.class,
      FormatExtension.class,
      ApiExtension.class
    }) // TODO: workaround for issue in dagger-auto
public class ExceptionFormatHtml extends ErrorEntityWriter<ApiErrorMessage, OgcApiErrorView>
    implements ExceptionFormatExtension {

  private final AppContext appContext;

  @Inject
  public ExceptionFormatHtml(AppContext appContext) {
    super(MediaType.TEXT_HTML_TYPE, OgcApiErrorView.class);
    this.appContext = appContext;
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
  public Object getExceptionEntity(ApiErrorMessage errorMessage) {
    return getRepresentation(errorMessage);
  }

  @Override
  public Object getExceptionEntity(ApiErrorMessage errorMessage, String apiPath) {
    return new OgcApiErrorView(errorMessage, apiPath);
  }

  // without a request, the static resources are referenced below the root of the web application
  @Override
  protected OgcApiErrorView getRepresentation(ApiErrorMessage errorMessage) {
    String rootPath =
        appContext.getPathPrefix().isEmpty()
            ? ""
            : "/" + String.join("/", appContext.getPathPrefix());
    return new OgcApiErrorView(errorMessage, rootPath);
  }
}
