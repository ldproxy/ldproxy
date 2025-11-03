/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.CollectionMetadata;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import java.text.MessageFormat;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoMultiBind
public interface QueriesHandler<T extends QueryIdentifier> {

  Logger LOGGER = LoggerFactory.getLogger(QueriesHandler.class);

  Locale[] LANGUAGES =
      I18n.getLanguages().stream().collect(Collectors.toUnmodifiableList()).toArray(Locale[]::new);
  String[] ENCODINGS = {"gzip", "identity"};

  static void ensureCollectionIdExists(OgcApiDataV2 apiData, String collectionId) {
    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }
  }

  static void ensureFeatureProviderSupportsQueries(FeatureProvider featureProvider) {
    if (!featureProvider.queries().isSupported()) {
      throw new IllegalStateException("Feature provider does not support queries.");
    }
  }

  Map<T, QueryHandler<? extends QueryInput>> getQueryHandlers();

  default boolean canHandle(T queryIdentifier, QueryInput queryInput) {
    return true;
  }

  default Response handle(
      T queryIdentifier, QueryInput queryInput, ApiRequestContext requestContext) {

    QueryHandler<? extends QueryInput> queryHandler = getQueryHandlers().get(queryIdentifier);

    if (Objects.isNull(queryHandler)) {
      throw new IllegalStateException("No query handler found for " + queryIdentifier + ".");
    }

    if (!queryHandler.isValidInput(queryInput)) {
      throw new IllegalStateException(
          MessageFormat.format(
              "Invalid query handler {0} for query input of class {1}.",
              queryHandler.getClass().getSimpleName(), queryInput.getClass().getSimpleName()));
    }

    return queryHandler.handle(queryInput, requestContext);
  }

  default Response.ResponseBuilder evaluatePreconditions(
      ApiRequestContext requestContext, Date lastModified, EntityTag etag) {

    if (requestContext.getRequest().isPresent()) {
      Request request = requestContext.getRequest().get();
      try {
        if (Objects.nonNull(lastModified) && Objects.nonNull(etag)) {
          return request.evaluatePreconditions(lastModified, etag);
        } else if (Objects.nonNull(etag)) {
          return request.evaluatePreconditions(etag);
        } else if (Objects.nonNull(lastModified)) {
          return request.evaluatePreconditions(lastModified);
        } else {
          return request.evaluatePreconditions();
        }
      } catch (Exception e) {
        // could not parse headers, so silently ignore them and return the regular response
        LOGGER.debug("Ignoring invalid conditional request headers: {}", e.getMessage());
      }
    }

    return null;
  }

  default Response.ResponseBuilder prepareSuccessResponse(ApiRequestContext requestContext) {
    Response.ResponseBuilder response = Response.ok().type(requestContext.getMediaType().type());

    requestContext.getLanguage().ifPresent(response::language);

    return response;
  }

  default Response.ResponseBuilder prepareSuccessResponse(
      ApiRequestContext requestContext,
      List<Link> links,
      HeaderCaching cacheInfo,
      EpsgCrs crs,
      HeaderContentDisposition dispositionInfo) {
    return prepareSuccessResponse(requestContext, links, cacheInfo, crs, dispositionInfo, null);
  }

  default Response.ResponseBuilder prepareSuccessResponse(
      ApiRequestContext requestContext,
      List<Link> links,
      HeaderCaching cacheInfo,
      EpsgCrs crs,
      HeaderContentDisposition dispositionInfo,
      CollectionMetadata collectionMetadata) {
    Response.ResponseBuilder response = Response.ok().type(requestContext.getMediaType().type());

    cacheInfo.getLastModified().ifPresent(response::lastModified);
    cacheInfo.getEtag().ifPresent(response::tag);
    cacheInfo
        .cacheControl()
        .ifPresent(cacheControl -> response.cacheControl(CacheControl.valueOf(cacheControl)));
    cacheInfo.expires().ifPresent(response::expires);

    response.variants(
        Variant.mediaTypes(
                new ImmutableList.Builder<ApiMediaType>()
                        .add(requestContext.getMediaType())
                        .addAll(requestContext.getAlternateMediaTypes())
                        .build()
                        .stream()
                        .map(ApiMediaType::type)
                        .toArray(MediaType[]::new))
            .languages(LANGUAGES)
            .encodings(ENCODINGS)
            .add()
            .build());

    requestContext.getLanguage().ifPresent(response::language);

    if (Objects.nonNull(links)) {
      // skip URI templates in the Link header as these are not RFC 8288 links
      List<javax.ws.rs.core.Link> headerLinks =
          links.stream()
              .filter(link -> link.getTemplated() == null || !link.getTemplated())
              .sorted(Link.COMPARATOR_LINKS)
              .map(Link::getLink)
              .collect(Collectors.toUnmodifiableList());

      // Instead use a Link-Template header for templaes
      List<String> headerLinkTemplates = getLinkTemplates(links);

      // only add links and link templates that fit into the limit
      applyLinks(
          response,
          requestContext.getMaxResponseLinkHeaderSize(),
          headerLinks,
          headerLinkTemplates);
    }

    if (Objects.nonNull(collectionMetadata)) {
      collectionMetadata
          .getNumberReturned()
          .ifPresent(n -> response.header("OGC-NumberReturned", n));
      collectionMetadata.getNumberMatched().ifPresent(n -> response.header("OGC-NumberMatched", n));
    }

    if (Objects.nonNull(crs)) {
      response.header("Content-Crs", "<" + crs.toUriString() + ">");
    }

    if (Objects.nonNull(dispositionInfo)) {
      response.header(
          "Content-Disposition",
          (dispositionInfo.getAttachment() ? "attachment" : "inline")
              + dispositionInfo
                  .getFilename()
                  .map(filename -> "; filename=\"" + filename + "\"")
                  .orElse(""));
    }

    return response;
  }

  Set<String> NOT_SKIPPABLE = Set.of("next");
  Set<String> LEAST_SKIPPABLE = Set.of("prev", "first");
  Set<String> MOST_SKIPPABLE = Set.of("alternate");

  default void applyLinks(
      Response.ResponseBuilder response,
      int maxResponseLinkHeaderSize,
      List<javax.ws.rs.core.Link> headerLinks,
      List<String> headerLinkTemplates) {

    List<javax.ws.rs.core.Link> links = headerLinks;
    List<String> linkTemplates = headerLinkTemplates;

    if (linksSize(links, linkTemplates) > maxResponseLinkHeaderSize) {
      links = links.stream().filter(link -> !MOST_SKIPPABLE.contains(link.getRel())).toList();
    }
    if (linksSize(links, linkTemplates) > maxResponseLinkHeaderSize) {
      links =
          links.stream()
              .filter(
                  link ->
                      NOT_SKIPPABLE.contains(link.getRel())
                          || LEAST_SKIPPABLE.contains(link.getRel()))
              .toList();
      linkTemplates = List.of();
    }
    if (linksSize(links, linkTemplates) > maxResponseLinkHeaderSize) {
      links = links.stream().filter(link -> NOT_SKIPPABLE.contains(link.getRel())).toList();
    }

    links.forEach(response::links);
    linkTemplates.forEach(template -> response.header("Link-Template", template));
  }

  default int linksSize(List<javax.ws.rs.core.Link> links, List<String> linkTemplates) {
    return links.stream().map(l -> l.toString().length()).mapToInt(Integer::intValue).sum()
        + linkTemplates.stream().map(String::length).mapToInt(Integer::intValue).sum();
  }

  default List<String> getLinkTemplates(List<Link> links) {
    return links.stream()
        .filter(link -> link.getTemplated() != null && link.getTemplated())
        .sorted(Link.COMPARATOR_LINKS)
        .map(
            template -> {
              StringBuilder builder =
                  new StringBuilder(
                      String.format("<%s>; rel=\"%s\"", template.getHref(), template.getRel()));
              if (template.getTitle() != null) {
                builder.append(String.format("; title=\"%s\"", template.getTitle()));
              }
              if (template.getType() != null) {
                builder.append(String.format("; type=\"%s\"", template.getType()));
              }
              return builder.toString();
            })
        .collect(Collectors.toUnmodifiableList());
  }

  default Date getLastModified(QueryInput queryInput, PageRepresentation resource) {
    return queryInput.getLastModified().or(resource::getLastModified).orElse(null);
  }

  default Date getLastModified(QueryInput queryInput) {
    return queryInput.getLastModified().orElse(null);
  }

  default List<Link> getLinks(ApiRequestContext requestContext, I18n i18n) {
    return new DefaultLinksGenerator()
        .generateLinks(
            requestContext.getUriCustomizer(),
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            i18n,
            requestContext.getLanguage());
  }

  default List<Link> getLinks(
      ApiRequestContext requestContext,
      List<Profile> profiles,
      Map<ApiMediaType, List<Profile>> alternateProfiles,
      I18n i18n) {
    return new DefaultLinksGenerator()
        .generateLinks(
            requestContext.getUriCustomizer(),
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            profiles,
            alternateProfiles,
            i18n,
            requestContext.getLanguage());
  }

  default Map<ApiMediaType, List<Profile>> getAlternateProfiles(
      List<ProfileSet> allProfileSets,
      OgcApiDataV2 apiData,
      String collectionId,
      ApiMediaType mediaType,
      List<ApiMediaType> alternateMediaTypes,
      List<Profile> profiles) {
    return allProfileSets.stream()
        .filter(ProfileExtension::includeAlternateLinks)
        .filter(
            profileSet ->
                mediaType.type().equals(profileSet.getMediaType())
                    || alternateMediaTypes.stream()
                        .anyMatch(mt -> mt.type().equals(profileSet.getMediaType())))
        .map(
            profileSet ->
                new SimpleImmutableEntry<>(
                    Stream.concat(alternateMediaTypes.stream(), Stream.of(mediaType))
                        .filter(mt -> mt.type().equals(profileSet.getMediaType()))
                        .findFirst()
                        .get(),
                    profileSet.getProfiles(apiData, Optional.of(collectionId)).stream()
                        .filter(profile -> !profiles.contains(profile))
                        .toList()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  default List<Profile> negotiateProfiles(
      List<ProfileSet> allProfileSets,
      FormatExtension outputFormat,
      ResourceType resourceType,
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      List<Profile> requestedProfiles,
      List<Profile> defaultProfilesResource) {
    List<Profile> givenProfiles =
        allProfileSets.stream()
            .filter(
                p ->
                    collectionId
                        .map(cid -> p.isEnabledForApi(apiData, cid))
                        .orElse(p.isEnabledForApi(apiData)))
            .map(
                profileSet ->
                    profileSet
                        .negotiateProfile(
                            requestedProfiles,
                            defaultProfilesResource,
                            outputFormat,
                            resourceType,
                            apiData,
                            collectionId)
                        .orElse(null))
            .filter(Objects::nonNull)
            .toList();

    List<Profile> profiles = List.copyOf(givenProfiles);

    for (Profile profile : givenProfiles) {
      if (profile instanceof ProfileFilter) {
        profiles = ((ProfileFilter) profile).filterProfiles(profiles);
      }
    }

    return profiles;
  }
}
