/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.DefaultLinksGenerator;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.styles.domain.ImmutableStyleMetadata;
import de.ii.ogcapi.styles.domain.ImmutableStyles;
import de.ii.ogcapi.styles.domain.ImmutableStylesheetMetadata;
import de.ii.ogcapi.styles.domain.MbStyleStylesheet;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StyleMetadata;
import de.ii.ogcapi.styles.domain.StyleMetadataFormatExtension;
import de.ii.ogcapi.styles.domain.StyleRepository;
import de.ii.ogcapi.styles.domain.Styles;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.styles.domain.StylesFormatExtension;
import de.ii.ogcapi.styles.domain.StylesLinkGenerator;
import de.ii.ogcapi.styles.domain.StylesheetContent;
import de.ii.ogcapi.styles.domain.StylesheetMetadata;
import de.ii.ogcapi.styles.domain.Tiles3dStylesheet;
import de.ii.ogcapi.tiles.domain.ImmutableStyleEntry;
import de.ii.ogcapi.tiles.domain.StyleEntry;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatile;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.values.domain.KeyValueStore;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import de.ii.xtraplatform.web.domain.LastModified;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class StyleRepositoryFiles extends AbstractVolatile
    implements StyleRepository, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(StyleRepositoryFiles.class);

  private final ExtensionRegistry extensionRegistry;
  private final ResourceStore stylesStore;
  private final ResourceStore legendsStore;
  private final KeyValueStore<MbStyleStylesheet> mbStylesStore;
  private final KeyValueStore<Tiles3dStylesheet> tiles3dStylesStore;
  private final I18n i18n;
  private final DefaultLinksGenerator defaultLinkGenerator;
  private final URI servicesUri;
  private final FeaturesCoreProviders providers;
  private final Values<Codelist> codelistStore;
  private final VolatileRegistry volatileRegistry;
  private final TilesProviders tilesProviders;

  @Inject
  public StyleRepositoryFiles(
      ResourceStore blobStore,
      ServicesContext servicesContext,
      ExtensionRegistry extensionRegistry,
      I18n i18n,
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      VolatileRegistry volatileRegistry,
      TilesProviders tilesProviders) {
    super(volatileRegistry, "app/styles");
    this.stylesStore = blobStore.with(StylesBuildingBlock.STORE_RESOURCE_TYPE);
    this.legendsStore = blobStore.with(StylesBuildingBlock.STORE_RESOURCE_TYPE_LEGENDS);
    this.mbStylesStore = valueStore.forTypeWritable(MbStyleStylesheet.class);
    this.tiles3dStylesStore = valueStore.forTypeWritable(Tiles3dStylesheet.class);
    this.i18n = i18n;
    this.extensionRegistry = extensionRegistry;
    this.servicesUri = servicesContext.getUri();
    this.providers = providers;
    this.codelistStore = valueStore.forType(Codelist.class);
    this.volatileRegistry = volatileRegistry;
    this.tilesProviders = tilesProviders;
    this.defaultLinkGenerator = new DefaultLinksGenerator();
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    onVolatileStart();

    return volatileRegistry
        .onAvailable(stylesStore, mbStylesStore, tiles3dStylesStore)
        .thenRun(() -> setState(State.AVAILABLE));
  }

  public Stream<StylesFormatExtension> getStylesFormatStream(
      OgcApiDataV2 apiData, Optional<String> collectionId) {
    return extensionRegistry.getExtensionsForType(StylesFormatExtension.class).stream()
        .filter(
            format ->
                collectionId
                    .map(s -> format.isEnabledForApi(apiData, s))
                    .orElseGet(() -> format.isEnabledForApi(apiData)));
  }

  public Stream<StyleFormatExtension> getStyleFormatStream(
      OgcApiDataV2 apiData, Optional<String> collectionId) {
    return extensionRegistry.getExtensionsForType(StyleFormatExtension.class).stream()
        .filter(
            format ->
                collectionId
                    .map(s -> format.isEnabledForApi(apiData, s))
                    .orElseGet(() -> format.isEnabledForApi(apiData)));
  }

  public Stream<StyleMetadataFormatExtension> getStyleMetadataFormatStream(
      OgcApiDataV2 apiData, Optional<String> collectionId) {
    return extensionRegistry.getExtensionsForType(StyleMetadataFormatExtension.class).stream()
        .filter(
            format ->
                collectionId
                    .map(s -> format.isEnabledForApi(apiData, s))
                    .orElseGet(() -> format.isEnabledForApi(apiData)));
  }

  @Override
  public List<ApiMediaType> getStylesheetMediaTypes(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      boolean includeDerived,
      boolean includeHtml) {
    return getStyleFormatStream(apiData, collectionId)
        .filter(
            styleFormat ->
                stylesheetExists(apiData, collectionId, styleId, styleFormat, includeDerived)
                    || (includeHtml
                        && styleFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)))
        .map(StyleFormatExtension::getMediaType)
        .collect(Collectors.toList());
  }

  @Override
  public Date getStyleLastModified(
      OgcApiDataV2 apiData, Optional<String> collectionId, String styleId) {
    return getStyleFormatStream(apiData, collectionId)
        .filter(styleFormat -> stylesheetExists(apiData, collectionId, styleId, styleFormat))
        .map(
            styleFormat ->
                getStylesheetLastModified(apiData, collectionId, styleId, styleFormat, true))
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }

  @Override
  public Date getStylesheetLastModified(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat,
      boolean includeDerived) {
    // a stylesheet exists, if we have a stylesheet document or if we can derive one
    if (exists(apiData, collectionId, styleId, styleFormat)) {
      try {
        if (isMbStyle(styleFormat)) {
          return LastModified.from(
              mbStylesStore.lastModified(styleId, getPathArrayStyles(apiData, collectionId)));
        }
        if (is3dTilesStyle(styleFormat)) {
          return LastModified.from(
              tiles3dStylesStore.lastModified(styleId, getPathArrayStyles(apiData, collectionId)));
        }
        return LastModified.from(
            stylesStore.lastModified(getPathStyle(apiData, collectionId, styleId, styleFormat)));
      } catch (IOException e) {
        // continue
      }
    }

    if (includeDerived
        && collectionId.isPresent()
        && deriveCollectionStylesEnabled(apiData, collectionId.get())) {
      if (exists(apiData, Optional.empty(), styleId, styleFormat)) {
        try {
          if (isMbStyle(styleFormat)) {
            return LastModified.from(mbStylesStore.lastModified(styleId, apiData.getId()));
          }
          if (is3dTilesStyle(styleFormat)) {
            return LastModified.from(tiles3dStylesStore.lastModified(styleId, apiData.getId()));
          }
          return LastModified.from(
              stylesStore.lastModified(getPathStyle(apiData, collectionId, styleId, styleFormat)));
        } catch (IOException e) {
          // continue
        }
      }
    }

    return null;
  }

  @Override
  public Styles getStyles(
      OgcApiDataV2 apiData, Optional<String> collectionId, ApiRequestContext requestContext) {
    final StylesLinkGenerator stylesLinkGenerator = new StylesLinkGenerator();

    List<StyleEntry> styleEntries =
        getStyleIds(apiData, collectionId).stream()
            .map(
                styleId -> {
                  ImmutableStyleEntry.Builder builder =
                      ImmutableStyleEntry.builder()
                          .id(styleId)
                          .title(
                              getTitle(apiData, collectionId, styleId, requestContext)
                                  .orElse(styleId));

                  Date lastModified = getStyleLastModified(apiData, collectionId, styleId);
                  if (Objects.nonNull(lastModified)) builder.lastModified(lastModified);

                  List<ApiMediaType> mediaTypes =
                      getStylesheetMediaTypes(apiData, collectionId, styleId, true, false);
                  builder.links(
                      stylesLinkGenerator.generateStyleLinks(
                          requestContext.getUriCustomizer(),
                          styleId,
                          mediaTypes,
                          i18n,
                          requestContext.getLanguage()));
                  if (collectionId.isPresent()) {
                    List<ApiMediaType> additionalMediaTypes =
                        getStyleFormatStream(apiData, collectionId)
                            .filter(
                                format ->
                                    format.canDeriveCollectionStyle()
                                        && !mediaTypes.contains(format.getMediaType())
                                        && willDeriveStylesheet(
                                            apiData, collectionId, styleId, format))
                            .map(StyleFormatExtension::getMediaType)
                            .collect(Collectors.toUnmodifiableList());
                    if (!additionalMediaTypes.isEmpty()) {
                      builder.addAllLinks(
                          stylesLinkGenerator.generateStyleLinks(
                              requestContext.getUriCustomizer(),
                              styleId,
                              additionalMediaTypes,
                              i18n,
                              requestContext.getLanguage()));
                    }
                  }
                  builder.addLinks(
                      stylesLinkGenerator.generateStyleMetadataLink(
                          requestContext.getUriCustomizer(),
                          styleId,
                          i18n,
                          requestContext.getLanguage()));
                  if (tilesProviders
                      .getRasterTilesetMetadata(
                          apiData, collectionId.flatMap(apiData::getCollectionData))
                      .containsKey(styleId)) {
                    builder.addLinks(
                        stylesLinkGenerator.generateMapTilesLink(
                            requestContext.getUriCustomizer(),
                            styleId,
                            i18n,
                            requestContext.getLanguage()));
                  }

                  final java.nio.file.Path resourcePath =
                      Path.of(apiData.getId()).resolve(String.format("%s.png", styleId));
                  try {
                    if (legendsStore.has(resourcePath)) {
                      builder.addLinks(
                          stylesLinkGenerator.generateStyleLegendLink(
                              requestContext.getUriCustomizer(),
                              styleId,
                              i18n,
                              requestContext.getLanguage()));
                    }
                  } catch (IOException ignore) {
                    // ignore, skip link
                  }
                  return builder.build();
                })
            .collect(Collectors.toList());

    return ImmutableStyles.builder()
        .styles(styleEntries)
        .lastModified(
            styleEntries.stream()
                .map(StyleEntry::getLastModified)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.naturalOrder()))
        .links(
            new DefaultLinksGenerator()
                .generateLinks(
                    requestContext.getUriCustomizer(),
                    requestContext.getMediaType(),
                    requestContext.getAlternateMediaTypes(),
                    i18n,
                    requestContext.getLanguage()))
        .build();
  }

  @Override
  public Styles getStyles(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      ApiRequestContext requestContext,
      boolean includeDerived) {
    Styles styles = getStyles(apiData, collectionId, requestContext);
    if (!includeDerived
        || collectionId.isEmpty()
        || !deriveCollectionStylesEnabled(apiData, collectionId.get())) return styles;

    Set<String> existingStyleIds =
        styles.getStyles().stream().map(StyleEntry::getId).collect(Collectors.toUnmodifiableSet());
    Styles rootStyles = getStyles(apiData, Optional.empty(), requestContext);
    return ImmutableStyles.builder()
        .from(styles)
        .addAllStyles(
            rootStyles.getStyles().stream()
                .filter(styleEntry -> !existingStyleIds.contains(styleEntry.getId()))
                .map(
                    styleEntry ->
                        deriveCollectionStyleEntry(
                            apiData, styleEntry, collectionId.get(), requestContext))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toUnmodifiableList()))
        .build();
  }

  private boolean exists(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat) {
    try {
      if (isMbStyle(styleFormat)) {
        return mbStylesStore.has(styleId, getPathArrayStyles(apiData, collectionId));
      }
      if (is3dTilesStyle(styleFormat)) {
        return tiles3dStylesStore.has(styleId, getPathArrayStyles(apiData, collectionId));
      }
      return stylesStore.has(getPathStyle(apiData, collectionId, styleId, styleFormat));
    } catch (IOException e) {
      return false;
    }
  }

  public boolean stylesheetExists(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat) {
    // exclude derived stylesheets
    return stylesheetExists(apiData, collectionId, styleId, styleFormat, false);
  }

  public boolean stylesheetExists(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat,
      boolean includeDerived) {
    // a stylesheet exists, if we have a stylesheet document or if we can derive one
    return exists(apiData, collectionId, styleId, styleFormat)
        || (includeDerived && willDeriveStylesheet(apiData, collectionId, styleId, styleFormat));
  }

  private boolean willDeriveStylesheet(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat) {
    // for specific style encodings, we derive a style for a single feature collection from a
    // multi-collection stylesheet, if this capability is not switched off for the collection
    try {
      return collectionId.isPresent()
          && deriveCollectionStylesEnabled(apiData, collectionId.get())
          && exists(apiData, Optional.empty(), styleId, styleFormat)
          && styleFormat.canDeriveCollectionStyle()
          && styleFormat
              .deriveCollectionStyle(
                  getStylesheetContent(apiData, Optional.empty(), styleId, styleFormat),
                  apiData,
                  collectionId.get(),
                  styleId)
              .isPresent();
    } catch (IOException e) {
      LOGGER.error(
          "Could not derive stylesheet for style '{}' in collection '{}' in API '{}' for style encoding '{}'",
          styleId,
          collectionId,
          apiData.getId(),
          styleFormat.getMediaType().label());
    }
    return false;
  }

  private StylesheetContent getStylesheetContent(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat)
      throws IOException {
    Path pathStyle = getPathStyle(apiData, collectionId, styleId, styleFormat);

    if (isMbStyle(styleFormat)) {
      MbStyleStylesheet stylesheet =
          mbStylesStore.get(styleId, getPathArrayStyles(apiData, collectionId));
      return new StylesheetContent(
          StyleFormatMbStyle.toBytes(stylesheet), pathStyle.toString(), true, stylesheet);
    }
    if (is3dTilesStyle(styleFormat)) {
      Tiles3dStylesheet stylesheet =
          tiles3dStylesStore.get(styleId, getPathArrayStyles(apiData, collectionId));
      return new StylesheetContent(
          StyleFormat3dTiles.toBytes(stylesheet), pathStyle.toString(), true, stylesheet);
    }

    return new StylesheetContent(
        stylesStore.content(pathStyle).get().readAllBytes(), pathStyle.toString(), true);
  }

  public StylesheetContent getStylesheet(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat,
      ApiRequestContext requestContext) {
    if (!stylesheetExists(apiData, collectionId, styleId, styleFormat)) {
      boolean styleExists = getStyleIds(apiData, collectionId).contains(styleId);

      if (styleExists) {
        if (collectionId.isPresent())
          throw new NotAcceptableException(
              MessageFormat.format(
                  "The style ''{0}'' is not available in the requested format ''{1}'' for collection ''{2}''.",
                  styleId, styleFormat.getMediaType().label(), collectionId.get()));
        throw new NotAcceptableException(
            MessageFormat.format(
                "The style ''{0}'' is not available in the requested format ''{1}''.",
                styleId, styleFormat.getMediaType().label()));
      }

      if (collectionId.isPresent())
        throw new NotFoundException(
            MessageFormat.format(
                "The style ''{0}'' does not exist in this API for collection ''{1}''.",
                styleId, collectionId.get()));
      throw new NotFoundException(
          MessageFormat.format("The style ''{0}'' does not exist in this API.", styleId));
    }

    try {
      return getStylesheetContent(apiData, collectionId, styleId, styleFormat);
    } catch (IOException e) {
      if (collectionId.isPresent())
        throw new RuntimeException(
            MessageFormat.format(
                "The style ''{0}'' could not be parsed for collection ''{1}''.",
                styleId, collectionId.get()),
            e);
      throw new RuntimeException(
          MessageFormat.format("The style ''{0}'' could not be parsed.", styleId), e);
    }
  }

  @Override
  public StylesheetContent getStylesheet(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat,
      ApiRequestContext requestContext,
      boolean includeDerived) {
    if (collectionId.isEmpty() || stylesheetExists(apiData, collectionId, styleId, styleFormat))
      return getStylesheet(apiData, collectionId, styleId, styleFormat, requestContext);

    if (stylesheetExists(apiData, collectionId, styleId, styleFormat, true)) {
      Optional<StylesheetContent> stylesheet =
          styleFormat.deriveCollectionStyle(
              getStylesheet(apiData, Optional.empty(), styleId, styleFormat, requestContext),
              apiData,
              collectionId.get(),
              styleId);
      if (stylesheet.isPresent()) return stylesheet.get();
    }

    throw new NotFoundException(
        MessageFormat.format(
            "The style ''{0}'' does not exist in this API for collection ''{1}''.",
            styleId, collectionId.get()));
  }

  @Override
  public StyleMetadata getStyleMetadata(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      ApiRequestContext requestContext) {
    if (getStylesheetMediaTypes(apiData, collectionId, styleId, true, false).isEmpty()) {
      if (collectionId.isEmpty())
        throw new NotFoundException(
            MessageFormat.format("The style ''{0}'' does not exist in this API.", styleId));
      throw new NotFoundException(
          MessageFormat.format(
              "The style ''{0}'' does not exist in this API for collection ''{1}''.",
              styleId, collectionId.get()));
    }

    // derive standard links (self/alternate)
    List<Link> links =
        defaultLinkGenerator.generateLinks(
            requestContext.getUriCustomizer(),
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            i18n,
            requestContext.getLanguage());

    // Derive standard metadata from the style
    // TODO move outside of this specific repository implementation
    List<StylesheetMetadata> stylesheets =
        deriveStylesheetMetadata(apiData, collectionId, styleId, requestContext);
    Optional<String> title = getTitle(apiData, collectionId, styleId, requestContext);

    Optional<StyleFormatExtension> format =
        getStyleFormatStream(apiData, collectionId)
            .filter(StyleFormatExtension::canDeriveMetadata)
            .filter(f -> stylesheetExists(apiData, collectionId, styleId, f, true))
            .findAny();
    ImmutableStyleMetadata.Builder builder =
        ImmutableStyleMetadata.builder()
            .id(Optional.ofNullable(styleId))
            .title(title.orElse(styleId))
            .stylesheets(stylesheets)
            .addAllLinks(links);
    format.ifPresent(
        styleFormatExtension ->
            builder.layers(
                styleFormatExtension.deriveLayerMetadata(
                    getStylesheet(
                        apiData, collectionId, styleId, styleFormatExtension, requestContext, true),
                    apiData,
                    providers,
                    codelistStore)));
    StyleMetadata metadata = builder.build();

    URICustomizer uriCustomizer =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(apiData.getSubPath().toArray(String[]::new));
    String serviceUrl = uriCustomizer.toString();
    return metadata.replaceParameters(serviceUrl);
  }

  @Override
  public Optional<Blob> getStyleLegend(
      OgcApiDataV2 apiData, Optional<String> collectionId, String styleId) {
    if (getStylesheetMediaTypes(apiData, collectionId, styleId, true, false).isEmpty()) {
      if (collectionId.isEmpty())
        throw new NotFoundException(
            MessageFormat.format("The style ''{0}'' does not exist in this API.", styleId));
      throw new NotFoundException(
          MessageFormat.format(
              "The style ''{0}'' does not exist in this API for collection ''{1}''.",
              styleId, collectionId.get()));
    }

    try {
      return legendsStore.get(getStyleLegendPath(apiData, styleId));
    } catch (IOException e) {
      if (LOGGER.isWarnEnabled()) {
        if (collectionId.isPresent()) {
          LOGGER.warn(
              "Could not retrieve legend for style '{}' in collection '{}'.",
              styleId,
              collectionId.get(),
              e);
        } else {
          LOGGER.warn("Could not retrieve legend for style '{}'.", styleId, e);
        }
      }
      return Optional.empty();
    }
  }

  private java.nio.file.Path getStyleLegendPath(OgcApiDataV2 apiData, String styleId) {
    return Path.of(apiData.getId()).resolve(String.format("%s.png", styleId));
  }

  @Override
  public ImmutableValidationResult.Builder validate(
      ImmutableValidationResult.Builder builder,
      OgcApiDataV2 apiData,
      Optional<String> collectionId) {
    Optional<StylesConfiguration> config =
        (collectionId.isEmpty() ? apiData : apiData.getCollections().get(collectionId.get()))
            .getExtension(StylesConfiguration.class);
    if (config.isPresent() && config.get().isEnabled()) {
      List<String> formatLabels =
          getStyleFormatStream(apiData, collectionId)
              .map(format -> format.getMediaType().label())
              .collect(Collectors.toUnmodifiableList());
      for (String encoding : config.get().getStyleEncodings()) {
        if (!formatLabels.contains(encoding)) {
          builder.addStrictErrors(
              MessageFormat.format(
                  "The style encoding ''{0}'' is specified in the STYLES module configuration, but the format does not exist.",
                  encoding));
        }
      }

      // check that the default stylesheet for the web map exists
      String defaultStyle =
          (collectionId.isEmpty() ? apiData : apiData.getCollections().get(collectionId.get()))
              .getExtension(HtmlConfiguration.class)
              .map(HtmlConfiguration::getDefaultStyle)
              .orElse("NONE");
      if (!defaultStyle.equals("NONE")) {
        boolean exists =
            getStyleFormatStream(apiData, collectionId)
                .filter(StyleFormatExtension::getAsDefault)
                .anyMatch(
                    format -> stylesheetExists(apiData, collectionId, defaultStyle, format, true));
        if (!exists) {
          if (collectionId.isPresent())
            builder.addWarnings(
                MessageFormat.format(
                    "The default style ''{0}'' specified in the HTML module configuration does not exist for collection ''{1}'' and no link to the web map will be created.",
                    defaultStyle, collectionId.get()));
          else
            builder.addWarnings(
                MessageFormat.format(
                    "The default style ''{0}'' specified in the HTML module configuration does not exist and no link to the web map will be created.",
                    defaultStyle));
        }
      }

      if (collectionId.isPresent()) {
        // all feature access is on the collection level
        final String style =
            apiData
                .getExtension(FeaturesHtmlConfiguration.class, collectionId.get())
                .map(FeaturesHtmlConfiguration::getStyle)
                .orElse(defaultStyle);
        if (!style.equals("NONE") && !style.equals("DEFAULT")) {
          boolean exists =
              getStyleFormatStream(apiData, collectionId)
                  .filter(StyleFormatExtension::getAsDefault)
                  .anyMatch(format -> stylesheetExists(apiData, collectionId, style, format, true));
          if (!exists) {
            builder.addWarnings(
                MessageFormat.format(
                    "The style ''{0}'' specified in the FEATURES_HTML module configuration does not exist for collection ''{1}''. 'NONE' will be used instead.",
                    style, collectionId.get()));
          }
        }
      }

      final String style =
          (collectionId.isEmpty()
                  ? apiData.getExtension(TilesConfiguration.class)
                  : apiData.getExtension(TilesConfiguration.class, collectionId.get()))
              .map(TilesConfiguration::getStyle)
              .orElse(defaultStyle);
      if (!style.equals("NONE") && !style.equals("DEFAULT")) {
        boolean exists =
            getStyleFormatStream(apiData, collectionId)
                .filter(StyleFormatExtension::getAsDefault)
                .anyMatch(format -> stylesheetExists(apiData, collectionId, style, format, true));
        if (!exists) {
          if (collectionId.isPresent())
            builder.addWarnings(
                MessageFormat.format(
                    "The style ''{0}'' specified in the TILES module configuration does not exist for collection ''{1}''. 'NONE' will be used instead.",
                    style, collectionId.get()));
          else
            builder.addWarnings(
                MessageFormat.format(
                    "The style ''{0}'' specified in the TILES module configuration does not exist. 'NONE' will be used instead.",
                    style));
        }
      }
    }

    return builder;
  }

  @Override
  public String getNewStyleId(OgcApiDataV2 apiData, Optional<String> collectionId) {
    Set<String> styleIds = getStyleIds(apiData, collectionId, true);
    int id = 1;
    String s;
    while (true) {
      s =
          String.format(
              "%s%s%s", collectionId.orElse(""), (collectionId.isPresent() ? "_" : ""), id++);
      if (!styleIds.contains(s)) return s;
    }
  }

  @Override
  public void writeStyleDocument(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension format,
      byte[] requestBody)
      throws IOException {
    if (isMbStyle(format)) {
      MbStyleStylesheet stylesheet = StyleFormatMbStyle.parse(requestBody, false);
      try {
        mbStylesStore.put(styleId, stylesheet, getPathArrayStyles(apiData, collectionId)).join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof IOException) {
          throw (IOException) e.getCause();
        }
        throw e;
      }
    } else if (is3dTilesStyle(format)) {
      Tiles3dStylesheet stylesheet = StyleFormat3dTiles.parse(requestBody, false);
      try {
        tiles3dStylesStore
            .put(styleId, stylesheet, getPathArrayStyles(apiData, collectionId))
            .join();
      } catch (CompletionException e) {
        if (e.getCause() instanceof IOException) {
          throw (IOException) e.getCause();
        }
        throw e;
      }
    } else {
      stylesStore.put(
          getStylesheetPath(apiData, collectionId, styleId, format),
          new ByteArrayInputStream(requestBody));
    }
  }

  @Override
  public void writeStyleMetadataDocument(
      OgcApiDataV2 apiData, Optional<String> collectionId, String styleId, byte[] requestBody)
      throws IOException {
    stylesStore.put(
        getStyleMetadataPath(apiData, collectionId, styleId),
        new ByteArrayInputStream(requestBody));
  }

  @Override
  public void deleteStyle(OgcApiDataV2 apiData, Optional<String> collectionId, String styleId)
      throws IOException {
    for (StyleFormatExtension format :
        getStyleFormatStream(apiData, collectionId).collect(Collectors.toUnmodifiableList())) {
      if (isMbStyle(format)) {
        mbStylesStore.delete(styleId, getPathArrayStyles(apiData, collectionId));
      }
      if (is3dTilesStyle(format)) {
        tiles3dStylesStore.delete(styleId, getPathArrayStyles(apiData, collectionId));
      }
      stylesStore.delete(getStylesheetPath(apiData, collectionId, styleId, format));
    }
    stylesStore.delete(getStyleMetadataPath(apiData, collectionId, styleId));
  }

  private Path getStylesheetPath(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension format) {
    String filename = styleId + "." + format.getFileExtension();
    return collectionId
        .map(s -> Path.of(apiData.getId()).resolve(s).resolve(filename))
        .orElseGet(() -> Path.of(apiData.getId()).resolve(filename));
  }

  private Path getStyleMetadataPath(
      OgcApiDataV2 apiData, Optional<String> collectionId, String styleId) {
    String filename = styleId + ".metadata";
    return collectionId
        .map(s -> Path.of(apiData.getId()).resolve(s).resolve(filename))
        .orElseGet(() -> Path.of(apiData.getId()).resolve(filename));
  }

  private boolean deriveCollectionStylesEnabled(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollections()
        .get(collectionId)
        .getExtension(StylesConfiguration.class)
        .map(StylesConfiguration::getDeriveCollectionStyles)
        .orElse(false);
  }

  private Optional<StyleEntry> deriveCollectionStyleEntry(
      OgcApiDataV2 apiData,
      StyleEntry rootStyleEntry,
      String collectionId,
      ApiRequestContext requestContext) {

    final String styleId = rootStyleEntry.getId();
    final StylesLinkGenerator stylesLinkGenerator = new StylesLinkGenerator();

    ImmutableStyleEntry.Builder builder =
        ImmutableStyleEntry.builder().from(rootStyleEntry).links(ImmutableList.of());

    getStyleFormatStream(apiData, Optional.of(collectionId))
        .filter(StyleFormatExtension::canDeriveCollectionStyle)
        .filter(format -> stylesheetExists(apiData, Optional.empty(), styleId, format))
        .forEach(
            format -> {
              StylesheetContent stylesheetContent =
                  getStylesheet(apiData, Optional.empty(), styleId, format, requestContext);
              Optional<StylesheetContent> derivedStylesheet =
                  format.deriveCollectionStyle(stylesheetContent, apiData, collectionId, styleId);
              if (derivedStylesheet.isEmpty()) return;

              builder.addAllLinks(
                  stylesLinkGenerator.generateStyleLinks(
                      requestContext.getUriCustomizer(),
                      styleId,
                      ImmutableList.of(format.getMediaType()),
                      i18n,
                      requestContext.getLanguage()));
            });

    if (tilesProviders
        .getRasterTilesetMetadata(apiData, apiData.getCollectionData(collectionId).get())
        .containsKey(styleId)) {
      builder.addLinks(
          stylesLinkGenerator.generateMapTilesLink(
              requestContext.getUriCustomizer(), styleId, i18n, requestContext.getLanguage()));
    }
    try {
      if (legendsStore.has(getStyleLegendPath(apiData, styleId))) {
        builder.addLinks(
            stylesLinkGenerator.generateStyleLegendLink(
                requestContext.getUriCustomizer(), styleId, i18n, requestContext.getLanguage()));
      }
    } catch (IOException ignore) {
      // ignore, skip link
    }

    StyleEntry styleEntry =
        builder
            .addLinks(
                stylesLinkGenerator.generateStyleMetadataLink(
                    requestContext.getUriCustomizer(), styleId, i18n, requestContext.getLanguage()))
            .build();

    return styleEntry.getLinks().size() > 1 ? Optional.of(styleEntry) : Optional.empty();
  }

  private List<StylesheetMetadata> deriveStylesheetMetadata(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      ApiRequestContext requestContext) {
    final StylesLinkGenerator stylesLinkGenerator = new StylesLinkGenerator();
    ImmutableList.Builder<StylesheetMetadata> builder = new ImmutableList.Builder<>();
    builder.addAll(
        getStyleFormatStream(apiData, collectionId)
            .filter(format -> !format.getDerived())
            .filter(format -> stylesheetExists(apiData, collectionId, styleId, format, true))
            .map(
                format ->
                    ImmutableStylesheetMetadata.builder()
                        .native_(true)
                        .title(format.getMediaType().label())
                        .link(
                            stylesLinkGenerator.generateStylesheetLink(
                                requestContext.getUriCustomizer().copy().removeLastPathSegments(2),
                                styleId,
                                format.getMediaType(),
                                i18n,
                                requestContext.getLanguage()))
                        .specification(format.getSpecification())
                        .version(format.getVersion())
                        .build())
            .collect(Collectors.toUnmodifiableList()));
    return builder.build();
  }

  private Path getPathStyles(OgcApiDataV2 apiData, Optional<String> collectionId) {
    return collectionId
        .map(s -> Path.of(apiData.getId()).resolve(s))
        .orElseGet(() -> Path.of(apiData.getId()));
  }

  private String[] getPathArrayStyles(OgcApiDataV2 apiData, Optional<String> collectionId) {
    return collectionId
        .map(s -> new String[] {apiData.getId(), s})
        .orElseGet(() -> new String[] {apiData.getId()});
  }

  private boolean isMbStyle(StyleFormatExtension format) {
    return format instanceof StyleFormatMbStyle || format instanceof StyleFormatHtml;
  }

  private boolean is3dTilesStyle(StyleFormatExtension format) {
    return format instanceof StyleFormat3dTiles;
  }

  private Path getPathStyle(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      StyleFormatExtension styleFormat) {
    return getPathStyles(apiData, collectionId)
        .resolve(String.format("%s.%s", styleId, styleFormat.getFileExtension()));
  }

  private Optional<String> getTitle(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String styleId,
      ApiRequestContext requestContext) {
    return getStyleFormatStream(apiData, collectionId)
        .filter(format -> stylesheetExists(apiData, collectionId, styleId, format))
        .map(
            format ->
                format.getTitle(
                    styleId, getStylesheet(apiData, collectionId, styleId, format, requestContext)))
        .filter(title -> !title.equals(styleId))
        .findAny();
  }

  private Set<String> getStyleIds(OgcApiDataV2 apiData, Optional<String> collectionId) {
    Set<String> formatExt =
        getStyleFormatStream(apiData, collectionId)
            .filter(format -> !format.getDerived())
            .map(StyleFormatExtension::getFileExtension)
            .collect(Collectors.toUnmodifiableSet());
    Path parent = getPathStyles(apiData, collectionId);
    String[] parentArray = getPathArrayStyles(apiData, collectionId);

    try (Stream<Path> fileStream =
        stylesStore.walk(
            parent,
            1,
            (path, attributes) ->
                attributes.isValue()
                    && !attributes.isHidden()
                    && formatExt.contains(
                        com.google.common.io.Files.getFileExtension(
                            path.getFileName().toString())))) {
      return Stream.concat(
              Stream.concat(
                  mbStylesStore.identifiers(parentArray).stream()
                      .filter(identifier -> identifier.path().size() == parentArray.length)
                      .map(Identifier::id),
                  tiles3dStylesStore.identifiers(parentArray).stream()
                      .filter(identifier -> identifier.path().size() == parentArray.length)
                      .map(Identifier::id)),
              fileStream.map(
                  path ->
                      com.google.common.io.Files.getNameWithoutExtension(
                          path.getFileName().toString())))
          .distinct()
          .sorted()
          .collect(ImmutableSet.toImmutableSet());
    } catch (IOException e) {
      LogContext.error(LOGGER, e, "Could not parse styles");
    }
    return ImmutableSet.of();
  }

  public Set<String> getStyleIds(
      OgcApiDataV2 apiData, Optional<String> collectionId, boolean includeDerived) {
    if (includeDerived
        && collectionId.isPresent()
        && deriveCollectionStylesEnabled(apiData, collectionId.get())) {
      // add the styles from the parent style collection, that are used to derive a style for the
      // collection
      Stream<String> derivedStream =
          getStyleIds(apiData, Optional.empty()).stream()
              .filter(
                  styleId ->
                      getStyleFormatStream(apiData, collectionId)
                          .anyMatch(
                              format ->
                                  willDeriveStylesheet(apiData, collectionId, styleId, format)));

      return Stream.concat(getStyleIds(apiData, collectionId).stream(), derivedStream)
          .collect(ImmutableSet.toImmutableSet());
    }

    return getStyleIds(apiData, collectionId);
  }
}
