/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Variant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.glassfish.jersey.internal.util.collection.Ref;
import org.glassfish.jersey.internal.util.collection.Refs;
import org.glassfish.jersey.message.internal.QualitySourceMediaType;
import org.glassfish.jersey.message.internal.VariantSelector;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ContentNegotiationMediaTypeImpl implements ContentNegotiationMediaType {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ContentNegotiationMediaTypeImpl.class);
  private static final String ACCEPT_HEADER = "Accept";

  @Inject
  ContentNegotiationMediaTypeImpl() {}

  @Override
  public Optional<ApiMediaType> negotiateMediaType(
      ContainerRequestContext requestContext, Set<ApiMediaType> supportedMediaTypes) {

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("accept {}", requestContext.getHeaderString(ACCEPT_HEADER));
    }

    Optional<ApiMediaType> ogcApiMediaType =
        negotiateMediaType(supportedMediaTypes, requestContext.getRequest());

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("content-type {}", ogcApiMediaType);
    }

    return ogcApiMediaType;
  }

  @Override
  public Optional<ApiMediaType> negotiateMediaType(
      Request request,
      HttpHeaders httpHeaders,
      UriInfo uriInfo,
      Set<ApiMediaType> supportedMediaTypes) {

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("accept {}", httpHeaders.getHeaderString(ACCEPT_HEADER));
    }

    Optional<ApiMediaType> ogcApiMediaType = negotiateMediaType(supportedMediaTypes, request);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("content-type {}", ogcApiMediaType);
    }

    return ogcApiMediaType;
  }

  private Optional<ApiMediaType> negotiateMediaType(
      Set<ApiMediaType> supportedMediaTypes, Request request) {
    MediaType[] supportedMediaTypesArray =
        supportedMediaTypes.stream().flatMap(this::toTypes).distinct().toArray(MediaType[]::new);

    MediaType selected = null;
    try {
      if (supportedMediaTypesArray.length > 0) {
        final List<Variant> variants = Variant.mediaTypes(supportedMediaTypesArray).build();
        if (request instanceof ContainerRequest) {
          final List<MediaType> acceptableMediaTypes =
              ((ContainerRequest) request).getAcceptableMediaTypes();
          final Ref<String> varyValueRef = Refs.emptyRef();
          List<Variant> compatibleVariants =
              VariantSelector.selectVariants((ContainerRequest) request, variants, varyValueRef);
          selected =
              ApiMediaType.negotiateMediaType(
                  acceptableMediaTypes,
                  compatibleVariants.stream()
                      .map(Variant::getMediaType)
                      .collect(Collectors.toUnmodifiableList()));
        } else {
          Variant variant = request.selectVariant(variants);
          if (Objects.nonNull(variant)) {
            selected = variant.getMediaType();
          }
        }
      }
    } catch (Exception ex) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Could not parse request headers during content negotiation. Selecting any media type. Reason: {}",
            ex.getMessage());
      }
      return supportedMediaTypes.stream().findAny();
    }

    return findMatchingOgcApiMediaType(selected, supportedMediaTypes);
  }

  private Optional<ApiMediaType> findMatchingOgcApiMediaType(
      MediaType mediaType, Set<ApiMediaType> supportedMediaTypes) {
    return supportedMediaTypes.stream().filter(type -> type.type().equals(mediaType)).findFirst();
  }

  private Stream<MediaType> toTypes(ApiMediaType apiMediaType) {
    return Stream.of(apiMediaType.type())
        .map(
            mediaType ->
                apiMediaType.qs() < 1000
                    ? new QualitySourceMediaType(
                        mediaType.getType(),
                        mediaType.getSubtype(),
                        apiMediaType.qs(),
                        mediaType.getParameters())
                    : mediaType);
  }
}
