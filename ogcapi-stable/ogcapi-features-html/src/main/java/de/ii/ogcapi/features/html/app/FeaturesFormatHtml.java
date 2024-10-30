/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.app;

import static de.ii.ogcapi.features.core.domain.SchemaGeneratorFeatureOpenApi.DEFAULT_FLATTENING_SEPARATOR;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation;
import de.ii.ogcapi.features.core.domain.ItemTypeSpecificConformanceClass;
import de.ii.ogcapi.features.html.domain.FeatureEncoderHtml;
import de.ii.ogcapi.features.html.domain.FeatureTransformationContextHtml;
import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration;
import de.ii.ogcapi.features.html.domain.FeaturesHtmlConfiguration.POSITION;
import de.ii.ogcapi.features.html.domain.ImmutableFeatureTransformationContextHtml;
import de.ii.ogcapi.features.html.domain.ModifiableFeatureCollectionView;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.html.domain.FormatHtml;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.ogcapi.html.domain.MapClient;
import de.ii.ogcapi.html.domain.MapClient.Type;
import de.ii.ogcapi.html.domain.NavigationDTO;
import de.ii.xtraplatform.auth.domain.User;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation.Builder;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import de.ii.xtraplatform.features.domain.transform.WithTransformationsApplied;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.strings.domain.StringTemplateFilters;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import de.ii.xtraplatform.web.domain.Http;
import de.ii.xtraplatform.web.domain.HttpClient;
import de.ii.xtraplatform.web.domain.MustacheRenderer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title HTML
 */
