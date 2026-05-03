/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.RequestInjectableContext;
import de.ii.xtraplatform.services.domain.ServiceInjectableContext;
import java.util.function.Supplier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.internal.inject.Binder;
import org.glassfish.jersey.process.internal.RequestScoped;

@Singleton
@AutoBind
@Provider
public class RequestContextBinder extends AbstractBinder
    implements Binder, RequestInjectableContext {

  public static final String OGCAPI_REQUEST_CONTEXT_KEY = "OGCAPI_REQUEST";

  @Inject
  public RequestContextBinder() {
    super();
  }

  @Override
  public void inject(
      ContainerRequestContext containerRequestContext, ApiRequestContext apiRequestContext) {
    containerRequestContext.setProperty(OGCAPI_REQUEST_CONTEXT_KEY, apiRequestContext);
  }

  @Override
  protected void configure() {
    bindFactory(OgcApiRequestFactory.class)
        .proxy(true)
        .proxyForSameScope(false)
        .to(ApiRequestContext.class)
        .in(RequestScoped.class);

    bindFactory(OgcApiDatasetFactory.class)
        .proxy(true)
        .proxyForSameScope(false)
        .to(OgcApi.class)
        .in(RequestScoped.class);
  }

  public static class OgcApiDatasetFactory implements Supplier<OgcApi> {

    private final ContainerRequestContext containerRequestContext;

    @Inject
    public OgcApiDatasetFactory(ContainerRequestContext containerRequestContext) {
      this.containerRequestContext = containerRequestContext;
    }

    @Override
    public OgcApi get() {
      return (OgcApi)
          containerRequestContext.getProperty(ServiceInjectableContext.SERVICE_CONTEXT_KEY);
    }
  }

  public static class OgcApiRequestFactory implements Supplier<ApiRequestContext> {

    private final ContainerRequestContext containerRequestContext;

    @Inject
    public OgcApiRequestFactory(ContainerRequestContext containerRequestContext) {
      this.containerRequestContext = containerRequestContext;
    }

    @Override
    public ApiRequestContext get() {
      return (ApiRequestContext) containerRequestContext.getProperty(OGCAPI_REQUEST_CONTEXT_KEY);
    }
  }
}
