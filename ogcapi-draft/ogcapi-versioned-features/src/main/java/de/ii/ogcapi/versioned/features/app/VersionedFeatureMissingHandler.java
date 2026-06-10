/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SingleFeatureMissingHandler;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.features.domain.CollectionMetadata;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureStream;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.SortKey;
import de.ii.xtraplatform.features.domain.Tuple;
import de.ii.xtraplatform.streams.domain.Reactive.Sink;
import de.ii.xtraplatform.web.domain.URICustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distinguishes {@code 404 Not Found} from {@code 410 Gone} on a versioned collection: if the
 * canonical id exists in any state (open or retired) but no version matched the (default or
 * supplied) {@code datetime}, the response status is {@code 410}. If the canonical id is unknown to
 * the provider, this handler declines and the queries handler falls back to its default {@code
 * 404}.
 *
 * <p>For the 410 path the handler also probes for the {@code PRIMARY_INTERVAL_START} of the latest
 * version of the canonical id so the {@code latest-version} link can carry that timestamp.
 */
@Singleton
@AutoBind
public class VersionedFeatureMissingHandler implements SingleFeatureMissingHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(VersionedFeatureMissingHandler.class);

  private final FeaturesCoreProviders providers;
  private final I18n i18n;

  @Inject
  public VersionedFeatureMissingHandler(FeaturesCoreProviders providers, I18n i18n) {
    this.providers = providers;
    this.i18n = i18n;
  }

  private static String relToI18nKey(String rel) {
    String[] parts = rel.split("-");
    StringBuilder sb = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
    }
    sb.append("Link");
    return sb.toString();
  }

  private String linkHeader(String href, String rel, ApiRequestContext requestContext) {
    String title = i18n.get(relToI18nKey(rel), requestContext.getLanguage());
    return String.format("<%s>; rel=\"%s\"; title=\"%s\"", href, rel, title);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return VersionedFeaturesConfiguration.class;
  }

  @Override
  public Optional<Response> handleMissing(
      OgcApi api, String collectionId, String featureId, ApiRequestContext requestContext) {
    if (!isEnabledForApi(api.getData(), collectionId)) {
      return Optional.empty();
    }

    FeatureTypeConfigurationOgcApi collectionData =
        api.getData().getCollections().get(collectionId);
    if (collectionData == null) {
      return Optional.empty();
    }
    String featureTypeId =
        collectionData
            .getExtension(FeaturesCoreConfiguration.class)
            .flatMap(FeaturesCoreConfiguration::getFeatureType)
            .orElse(collectionId);

    Optional<FeatureProvider> providerOpt =
        providers.getFeatureProvider(api.getData(), collectionData);
    if (providerOpt.isEmpty() || !providerOpt.get().queries().isAvailable()) {
      return Optional.empty();
    }
    FeatureProvider provider = providerOpt.get();

    Optional<String> startFieldPath =
        providers
            .getQueryablesSchema(api.getData(), collectionData)
            .flatMap(SchemaBase::getPrimaryInterval)
            .map(Tuple::first)
            .filter(java.util.Objects::nonNull)
            .map(FeatureSchema::getFullPathAsString);

    ImmutableFeatureQuery.Builder probeBuilder =
        ImmutableFeatureQuery.builder()
            .type(featureTypeId)
            .filter(In.of(ScalarLiteral.of(featureId)))
            .limit(1);
    startFieldPath.ifPresent(
        path -> probeBuilder.addSortKeys(SortKey.of(path, SortKey.Direction.DESCENDING)));
    FeatureQuery probe = probeBuilder.build();

    long matched;
    Optional<Instant> latestStart = Optional.empty();
    try {
      FeatureStream stream = provider.queries().get().getFeatureStream(probe);
      CompletableFuture<CollectionMetadata> onMetadata = new CompletableFuture<>();
      FeatureStream.Result result =
          stream.runWith(Sink.ignore(), Map.of(), onMetadata).toCompletableFuture().join();
      matched = onMetadata.join().getNumberMatched().orElse(0L);
      latestStart = result.getTemporalExtent().map(Tuple::first).filter(java.util.Objects::nonNull);
    } catch (Exception e) {
      LOGGER.debug(
          "Existence probe for '{}/{}' failed; falling back to 404", collectionId, featureId, e);
      return Optional.empty();
    }

    if (matched > 0L) {
      String originalHref = requestContext.getUriCustomizer().copy().clearParameters().toString();
      String latestHref =
          latestStart
              .map(
                  start ->
                      requestContext
                          .getUriCustomizer()
                          .copy()
                          .clearParameters()
                          .setParameter("datetime", start.toString())
                          .toString())
              .orElse(originalHref);
      String timeMapHref =
          requestContext
              .getUriCustomizer()
              .copy()
              .clearParameters()
              .ensureLastPathSegment("versions")
              .toString();

      Object body;
      MediaType bodyType;
      if (MediaType.TEXT_HTML_TYPE.equals(requestContext.getMediaType().type())) {
        body = goneHtmlBody(api, collectionId, featureId, requestContext, timeMapHref, latestHref);
        bodyType = MediaType.TEXT_HTML_TYPE;
      } else {
        body = goneProblemJson(featureId, requestContext, originalHref, latestHref, timeMapHref);
        bodyType = MediaType.valueOf("application/problem+json");
      }

      return Optional.of(
          Response.status(Response.Status.GONE)
              .header("Link", linkHeader(originalHref, "original", requestContext))
              .header("Link", linkHeader(latestHref, "latest-version", requestContext))
              .header("Link", linkHeader(timeMapHref, "timemap", requestContext))
              .header("Link", linkHeader(timeMapHref, "version-history", requestContext))
              .type(bodyType)
              .entity(body)
              .build());
    }
    return Optional.empty();
  }

  private static final ObjectMapper JSON = new ObjectMapper();

  private String goneProblemJson(
      String featureId,
      ApiRequestContext requestContext,
      String originalHref,
      String latestHref,
      String timeMapHref) {
    var lang = requestContext.getLanguage();
    ObjectNode body = JSON.createObjectNode();
    body.put("status", 410);
    body.put("title", goneTitle(requestContext));
    body.put("detail", i18n.get("featureGoneDetail", lang).replace("{{featureId}}", featureId));
    body.put("instance", originalHref);
    body.put("original", originalHref);
    body.put("latestVersion", latestHref);
    body.put("timeMap", timeMapHref);
    return body.toString();
  }

  // Resolve the request's `datetime` query parameter and format it with a locale-aware
  // medium-style date+time formatter so the localized message reads naturally in either language.
  // `NOW` or absent → server's current instant; anything else → parsed as RFC 3339 / ISO 8601.
  private String formatRequestedDatetime(ApiRequestContext requestContext) {
    String raw =
        requestContext.getUriCustomizer().getQueryParams().stream()
            .filter(p -> "datetime".equalsIgnoreCase(p.getName()))
            .map(p -> p.getValue())
            .findFirst()
            .orElse(null);
    Instant instant;
    if (Objects.isNull(raw) || raw.isBlank() || "NOW".equalsIgnoreCase(raw)) {
      instant = Instant.now();
    } else {
      try {
        instant = OffsetDateTime.parse(raw).toInstant();
      } catch (Throwable ignore) {
        try {
          instant = Instant.parse(raw);
        } catch (Throwable ignore2) {
          // Fall back to the raw string when it can't be parsed (e.g. malformed input that the
          // datetime parameter accepted but we can't render). Better than dropping the marker.
          return raw;
        }
      }
    }
    Locale locale = requestContext.getLanguage().orElse(Locale.ENGLISH);
    // MEDIUM date + LONG time so the locale-aware formatter includes the zone short-name; with
    // `withZone(UTC)` that renders as "UTC" in both EN and DE.
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.LONG)
        .withLocale(locale)
        .withZone(ZoneId.of("UTC"))
        .format(instant);
  }

  private String goneTitle(ApiRequestContext requestContext) {
    return i18n.get("featureGoneTitle", requestContext.getLanguage())
        .replace("{{datetime}}", formatRequestedDatetime(requestContext));
  }

  private Object goneHtmlBody(
      OgcApi api,
      String collectionId,
      String featureId,
      ApiRequestContext requestContext,
      String timeMapHref,
      String latestHref) {
    OgcApiDataV2 apiData = api.getData();
    Locale language = requestContext.getLanguage().orElse(Locale.ENGLISH);
    HtmlConfiguration htmlConfig = apiData.getExtension(HtmlConfiguration.class).orElse(null);

    String title = goneTitle(requestContext);
    String description =
        i18n.get("featureGoneDescription", requestContext.getLanguage())
            .replace("{{featureId}}", featureId);

    // URI ends with `.../collections/<cid>/items/<fid>` (4 segments under the service).
    URICustomizer uri = requestContext.getUriCustomizer().copy().clearParameters();
    List<NavigationDTO> breadCrumbs =
        new ImmutableList.Builder<NavigationDTO>()
            .add(
                new NavigationDTO(
                    i18n.get("root", requestContext.getLanguage()),
                    uri.copy().removeLastPathSegments(apiData.getSubPath().size() + 4).toString()))
            .add(
                new NavigationDTO(
                    apiData.getLabel(), uri.copy().removeLastPathSegments(4).toString()))
            .add(
                new NavigationDTO(
                    i18n.get("collectionsTitle", requestContext.getLanguage()),
                    uri.copy().removeLastPathSegments(3).toString()))
            .add(
                new NavigationDTO(
                    apiData.getCollections().get(collectionId).getLabel(),
                    uri.copy().removeLastPathSegments(2).toString()))
            .add(new NavigationDTO(featureId))
            .build();

    return new ImmutableFeatureGoneView.Builder()
        .apiData(apiData)
        .htmlConfig(htmlConfig)
        .basePath(requestContext.getBasePath())
        .apiPath(requestContext.getApiPath())
        .uriCustomizer(requestContext.getUriCustomizer().copy())
        .breadCrumbs(breadCrumbs)
        .title(title)
        .description(description)
        .featureId(featureId)
        .timeMapHref(timeMapHref)
        .latestVersionHref(latestHref)
        .i18n(i18n)
        .language(language)
        .rawLinks(ImmutableList.of())
        .user(requestContext.getUser())
        .build();
  }
}