@Singleton
@AutoBind
public class FeaturesFormatHtml extends FeatureFormatExtension
    implements ItemTypeSpecificConformanceClass, FormatHtml {

  private static final WithTransformationsApplied SCHEMA_FLATTENER =
      new WithTransformationsApplied(
          ImmutableMap.of(
              PropertyTransformations.WILDCARD,
              new Builder().flatten(DEFAULT_FLATTENING_SEPARATOR).build()));

  private final Values<Codelist> codelistStore;
  private final I18n i18n;
  private final FeaturesCoreValidation featuresCoreValidator;
  private final URI servicesUri;
  private final MustacheRenderer mustacheRenderer;
  private final HttpClient httpClient;

  @Inject
  public FeaturesFormatHtml(
      ExtensionRegistry extensionRegistry,
      ValueStore valueStore,
      MustacheRenderer mustacheRenderer,
      I18n i18n,
      FeaturesCoreProviders providers,
      FeaturesCoreValidation featuresCoreValidator,
      ServicesContext servicesContext,
      Http http) {
    super(extensionRegistry, providers);
    this.codelistStore = valueStore.forType(Codelist.class);
    this.i18n = i18n;
    this.featuresCoreValidator = featuresCoreValidator;
    this.servicesUri = servicesContext.getUri();
    this.mustacheRenderer = mustacheRenderer;
    this.httpClient = http.getDefaultClient();
  }

  @Override
  public boolean supportsEmbedding() {
    return true;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    ImmutableList.Builder<String> builder = new ImmutableList.Builder<>();

    if (apiData
        .getExtension(FeaturesHtmlConfiguration.class)
        .map(ExtensionConfiguration::isEnabled)
        .orElse(true)) {
      if (isItemTypeUsed(apiData, FeaturesCoreConfiguration.ItemType.feature))
        builder.add("http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/html");
      if (isItemTypeUsed(apiData, FeaturesCoreConfiguration.ItemType.record))
        builder.add("http://www.opengis.net/spec/ogcapi-records-1/0.0/conf/html");
    }
    return builder.build();
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
  public ApiMediaType getCollectionMediaType() {
    return getMediaType();
  }

  @Override
  public boolean canEncodeFeatures() {
    return true;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == MODE.NONE) return ValidationResult.of();

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(api.getData());

    // get HTML configurations to process
    Map<String, FeaturesHtmlConfiguration> htmlConfigurationMap =
        api.getData().getCollections().entrySet().stream()
            .map(
                entry -> {
                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  final FeaturesHtmlConfiguration config =
                      collectionData.getExtension(FeaturesHtmlConfiguration.class).orElse(null);
                  if (Objects.isNull(config)) return null;
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, Collection<String>> keyMap =
        htmlConfigurationMap.entrySet().stream()
            .map(
                entry ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(), entry.getValue().getTransformations().keySet()))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    for (Map.Entry<String, Collection<String>> stringCollectionEntry :
        featuresCoreValidator.getInvalidPropertyKeys(keyMap, featureSchemas).entrySet()) {
      for (String property : stringCollectionEntry.getValue()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "A transformation for property ''{0}'' in collection ''{1}'' is invalid, because the property was not found in the provider schema.",
                property, stringCollectionEntry.getKey()));
      }
    }

    for (Map.Entry<String, FeaturesHtmlConfiguration> entry : htmlConfigurationMap.entrySet()) {
      String collectionId = entry.getKey();
      for (Map.Entry<String, List<PropertyTransformation>> entry2 :
          entry.getValue().getTransformations().entrySet()) {
        String property = entry2.getKey();
        for (PropertyTransformation transformation : entry2.getValue()) {
          builder = transformation.validate(builder, collectionId, property, codelistStore.ids());
        }
      }
    }

    return builder.build();
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FeaturesHtmlConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData)
        && apiData
            .getExtension(HtmlConfiguration.class)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  private boolean getPropertyTooltips(OgcApiDataV2 apiData, boolean isCollection) {
    return apiData
        .getExtension(FeaturesHtmlConfiguration.class)
        .map(cfg -> isCollection ? cfg.getPropertyTooltipsOnItems() : cfg.getPropertyTooltips())
        .orElse(false);
  }

  private boolean getPropertyTooltips(
      OgcApiDataV2 apiData, String collectionId, boolean isCollection) {
    return apiData
        .getExtension(FeaturesHtmlConfiguration.class, collectionId)
        .map(cfg -> isCollection ? cfg.getPropertyTooltipsOnItems() : cfg.getPropertyTooltips())
        .orElse(false);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData
            .getExtension(getBuildingBlockConfigurationType(), collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(false)
        && apiData
            .getExtension(HtmlConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {
    OgcApi api = transformationContext.getApi();
    OgcApiDataV2 apiData = transformationContext.getApiData();
    String staticUrlPrefix = transformationContext.getOgcApiRequest().getStaticUrlPrefix();
    URICustomizer uriCustomizer = transformationContext.getOgcApiRequest().getUriCustomizer();
    Optional<User> user = transformationContext.getOgcApiRequest().getUser();
    ModifiableFeatureCollectionView featureTypeDataset;

    boolean hideMap =
        transformationContext
            .getFeatureSchema()
            .flatMap(
                schema ->
                    schema.getProperties().stream()
                        .filter(FeatureSchema::isPrimaryGeometry)
                        .findFirst())
            .isEmpty();

    if (transformationContext.isQueryExpression()) {
      // Features - Search

      String queryId = transformationContext.getQueryId().orElseThrow();
      featureTypeDataset =
          createQueryExpressionView(
              api,
              queryId,
              transformationContext.getQueryTitle().orElse(queryId),
              transformationContext.getQueryDescription().orElse(null),
              uriCustomizer.copy(),
              staticUrlPrefix,
              language,
              isNoIndexEnabledForApi(apiData),
              getMapPosition(apiData),
              hideMap,
              transformationContext.getQueryTitle().orElse("Search"),
              getPropertyTooltips(apiData, true),
              transformationContext.getLinks(),
              user);
    } else {
      // Features - Core
      String collectionName = transformationContext.getCollectionId();

      if (transformationContext.isFeatureCollection()) {
        FeatureTypeConfigurationOgcApi collectionData =
            apiData.getCollections().get(collectionName);

        Integer htmlMaxLimit =
            collectionData
                .getExtension(FeaturesHtmlConfiguration.class)
                .map(FeaturesHtmlConfiguration::getMaximumPageSize)
                .orElse(null);
        if (Objects.nonNull(htmlMaxLimit) && htmlMaxLimit < transformationContext.getLimit())
          throw new IllegalArgumentException(
              String.format(
                  "The HTML output has a maximum page size (parameter 'limit') of %d. Found: %d",
                  htmlMaxLimit, transformationContext.getLimit()));

        featureTypeDataset =
            createFeatureCollectionView(
                api,
                apiData.getCollections().get(collectionName),
                uriCustomizer.copy(),
                staticUrlPrefix,
                language,
                isNoIndexEnabledForApi(apiData),
                getMapPosition(apiData, collectionName),
                hideMap,
                getPropertyTooltips(apiData, collectionName, true),
                apiData.getLabel(),
                transformationContext.getLinks(),
                apiData.getSubPath(),
                user);
      } else {
        featureTypeDataset =
            createFeatureDetailsView(
                api,
                apiData.getCollections().get(collectionName),
                uriCustomizer.copy(),
                transformationContext.getLinks(),
                apiData.getLabel(),
                uriCustomizer.getLastPathSegment(),
                staticUrlPrefix,
                language,
                isNoIndexEnabledForApi(apiData),
                apiData.getSubPath(),
                getMapPosition(apiData, collectionName),
                hideMap,
                getPropertyTooltips(apiData, collectionName, true),
                user);
      }
    }

    FeatureTransformationContextHtml transformationContextHtml =
        ImmutableFeatureTransformationContextHtml.builder()
            .from(transformationContext)
            .collectionView(featureTypeDataset)
            .codelists(codelistStore.asMap())
            .mustacheRenderer(mustacheRenderer)
            .i18n(i18n)
            .language(language)
            .build();

    return Optional.of(new FeatureEncoderHtml(transformationContextHtml));
  }

  @Override
  public boolean isComplex() {
    return true;
  }

  @Override
  public boolean isForHumans() {
    return true;
  }

  @Override
  public boolean supportsRootConcat() {
    return true;
  }

  private ModifiableFeatureCollectionView createFeatureCollectionView(
      OgcApi api,
      FeatureTypeConfigurationOgcApi featureType,
      URICustomizer uriCustomizer,
      String staticUrlPrefix,
      Optional<Locale> language,
      boolean noIndex,
      POSITION mapPosition,
      boolean hideMap,
      boolean propertyTooltips,
      String apiLabel,
      List<Link> links,
      List<String> subPathToLandingPage,
      Optional<User> user) {
    OgcApiDataV2 apiData = api.getData();
    URI requestUri = null;
    try {
      requestUri = uriCustomizer.build();
    } catch (URISyntaxException e) {
      // ignore
    }

    Optional<HtmlConfiguration> htmlConfig = featureType.getExtension(HtmlConfiguration.class);
    String attribution = apiData.getMetadata().flatMap(ApiMetadata::getAttribution).orElse(null);

    Optional<FeaturesHtmlConfiguration> config =
        featureType.getExtension(FeaturesHtmlConfiguration.class);
    MapClient.Type mapClientType =
        config.map(FeaturesHtmlConfiguration::getMapClientType).orElse(MapClient.Type.MAP_LIBRE);
    String serviceUrl =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(apiData.getSubPath().toArray(String[]::new))
            .toString();
    Optional<String> styleUrl =
        Optional.ofNullable(
            htmlConfig
                .map(
                    cfg ->
                        cfg.getStyle(
                            config.map(FeaturesHtmlConfiguration::getStyle),
                            Optional.of(featureType.getId()),
                            serviceUrl,
                            mapClientType))
                .orElse(null));
    Optional<String> style = Optional.empty();
    if (mapClientType == Type.CESIUM && styleUrl.isPresent()) {
      // TODO we currently use a HTTP request to avoid a dependency to STYLES. Once the
      //  StyleRepository is part of xtraplatform, access the style directly.
      InputStream styleStream = httpClient.getAsInputStream(styleUrl.get());
      try {
        style = Optional.of(new String(styleStream.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
        // ignore
      }
    }
    boolean removeZoomLevelConstraints =
        config.map(FeaturesHtmlConfiguration::getRemoveZoomLevelConstraints).orElse(false);

    String rootTitle = i18n.get("root", language);
    String collectionsTitle = i18n.get("collectionsTitle", language);
    String itemsTitle = i18n.get("itemsTitle", language);

    URICustomizer uriBuilder =
        uriCustomizer.copy().clearParameters().removePathSegment("items", -1);

    return ModifiableFeatureCollectionView.create()
        .setApiData(apiData)
        .setCollectionData(featureType)
        .setSpatialExtent(api.getSpatialExtent(featureType.getId()))
        .setUri(requestUri)
        .setName(featureType.getId())
        .setTitle(featureType.getLabel())
        .setDescription(featureType.getDescription().orElse(null))
        .setRawAttribution(attribution)
        .setUrlPrefix(staticUrlPrefix)
        .setHtmlConfig(htmlConfig.orElse(null))
        .setPersistentUri(Optional.empty())
        .setNoIndex(noIndex)
        .setI18n(i18n)
        .setLanguage(language.orElse(Locale.ENGLISH))
        .setMapPosition(mapPosition)
        .setMapClientType(mapClientType)
        .setStyleUrl(styleUrl.orElse(null))
        .setStyle(style)
        .setRemoveZoomLevelConstraints(removeZoomLevelConstraints)
        .setHideMap(hideMap)
        .setPropertyTooltips(propertyTooltips)
        .setUriCustomizer(uriCustomizer)
        .setBreadCrumbs(
            new ImmutableList.Builder<NavigationDTO>()
                .add(
                    new NavigationDTO(
                        rootTitle,
                        homeUrl(apiData)
                            .orElse(
                                uriBuilder
                                    .copy()
                                    .removeLastPathSegments(2 + subPathToLandingPage.size())
                                    .toString())))
                .add(
                    new NavigationDTO(
                        apiLabel, uriBuilder.copy().removeLastPathSegments(2).toString()))
                .add(
                    new NavigationDTO(
                        collectionsTitle, uriBuilder.copy().removeLastPathSegments(1).toString()))
                .add(new NavigationDTO(featureType.getLabel(), uriBuilder.toString()))
                .add(new NavigationDTO(itemsTitle))
                .build())
        .setRawFormats(
            links.stream()
                .filter(
                    link ->
                        Objects.equals(link.getRel(), "alternate")
                            && !link.getTypeLabel().isBlank())
                .sorted(Comparator.comparing(link -> link.getTypeLabel().toUpperCase()))
                .map(link -> new NavigationDTO(link.getTypeLabel(), link.getHref()))
                .collect(Collectors.toList()))
        .setRawTemporalExtent(api.getTemporalExtent(featureType.getId()))
        .setUser(user);
  }

  private ModifiableFeatureCollectionView createFeatureDetailsView(
      OgcApi api,
      FeatureTypeConfigurationOgcApi featureType,
      URICustomizer uriCustomizer,
      List<Link> links,
      String apiLabel,
      String featureId,
      String staticUrlPrefix,
      Optional<Locale> language,
      boolean noIndex,
      List<String> subPathToLandingPage,
      POSITION mapPosition,
      boolean hideMap,
      boolean propertyTooltips,
      Optional<User> user) {
    OgcApiDataV2 apiData = api.getData();
    String rootTitle = i18n.get("root", language);
    String collectionsTitle = i18n.get("collectionsTitle", language);
    String itemsTitle = i18n.get("itemsTitle", language);

    URI requestUri = null;
    try {
      requestUri = uriCustomizer.build();
    } catch (URISyntaxException e) {
      // ignore
    }
    URICustomizer uriBuilder = uriCustomizer.copy().clearParameters().removeLastPathSegments(1);

    Optional<String> template = featureType.getPersistentUriTemplate();
    String persistentUri = null;
    if (template.isPresent()) {
      // we have a template and need to replace the local feature id
      persistentUri = StringTemplateFilters.applyTemplate(template.get(), featureId);
    }

    Optional<HtmlConfiguration> htmlConfig = featureType.getExtension(HtmlConfiguration.class);
    String attribution = apiData.getMetadata().flatMap(ApiMetadata::getAttribution).orElse(null);

    Optional<FeaturesHtmlConfiguration> config =
        featureType.getExtension(FeaturesHtmlConfiguration.class);
    MapClient.Type mapClientType =
        config.map(FeaturesHtmlConfiguration::getMapClientType).orElse(MapClient.Type.MAP_LIBRE);
    String serviceUrl =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(apiData.getSubPath().toArray(String[]::new))
            .toString();
    Optional<String> styleUrl =
        htmlConfig.map(
            cfg ->
                cfg.getStyle(
                    config.map(FeaturesHtmlConfiguration::getStyle),
                    Optional.of(featureType.getId()),
                    serviceUrl,
                    mapClientType));
    Optional<String> style = Optional.empty();
    if (mapClientType == Type.CESIUM && styleUrl.isPresent()) {
      // TODO we currently use a HTTP request to avoid a dependency to STYLES. Once the
      //  StyleRepository is part of xtraplatform, access the style directly.
      InputStream styleStream = httpClient.getAsInputStream(styleUrl.get());
      try {
        style = Optional.of(new String(styleStream.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
        // ignore
      }
    }
    boolean removeZoomLevelConstraints =
        config.map(FeaturesHtmlConfiguration::getRemoveZoomLevelConstraints).orElse(false);

    List<NavigationDTO> formats =
        links.stream()
            .filter(
                link ->
                    Objects.equals(link.getRel(), "alternate") && !link.getTypeLabel().isBlank())
            .sorted(Comparator.comparing(link -> link.getTypeLabel().toUpperCase()))
            .map(link -> new NavigationDTO(link.getTypeLabel(), link.getHref()))
            .collect(Collectors.toList());

    return ModifiableFeatureCollectionView.create()
        .setApiData(apiData)
        .setCollectionData(featureType)
        .setSpatialExtent(api.getSpatialExtent(featureType.getId()))
        .setUri(requestUri)
        .setUriCustomizer(uriCustomizer)
        .setName(featureType.getId())
        .setTitle(featureType.getLabel())
        .setDescription(featureType.getDescription().orElse(featureType.getLabel()))
        .setRawAttribution(attribution)
        .setUrlPrefix(staticUrlPrefix)
        .setHtmlConfig(htmlConfig.orElse(null))
        .setPersistentUri(Optional.ofNullable(persistentUri))
        .setNoIndex(noIndex)
        .setI18n(i18n)
        .setLanguage(language.orElse(Locale.ENGLISH))
        .setMapPosition(mapPosition)
        .setMapClientType(mapClientType)
        .setStyleUrl(styleUrl.orElse(null))
        .setStyle(style)
        .setRemoveZoomLevelConstraints(removeZoomLevelConstraints)
        .setHideMap(hideMap)
        .setPropertyTooltips(propertyTooltips)
        .setRawTemporalExtent(api.getTemporalExtent(featureType.getId()))
        .setRawFormats(formats)
        .setBreadCrumbs(
            new ImmutableList.Builder<NavigationDTO>()
                .add(
                    new NavigationDTO(
                        rootTitle,
                        homeUrl(apiData)
                            .orElse(
                                uriBuilder
                                    .copy()
                                    .removeLastPathSegments(3 + subPathToLandingPage.size())
                                    .toString())))
                .add(
                    new NavigationDTO(
                        apiLabel, uriBuilder.copy().removeLastPathSegments(3).toString()))
                .add(
                    new NavigationDTO(
                        collectionsTitle, uriBuilder.copy().removeLastPathSegments(2).toString()))
                .add(
                    new NavigationDTO(
                        featureType.getLabel(),
                        uriBuilder.copy().removeLastPathSegments(1).toString()))
                .add(new NavigationDTO(itemsTitle, uriBuilder.toString()))
                .add(new NavigationDTO(featureId))
                .build())
        .setUser(user);
  }

  private ModifiableFeatureCollectionView createQueryExpressionView(
      OgcApi api,
      String id,
      String title,
      String description,
      URICustomizer uriCustomizer,
      String staticUrlPrefix,
      Optional<Locale> language,
      boolean noIndex,
      POSITION mapPosition,
      boolean hideMap,
      String queryLabel,
      boolean propertyTooltips,
      List<Link> links,
      Optional<User> user) {
    OgcApiDataV2 apiData = api.getData();
    URI requestUri = null;
    try {
      requestUri = uriCustomizer.build();
    } catch (URISyntaxException e) {
      // ignore
    }

    Optional<HtmlConfiguration> htmlConfig = apiData.getExtension(HtmlConfiguration.class);
    String attribution = apiData.getMetadata().flatMap(ApiMetadata::getAttribution).orElse(null);

    Optional<FeaturesHtmlConfiguration> config =
        apiData.getExtension(FeaturesHtmlConfiguration.class);
    MapClient.Type mapClientType =
        config.map(FeaturesHtmlConfiguration::getMapClientType).orElse(MapClient.Type.MAP_LIBRE);
    String serviceUrl =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(apiData.getSubPath().toArray(String[]::new))
            .toString();
    String styleUrl =
        htmlConfig
            .map(
                cfg ->
                    cfg.getStyle(
                        config.map(FeaturesHtmlConfiguration::getStyle),
                        Optional.empty(),
                        serviceUrl,
                        mapClientType))
            .orElse(null);
    Optional<String> style = Optional.empty();
    if (mapClientType == Type.CESIUM && Objects.nonNull(styleUrl)) {
      // TODO we currently use a HTTP request to avoid a dependency to STYLES. Once the
      //  StyleRepository is part of xtraplatform, access the style directly.
      InputStream styleStream = httpClient.getAsInputStream(styleUrl);
      try {
        style = Optional.of(new String(styleStream.readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
        // ignore
      }
    }
    boolean removeZoomLevelConstraints =
        config.map(FeaturesHtmlConfiguration::getRemoveZoomLevelConstraints).orElse(false);
    URICustomizer resourceUri = uriCustomizer.copy().clearParameters();

    return ModifiableFeatureCollectionView.create()
        .setFromStoredQuery(true)
        .setFilterEditor(null)
        .setApiData(apiData)
        .setSpatialExtent(api.getSpatialExtent())
        .setUri(requestUri)
        .setName(id)
        .setTitle(title)
        .setDescription(description)
        .setRawAttribution(attribution)
        .setUrlPrefix(staticUrlPrefix)
        .setHtmlConfig(htmlConfig.orElse(null))
        .setPersistentUri(Optional.empty())
        .setNoIndex(noIndex)
        .setI18n(i18n)
        .setLanguage(language.orElse(Locale.ENGLISH))
        .setMapPosition(mapPosition)
        .setMapClientType(mapClientType)
        .setPropertyTooltips(propertyTooltips)
        .setStyleUrl(styleUrl)
        .setStyle(style)
        .setRemoveZoomLevelConstraints(removeZoomLevelConstraints)
        .setHideMap(hideMap)
        .setUriCustomizer(uriCustomizer)
        .setBreadCrumbs(
            new ImmutableList.Builder<NavigationDTO>()
                .add(
                    new NavigationDTO(
                        i18n.get("root", language),
                        resourceUri
                            .copy()
                            .removeLastPathSegments(apiData.getSubPath().size() + 2)
                            .toString()))
                .add(
                    new NavigationDTO(
                        apiData.getLabel(),
                        resourceUri.copy().removeLastPathSegments(2).toString()))
                .add(
                    new NavigationDTO(
                        i18n.get("storedQueriesTitle", language),
                        resourceUri.copy().removeLastPathSegments(1).toString()))
                .add(new NavigationDTO(queryLabel))
                .build())
        .setRawFormats(
            links.stream()
                .filter(
                    link ->
                        Objects.equals(link.getRel(), "alternate")
                            && !link.getTypeLabel().isBlank())
                .sorted(Comparator.comparing(link -> link.getTypeLabel().toUpperCase()))
                .map(link -> new NavigationDTO(link.getTypeLabel(), link.getHref()))
                .collect(Collectors.toList()))
        .setRawTemporalExtent(api.getTemporalExtent())
        .setUser(user);
  }

  private boolean isNoIndexEnabledForApi(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(HtmlConfiguration.class)
        .map(HtmlConfiguration::getNoIndexEnabled)
        .orElse(true);
  }

  private POSITION getMapPosition(OgcApiDataV2 apiData) {
    return apiData
        .getExtension(FeaturesHtmlConfiguration.class)
        .map(FeaturesHtmlConfiguration::getMapPosition)
        .orElse(POSITION.AUTO);
  }

  private POSITION getMapPosition(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getExtension(FeaturesHtmlConfiguration.class, collectionId)
        .map(FeaturesHtmlConfiguration::getMapPosition)
        .orElse(POSITION.AUTO);
  }
}
