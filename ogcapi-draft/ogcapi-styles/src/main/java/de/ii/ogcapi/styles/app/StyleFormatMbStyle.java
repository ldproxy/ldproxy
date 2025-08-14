/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.JsonSchemaExtension;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.URICustomizer;
import de.ii.ogcapi.styles.domain.ImmutableMbStyleStylesheet;
import de.ii.ogcapi.styles.domain.MbStyleLayer;
import de.ii.ogcapi.styles.domain.MbStyleLayer.LayerType;
import de.ii.ogcapi.styles.domain.MbStyleStylesheet;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StyleLayer;
import de.ii.ogcapi.styles.domain.StylesConfiguration;
import de.ii.ogcapi.styles.domain.StylesheetContent;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.services.domain.ServicesContext;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.values.domain.Values;
import io.swagger.v3.oas.models.media.Schema;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Mapbox
 */
@Singleton
@AutoBind
public class StyleFormatMbStyle implements ConformanceClass, StyleFormatExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(StyleFormatMbStyle.class);

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.mapbox.style+json"))
          .label("Mapbox")
          .parameter("mbs")
          .fileExtension("json")
          .build();

  private final URI servicesUri;
  private final Schema<?> schemaStyle;
  private final Map<String, Schema<?>> referencedSchemas;
  private final ExtensionRegistry extensionRegistry;

  @Inject
  public StyleFormatMbStyle(
      ExtensionRegistry extensionRegistry,
      ServicesContext servicesContext,
      ClassSchemaCache classSchemaCache) {
    this.extensionRegistry = extensionRegistry;
    this.servicesUri = servicesContext.getUri();
    this.schemaStyle = classSchemaCache.getSchema(MbStyleStylesheet.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(MbStyleStylesheet.class);
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-styles-1/0.0/conf/mapbox-styles");
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaStyle)
        .schemaRef(MbStyleStylesheet.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public String getFileExtension() {
    return "mbs";
  }

  @Override
  public String getSpecification() {
    return "https://docs.mapbox.com/mapbox-gl-js/style-spec/";
  }

  @Override
  public String getVersion() {
    return "8";
  }

  @Override
  public boolean getAsDefault() {
    return true;
  }

  @Override
  public String getTitle(String styleId, StylesheetContent stylesheetContent) {
    Optional<MbStyleStylesheet> optionalStylesheet = stylesheetContent.getMbStyle();
    return optionalStylesheet.isPresent()
        ? optionalStylesheet.get().getName().orElse(styleId)
        : styleId;
  }

  @Override
  public Object getStyleEntity(
      StylesheetContent stylesheetContent,
      OgcApi api,
      Optional<String> collectionId,
      String styleId,
      Optional<TileMatrixSet> tileMatrixSet,
      ApiRequestContext requestContext) {
    URICustomizer uriCustomizer =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(api.getData().getSubPath().toArray(String[]::new));
    String serviceUrl = uriCustomizer.toString();
    MbStyleStylesheet stylesheet =
        stylesheetContent
            .getMbStyle()
            .map(mbs -> mbs.adjustForTileMatrixSetIfNecessary(tileMatrixSet, serviceUrl))
            .map(mbs -> mbs.replaceParameters(serviceUrl))
            .orElseThrow(NotFoundException::new);

    if (collectionId
        .map(s -> api.getData().getExtension(StylesConfiguration.class, s))
        .orElse(api.getData().getExtension(StylesConfiguration.class))
        .filter(cfg -> Boolean.TRUE.equals(cfg.getAddBoundsToVectorSource()))
        .isPresent()) {
      return stylesheet.addBounds(api.getSpatialExtent(collectionId));
    }
    return stylesheet;
  }

  @Override
  public boolean canDeriveCollectionStyle() {
    return true;
  }

  @Override
  public Optional<StylesheetContent> deriveCollectionStyle(
      StylesheetContent stylesheetContent,
      OgcApiDataV2 apiData,
      String collectionId,
      String styleId) {
    URICustomizer uriCustomizer =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(apiData.getSubPath().toArray(String[]::new));
    String serviceUrl = uriCustomizer.toString();
    Optional<MbStyleStylesheet> mbStyleOriginal =
        stylesheetContent.getMbStyle().map(mbs -> mbs.replaceParameters(serviceUrl));
    if (mbStyleOriginal.isEmpty()
        || mbStyleOriginal.get().getLayers().stream()
            .noneMatch(
                layer ->
                    layer.getSource().isPresent()
                        && layer.getSource().get().equals(apiData.getId())
                        && layer.getSource().isPresent()
                        && layer.getSourceLayer().get().equals(collectionId)))
      return Optional.empty();

    MbStyleStylesheet mbStyleDerived =
        new ImmutableMbStyleStylesheet.Builder()
            .from(mbStyleOriginal.get())
            .layers(
                mbStyleOriginal.get().getLayers().stream()
                    .filter(
                        layer ->
                            layer.getSource().isEmpty()
                                || !layer.getSource().get().equals(apiData.getId())
                                || (layer.getSourceLayer().isEmpty()
                                    || layer.getSourceLayer().get().equals(collectionId)))
                    .collect(Collectors.toUnmodifiableList()))
            .build();

    String descriptor = String.format("%s/%s/%s", apiData.getId(), collectionId, styleId);
    try {
      return Optional.of(
          new StylesheetContent(toBytes(mbStyleDerived), descriptor, true, mbStyleDerived));
    } catch (JsonProcessingException e) {
      LOGGER.error(
          String.format("Could not derive style %s. Reason: %s", descriptor, e.getMessage()));
      return Optional.empty();
    }
  }

  @Override
  public boolean canDeriveMetadata() {
    return true;
  }

  @Override
  public List<StyleLayer> deriveLayerMetadata(
      StylesheetContent stylesheetContent,
      OgcApiDataV2 apiData,
      FeaturesCoreProviders providers,
      Values<Codelist> codelistStore) {
    URICustomizer uriCustomizer =
        new URICustomizer(servicesUri)
            .ensureLastPathSegments(apiData.getSubPath().toArray(String[]::new));
    String serviceUrl = uriCustomizer.toString();
    Optional<MbStyleStylesheet> mbStyle =
        stylesheetContent.getMbStyle().map(mbs -> mbs.replaceParameters(serviceUrl));
    if (mbStyle.isEmpty()) return ImmutableList.of();

    List<JsonSchemaExtension> jsonSchemaExtensions =
        extensionRegistry.getExtensionsForType(JsonSchemaExtension.class).stream()
            .filter(e -> e.isEnabledForApi(apiData))
            .collect(Collectors.toList());

    return mbStyle.get().getLayerMetadata(apiData, providers, codelistStore, jsonSchemaExtensions);
  }

  @Override
  public Optional<String> analyze(StylesheetContent stylesheetContent, boolean strict) {
    MbStyleStylesheet stylesheet =
        stylesheetContent
            .getMbStyle()
            .orElseGet(() -> parse(stylesheetContent.getContent(), strict));

    // TODO add more checks
    if (strict) {
      if (stylesheet.getLayers().isEmpty()) {
        throw new IllegalArgumentException("The Mapbox Style document has no layers.");
      }
      if (stylesheet.getVersion() != 8) {
        throw new IllegalArgumentException(
            "The Mapbox Style document does not have version '8'. Found: "
                + stylesheet.getVersion());
      }
      if (stylesheet.getSources().isEmpty()) {
        throw new IllegalArgumentException("The Mapbox Style document has no sources.");
      }
      List<String> ids = new ArrayList<>();
      for (MbStyleLayer layer : stylesheet.getLayers()) {
        String id = layer.getId();
        if (Objects.isNull(id))
          throw new IllegalArgumentException("A layer in the Mapbox Style document has no id.");

        LayerType type = layer.getType();
        if (Objects.isNull(type))
          throw new IllegalArgumentException(
              String.format("Layer '%s' in the Mapbox Style document has no type.", id));

        if (ids.contains(id)) {
          throw new IllegalArgumentException(
              String.format("Multiple layers in the Mapbox Style document have id '%s'.", id));
        }
        ids.add(id);
      }
    }

    Optional<String> styleIdCandidate = stylesheet.getName();
    if (styleIdCandidate.isPresent()) {
      Pattern styleNamePattern = Pattern.compile("[^\\w\\-]", Pattern.CASE_INSENSITIVE);
      Matcher styleNameMatcher = styleNamePattern.matcher(styleIdCandidate.get());
      if (styleIdCandidate.get().contains(" ") || styleNameMatcher.find()) return Optional.empty();
    }

    return styleIdCandidate;
  }

  private static ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

  private static ObjectMapper STRICT_MAPPER =
      MAPPER.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  static MbStyleStylesheet parse(byte[] content, boolean strict) {
    ObjectMapper mapper = strict ? STRICT_MAPPER : MAPPER;
    try {
      return mapper.readValue(content, MbStyleStylesheet.class);
    } catch (IOException e) {
      throw new IllegalArgumentException("The content of the stylesheet is invalid.", e);
    }
  }

  static byte[] toBytes(MbStyleStylesheet stylesheet) throws JsonProcessingException {
    return MAPPER.writeValueAsBytes(stylesheet);
  }
}
