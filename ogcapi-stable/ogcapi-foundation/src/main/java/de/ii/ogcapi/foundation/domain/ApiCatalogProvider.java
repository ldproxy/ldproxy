/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import static de.ii.ogcapi.foundation.domain.AbstractRequestContext.STATIC_PATH;
import static de.ii.ogcapi.foundation.domain.ApiRequestContext.PATH_SPLITTER;

import com.google.common.base.Splitter;
import de.ii.ogcapi.foundation.domain.ImmutableApiCatalog.Builder;
import de.ii.xtraplatform.entities.domain.EntityDataBuilder;
import de.ii.xtraplatform.entities.domain.EntityDataDefaultsStore;
import de.ii.xtraplatform.services.domain.Service;
import de.ii.xtraplatform.services.domain.ServiceData;
import de.ii.xtraplatform.services.domain.ServiceListingProvider;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.values.domain.Identifier;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.apache.hc.core5.http.NameValuePair;

public abstract class ApiCatalogProvider implements ServiceListingProvider, ApiExtension {

  protected final URI servicesUri;
  protected final I18n i18n;
  protected final EntityDataDefaultsStore defaultsStore;
  protected final ExtensionRegistry extensionRegistry;

  public ApiCatalogProvider(
      ServicesContext servicesContext,
      I18n i18n,
      EntityDataDefaultsStore defaultsStore,
      ExtensionRegistry extensionRegistry) {
    this.servicesUri = servicesContext.getUri();
    this.i18n = i18n;
    this.defaultsStore = defaultsStore;
    this.extensionRegistry = extensionRegistry;
  }

  @Override
  public Response getServiceListing(List<ServiceData> apis, URI uri, Optional<Principal> user) {
    try {
      return getServiceListing(apis, uri, user, Optional.of(Locale.ENGLISH));
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not generate service overview.", e);
    }
  }

  public abstract Response getServiceListing(
      List<ServiceData> services, URI uri, Optional<Principal> user, Optional<Locale> language)
      throws URISyntaxException;

  public abstract ApiMediaType getApiMediaType();

  private void customizeUri(final URICustomizer uriCustomizer) {
    uriCustomizer.setScheme(servicesUri.getScheme());
    uriCustomizer.prependPathSegments(PATH_SPLITTER.splitToList(servicesUri.getPath()));
    uriCustomizer.ensureNoTrailingSlash();
  }

  private String getStaticUrlPrefix(final URICustomizer uriCustomizer) {
    return uriCustomizer
        .copy()
        .setPathSegments(PATH_SPLITTER.splitToList(servicesUri.getPath()))
        .ensureLastPathSegment(STATIC_PATH)
        .ensureNoTrailingSlash()
        .getPath();
  }

  private FoundationConfiguration getFoundationConfigurationDefaults() {
    EntityDataBuilder<?> builder =
        defaultsStore.getBuilder(
            Identifier.from(
                EntityDataDefaultsStore.EVENT_TYPE,
                Service.TYPE,
                OgcApiDataV2.SERVICE_TYPE.toLowerCase(Locale.ROOT)));
    if (builder instanceof OgcApiDataV2.Builder) {
      OgcApiDataV2 defaults =
          ((OgcApiDataV2.Builder) builder.fillRequiredFieldsWithPlaceholders()).build();
      return defaults
          .getExtension(FoundationConfiguration.class)
          .orElse(new ImmutableFoundationConfiguration.Builder().build());
    }
    return new ImmutableFoundationConfiguration.Builder().build();
  }

  protected URI getApiUrl(URI uri, String apiId, Optional<Integer> apiVersion)
      throws URISyntaxException {
    return apiVersion.isPresent()
        ? new URICustomizer(uri)
            .clearParameters()
            .ensureLastPathSegments(apiId, "v" + apiVersion.get())
            .ensureNoTrailingSlash()
            .build()
        : new URICustomizer(uri)
            .clearParameters()
            .ensureLastPathSegment(apiId)
            .ensureNoTrailingSlash()
            .build();
  }

  protected URI getApiUrl(URI uri, List<String> subPathToLandingPage) throws URISyntaxException {
    return new URICustomizer(uri)
        .clearParameters()
        .ensureLastPathSegments(subPathToLandingPage.toArray(new String[0]))
        .ensureNoTrailingSlash()
        .build();
  }

