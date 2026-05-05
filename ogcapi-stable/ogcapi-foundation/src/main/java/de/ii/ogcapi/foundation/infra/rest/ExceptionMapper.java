/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.ApiErrorMessage;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExceptionFormatExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.FormatNotSupportedException;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.VolatileUnavailableException;
import io.dropwizard.jersey.errors.LoggingExceptionMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.glassfish.jersey.message.internal.MessageBodyProviderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
@Provider
public class ExceptionMapper extends LoggingExceptionMapper<Throwable> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionMapper.class);
  public static final String CLIENT_ERROR_TEMPLATE =
      "Client error, HTTP status {}, Request path {}: {}";
  public static final String UNUSED = "unused";

  private final ExtensionRegistry extensionRegistry;

  @SuppressWarnings(UNUSED)
  @Context
  private UriInfo uriInfo;

  @SuppressWarnings(UNUSED)
  @Context
  private Request request;

  @SuppressWarnings(UNUSED)
  @Context
  private HttpHeaders httpHeaders;

  private final ContentNegotiationMediaType contentNegotiationMediaType;

  @Inject
  public ExceptionMapper(
      ExtensionRegistry extensionRegistry,
      ContentNegotiationMediaType contentNegotiationMediaType) {
    super();
    this.extensionRegistry = extensionRegistry;
    this.contentNegotiationMediaType = contentNegotiationMediaType;
  }

  @Override
  public Response toResponse(Throwable exception) {
    // content negotiation for the error response
    ImmutableSet<ApiMediaType> supportedMediaTypes =
        extensionRegistry.getExtensionsForType(ExceptionFormatExtension.class).stream()
            .map(FormatExtension::getMediaType)
            .collect(ImmutableSet.toImmutableSet());
    Optional<ApiMediaType> mediaType =
        contentNegotiationMediaType.negotiateMediaType(
            request, httpHeaders, uriInfo, supportedMediaTypes);
    ExceptionFormatExtension exceptionFormat =
        mediaType
            .flatMap(
                apiMediaType ->
                    extensionRegistry.getExtensionsForType(ExceptionFormatExtension.class).stream()
                        .filter(format -> apiMediaType == format.getMediaType())
                        .findFirst())
            .orElseGet(
                () ->
                    extensionRegistry.getExtensionsForType(ExceptionFormatExtension.class).get(0));

    String msg = getMessage(exception);
    String msgCause = getMessageOfCause(exception);

    if (exception instanceof FormatNotSupportedException) {
      return processException((FormatNotSupportedException) exception, exceptionFormat, msg);
    } else if (exception instanceof InternalServerErrorException
        && Objects.requireNonNullElse(exception.getCause(), exception)
            instanceof MessageBodyProviderNotFoundException) {
      // this catches exceptions when the API catalog is requested and the requested format cannot
      // be written
      return processException((InternalServerErrorException) exception, exceptionFormat, msgCause);
    } else if (exception instanceof WebApplicationException) {
      return processException((WebApplicationException) exception, exceptionFormat, msg);
    } else if (exception instanceof IllegalArgumentException) {
      return processException(
          exceptionFormat, msgCause.isEmpty() ? msg : String.format("%s: %s", msg, msgCause));
    } else if (exception instanceof VolatileUnavailableException) {
      return serviceUnavailable(exceptionFormat, null);
    }
    return serverError(exception, exceptionFormat);
  }

  private String getMessage(Throwable exception) {
    return Objects.requireNonNullElse(
        exception.getMessage(),
        String.format(
            "%s at %s",
            exception.getClass().getSimpleName(), exception.getStackTrace()[0].toString()));
  }

  private String getMessageOfCause(Throwable exception) {
    return Objects.nonNull(exception.getCause())
        ? Objects.nonNull(exception.getCause().getMessage())
            ? exception.getCause().getMessage()
            : String.format(
                "%s at %s",
                exception.getCause().getClass().getSimpleName(),
                exception.getCause().getStackTrace()[0].toString())
        : "";
  }

  private Response serverError(Throwable exception, ExceptionFormatExtension exceptionFormat) {
    long id = logException(exception);
    final Response.Status responseStatus = Response.Status.INTERNAL_SERVER_ERROR;
    return Response.serverError()
        .type(exceptionFormat.getMediaType().type())
        .entity(
            exceptionFormat.getExceptionEntity(
                new ApiErrorMessage(
                    responseStatus.getStatusCode(),
                    responseStatus.getReasonPhrase(),
                    String.format(
                        "There was an error processing your request, it has been logged. Error ID: %016x",
                        id))))
        .build();
  }

  // TODO: detail message
  // TODO: maybe add a Retry-After HTTP header?
  private Response serviceUnavailable(ExceptionFormatExtension exceptionFormat, String message) {
    final Response.Status responseStatus = Status.SERVICE_UNAVAILABLE;
    final String msg =
        Objects.requireNonNullElse(
            message, "The requested resource is currently not available. Please try again later.");
    return Response.status(responseStatus)
        .type(exceptionFormat.getMediaType().type())
        .entity(
            exceptionFormat.getExceptionEntity(
                new ApiErrorMessage(
                    responseStatus.getStatusCode(), responseStatus.getReasonPhrase(), msg)))
        .build();
  }

  private Response processException(
      FormatNotSupportedException exception, ExceptionFormatExtension exceptionFormat, String msg) {
    final Response.Status responseStatus = Response.Status.NOT_ACCEPTABLE;
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          CLIENT_ERROR_TEMPLATE,
          responseStatus.getStatusCode(),
          getRequestPath(true),
          exception.getMessage());
    }
    return Response.status(responseStatus)
        .type(exceptionFormat.getMediaType().type())
        .entity(
            exceptionFormat.getExceptionEntity(
                new ApiErrorMessage(
                    responseStatus.getStatusCode(), responseStatus.getReasonPhrase(), msg)))
        .build();
  }

  private Response processException(
      InternalServerErrorException exception,
      ExceptionFormatExtension exceptionFormat,
      String msg) {
    final Response.Status responseStatus = Response.Status.NOT_ACCEPTABLE;
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          CLIENT_ERROR_TEMPLATE,
          responseStatus.getStatusCode(),
          getRequestPath(true),
          exception.getCause().getMessage());
    }
    return Response.status(responseStatus)
        .type(exceptionFormat.getMediaType().type())
        .entity(
            exceptionFormat.getExceptionEntity(
                new ApiErrorMessage(
                    responseStatus.getStatusCode(), responseStatus.getReasonPhrase(), msg)))
        .build();
  }

  private Response processException(
      WebApplicationException exception, ExceptionFormatExtension exceptionFormat, String msg) {
    final Response response = exception.getResponse();
    Response.Status.Family family = response.getStatusInfo().getFamily();
    if (Response.Status.Family.REDIRECTION.equals(family)) {
      return response;
    } else if (Response.Status.Family.CLIENT_ERROR.equals(family)) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(
            CLIENT_ERROR_TEMPLATE,
            response.getStatus(),
            getRequestPath(true),
            exception.getMessage());
      }
      return Response.fromResponse(response)
          .type(exceptionFormat.getMediaType().type())
          .entity(
              exceptionFormat.getExceptionEntity(
                  new ApiErrorMessage(
                      response.getStatus(), response.getStatusInfo().getReasonPhrase(), msg)))
          .build();
    } else if (exception instanceof ServiceUnavailableException) {
      return serviceUnavailable(exceptionFormat, exception.getMessage());
    }

    // family.equals(Response.Status.Family.SERVER_ERROR)
    long id =
        logException(Objects.nonNull(exception.getCause()) ? exception.getCause() : exception);
    return Response.serverError()
        .type(exceptionFormat.getMediaType().type())
        .entity(
            exceptionFormat.getExceptionEntity(
                new ApiErrorMessage(
                    response.getStatus(),
                    response.getStatusInfo().getReasonPhrase(),
                    String.format(
                        "There was an error processing your request, it has been logged. Error ID: %016x",
                        id))))
        .build();
  }

  private Response processException(ExceptionFormatExtension exceptionFormat, String msg) {
    final Response.Status responseStatus = Response.Status.BAD_REQUEST;
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          CLIENT_ERROR_TEMPLATE, responseStatus.getStatusCode(), getRequestPath(true), msg);
    }
    return Response.status(responseStatus)
        .type(exceptionFormat.getMediaType().type())
        .entity(
            exceptionFormat.getExceptionEntity(
                new ApiErrorMessage(
                    responseStatus.getStatusCode(), responseStatus.getReasonPhrase(), msg)))
        .build();
  }

  @Override
  protected long logException(Throwable exception) {
    final long id = ThreadLocalRandom.current().nextLong();
    String message = exception.getMessage();
    if (Objects.isNull(message)) {
      message =
          exception.getClass().getSimpleName() + " at " + exception.getStackTrace()[0].toString();
    }
    if (LOGGER.isErrorEnabled()) {
      LOGGER.error(String.format("Server Error with ID %016x: {}", id), message);
    }
    if (LOGGER.isDebugEnabled(LogContext.MARKER.STACKTRACE)) {
      LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace:", exception);
    }
    return id;
  }

  private String getRequestPath(@SuppressWarnings("SameParameterValue") boolean queryParameters) {
    String s =
        queryParameters ? uriInfo.getRequestUri().toString() : uriInfo.getAbsolutePath().toString();
    return s.substring(s.indexOf('/', s.indexOf("//") + 1));
  }
}