  protected ApiCatalog getCatalog(List<ServiceData> services, URI uri, Optional<Locale> language)
      throws URISyntaxException {
    final DefaultLinksGenerator linksGenerator = new DefaultLinksGenerator();
    URICustomizer uriCustomizer = new URICustomizer(uri);
    String urlPrefix = getStaticUrlPrefix(uriCustomizer);
    List<String> tags =
        Splitter.on(',')
            .omitEmptyStrings()
            .trimResults()
            .splitToList(
                uriCustomizer.getQueryParams().stream()
                    .filter(param -> "tags".equals(param.getName()))
                    .map(NameValuePair::getValue)
                    .findAny()
                    .orElse(""));
    Optional<String> name =
        uriCustomizer.getQueryParams().stream()
            .filter(param -> "name".equals(param.getName()))
            .map(NameValuePair::getValue)
            .findAny();
    customizeUri(uriCustomizer);
    URI catalogUri;
    try {
      catalogUri = uriCustomizer.build();
    } catch (URISyntaxException e) {
      // ignore, use fallback
      catalogUri = uri;
    }

    ApiMediaType mediaType = getApiMediaType();
    List<ApiMediaType> alternateMediaTypes =
        extensionRegistry.getExtensionsForType(ApiCatalogProvider.class).stream()
            .map(ApiCatalogProvider::getApiMediaType)
            .filter(candidate -> !candidate.label().equals(mediaType.label()))
            .collect(Collectors.toList());

    FoundationConfiguration config = getFoundationConfigurationDefaults();

    URI finalUri = catalogUri;
    ImmutableApiCatalog.Builder builder =
        getCatalogBuilder(
            services,
            language,
            linksGenerator,
            uriCustomizer,
            urlPrefix,
            tags,
            name,
            mediaType,
            alternateMediaTypes,
            config,
            finalUri);

    if (Objects.nonNull(config.getGoogleSiteVerification())) {
      builder.googleSiteVerification(config.getGoogleSiteVerification());
    }

    for (ApiCatalogExtension extension :
        extensionRegistry.getExtensionsForType(ApiCatalogExtension.class)) {
      builder =
          extension.process(
              builder, uriCustomizer.copy(), mediaType, alternateMediaTypes, language);
    }

    getFoundationConfigurationDefaults().getApiCatalogAdditionalEntries().forEach(builder::addApis);

    return builder.build();
  }

  private Builder getCatalogBuilder(
      List<ServiceData> services,
      Optional<Locale> language,
      DefaultLinksGenerator linksGenerator,
      URICustomizer uriCustomizer,
      String urlPrefix,
      List<String> tags,
      Optional<String> name,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      FoundationConfiguration config,
      URI finalUri)
      throws URISyntaxException {
    return new Builder()
        .title(
            Objects.requireNonNullElse(
                config.getApiCatalogLabel(), i18n.get("rootTitle", language)))
        .description(
            Objects.requireNonNullElse(
                config.getApiCatalogDescription(), i18n.get("rootDescription", language)))
        .catalogUri(new URICustomizer(finalUri).clearParameters().ensureNoTrailingSlash().build())
        .urlPrefix(urlPrefix)
        .links(
            linksGenerator.generateLinks(
                uriCustomizer, mediaType, alternateMediaTypes, i18n, language))
        .apis(
            services.stream()
                .sorted(Comparator.comparing(ServiceData::getLabel))
                .filter(
                    api ->
                        name.isEmpty()
                            || api.getLabel()
                                .toLowerCase(language.orElse(Locale.ENGLISH))
                                .contains(name.get().toLowerCase(language.orElse(Locale.ENGLISH))))
                .filter(
                    api ->
                        tags.isEmpty()
                            || api instanceof OgcApiDataV2
                                && ((OgcApiDataV2) api).getTags().stream().anyMatch(tags::contains))
                .map(
                    api -> {
                      try {
                        if (api instanceof OgcApiDataV2) {
                          return new ImmutableApiCatalogEntry.Builder()
                              .id(api.getId())
                              .title(api.getLabel())
                              .description(api.getDescription())
                              .landingPageUri(getApiUrl(finalUri, api.getSubPath()))
                              .tags(((OgcApiDataV2) api).getTags())
                              .isDataset(((OgcApiDataV2) api).isDataset())
                              .build();
                        }
                        return new ImmutableApiCatalogEntry.Builder()
                            .id(api.getId())
                            .title(api.getLabel())
                            .description(api.getDescription())
                            .landingPageUri(getApiUrl(finalUri, api.getId(), api.getApiVersion()))
                            .build();
                      } catch (URISyntaxException e) {
                        throw new IllegalStateException(
                            String.format(
                                "Could not create landing page URI for API '%s'.", api.getId()),
                            e);
                      }
                    })
                .collect(Collectors.toList()));
  }
}
