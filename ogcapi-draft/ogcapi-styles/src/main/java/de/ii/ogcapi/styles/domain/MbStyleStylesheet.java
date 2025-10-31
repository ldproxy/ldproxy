/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.JsonSchemaExtension;
import de.ii.ogcapi.features.core.domain.JsonSchemaGeometry;
import de.ii.ogcapi.features.core.domain.JsonSchemaObject;
import de.ii.ogcapi.foundation.domain.ImmutableLink;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.styles.app.AdjustZoomLevels;
import de.ii.ogcapi.styles.app.SchemaCacheStyleLayer;
import de.ii.ogcapi.styles.domain.MbStyleLayer.LayerType;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import de.ii.xtraplatform.values.domain.AutoValue;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.ValueEncoding.FORMAT;
import de.ii.xtraplatform.values.domain.Values;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore.FormatAlias;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    jdkOnly = true,
    builder = "new",
    deepImmutablesDetection = true,
    passAnnotations = {FromValueStore.class})
@FromValueStore(
    type = "maplibre-styles",
    defaultFormat = FORMAT.JSON,
    formatAliases = {@FormatAlias(extension = "mbs", format = FORMAT.JSON)})
@JsonDeserialize(builder = ImmutableMbStyleStylesheet.Builder.class)
public abstract class MbStyleStylesheet implements StoredValue, AutoValue {

  public static final String SCHEMA_REF = "#/components/schemas/MbStyleStylesheet";

  public enum Visibility {
    visible,
    none
  }

  public abstract int getVersion();

  public abstract Optional<String> getName();

  public abstract Optional<Map<String, Object>> getMetadata();

  public abstract Optional<List<Double>> getCenter();

  public abstract Optional<Double> getZoom();

  @Value.Default
  public Double getBearing() {
    return 0.0;
  }

  @Value.Default
  public Double getPitch() {
    return 0.0;
  }

  public abstract Optional<MbStyleLight> getLight();

  public abstract Map<String, MbStyleSource> getSources();

  public abstract Optional<String> getSprite();

  public abstract Optional<String> getGlyphs();

  public abstract Optional<MbStyleTransition> getTransition();

  public abstract List<MbStyleLayer> getLayers();

  public abstract Optional<MbStyleTerrain> getTerrain();

  // TODO: replace with SchemaDeriverStyleLayer
  @JsonIgnore
  public List<StyleLayer> getLayerMetadata(
      OgcApiDataV2 apiData,
      FeaturesCoreProviders providers,
      Values<Codelist> codelistStore,
      List<JsonSchemaExtension> jsonSchemaExtensions) {
    // prepare a map with the JSON schemas of the feature collections used in the style
    JsonSchemaCache schemas = new SchemaCacheStyleLayer(codelistStore::asMap);

    Map<String, JsonSchemaObject> schemaMap =
        getLayers().stream()
            .filter(
                layer ->
                    layer.getSource().isPresent()
                        && layer.getSource().get().equals(apiData.getId()))
            .map(layer -> layer.getSourceLayer())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .filter(sourceLayer -> apiData.getCollections().containsKey(sourceLayer))
            .map(
                collectionId -> {
                  Optional<FeatureSchema> schema =
                      providers.getFeatureSchema(
                          apiData, apiData.getCollections().get(collectionId));
                  if (schema.isEmpty()) return null;
                  return new AbstractMap.SimpleImmutableEntry<>(
                      collectionId,
                      schemas.getSchema(
                          schema.get(),
                          apiData,
                          apiData.getCollections().get(collectionId),
                          List.of(),
                          Optional.empty(),
                          jsonSchemaExtensions));
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    return getLayers().stream()
        .map(
            layer -> {
              ImmutableStyleLayer.Builder builder = ImmutableStyleLayer.builder().id(layer.getId());

              Map<String, Object> md = layer.getMetadata();
              if (md.containsKey("description"))
                builder.description(md.get("description").toString());

              Optional<String> apiId = layer.getSource();
              Optional<String> collectionId = layer.getSourceLayer();

              final boolean knownSource =
                  apiId.isPresent()
                      && apiId.get().equals(apiData.getId())
                      && collectionId.isPresent()
                      && schemaMap.containsKey(collectionId.get());
              final JsonSchemaObject properties =
                  knownSource ? schemaMap.get(collectionId.get()) : null;

              ImmutableSet.Builder<String> attNamesBuilder = ImmutableSet.builder();
              attNamesBuilder.addAll(getAttributes(layer.getFilter()));
              layer
                  .getLayout()
                  .values()
                  .forEach(value -> attNamesBuilder.addAll(getAttributes(value)));
              layer
                  .getPaint()
                  .values()
                  .forEach(value -> attNamesBuilder.addAll(getAttributes(value)));
              Set<String> attNames = attNamesBuilder.build();

              builder.attributes(
                  attNames.stream()
                      .sorted()
                      .map(
                          attName -> {
                            if (Objects.nonNull(properties)) {
                              if (properties.getProperties().containsKey(attName))
                                return new SimpleImmutableEntry<>(
                                    attName, properties.getProperties().get(attName));
                              return properties.getPatternProperties().entrySet().stream()
                                  .filter(entry -> attName.matches(entry.getKey()))
                                  .map(
                                      entry ->
                                          new SimpleImmutableEntry<>(attName, entry.getValue()))
                                  .findAny()
                                  .orElse(null);
                            }
                            return null;
                          })
                      .filter(Objects::nonNull)
                      .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue)));

              JsonSchema geometry =
                  properties != null
                      ? properties.getProperties().values().stream()
                          .filter(
                              property ->
                                  property.getRole().filter("primary-geometry"::equals).isPresent())
                          .findFirst()
                          .orElse(null)
                      : null;
              if (Objects.nonNull(geometry) && geometry instanceof JsonSchemaGeometry) {
                String geomAsString = ((JsonSchemaGeometry) geometry).getFormat();
                boolean point =
                    "geometry-point".equals(geomAsString)
                        || "geometry-multipoint".equals(geomAsString)
                        || "geometry-point-or-multipoint".equals(geomAsString);
                boolean line =
                    "geometry-linestring".equals(geomAsString)
                        || "geometry-multilinestring".equals(geomAsString)
                        || "geometry-linestring-or-multilinestring".equals(geomAsString);
                boolean polygon =
                    "geometry-polygon".equals(geomAsString)
                        || "geometry-multipolygon".equals(geomAsString)
                        || "geometry-polygon-or-multipolygon".equals(geomAsString);
                if (point && !line && !polygon) builder.type("point");
                else if (!point && line && !polygon) builder.type("line");
                else if (!point && !line && polygon) builder.type("polygon");
                else builder.type("geometry");
              } else if (layer.getType().toString().matches("fill|line|symbol|fill\\-extrusion")) {
                builder.type("geometry");
              } else if (layer.getType() == LayerType.circle) {
                builder.type("point");
              } else if (layer.getType() == LayerType.raster) {
                builder.type("raster");
              }

              if (knownSource) {
                builder.sampleData(
                    new ImmutableLink.Builder()
                        .rel("start")
                        .title("")
                        .href(
                            String.format("{serviceUrl}/collections/%s/items", collectionId.get()))
                        .templated(true)
                        .build());
              }

              return builder.build();
            })
        .collect(Collectors.toUnmodifiableList());
  }

  @JsonIgnore
  private Set<String> getAttributes(Object expression) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    if (expression instanceof Optional) {
      if (((Optional) expression).isEmpty()) return ImmutableSet.of();
      expression = ((Optional) expression).get();
    }
    if (expression instanceof Iterable) {
      Iterator it = ((Iterable) expression).iterator();
      if (it.hasNext()) {
        Object obj = it.next();
        if (obj instanceof String
            && ImmutableList.of("get", "has", "in", "==", "!=", "<", ">", "<=", ">=")
                .contains((String) obj)
            && it.hasNext()) {
          obj = it.next();
          if (obj instanceof String) {
            builder.add((String) obj);
          }
        } else {
          while (it.hasNext()) {
            builder.addAll(getAttributes(it.next()));
          }
        }
      }
    }
    return builder.build();
  }

  @JsonIgnore
  public MbStyleStylesheet addBounds(Optional<BoundingBox> optionalBbox) {
    return optionalBbox
        .flatMap(
            bbox ->
                this.getSources().entrySet().stream()
                    .filter(
                        entry ->
                            entry.getValue() instanceof MbStyleVectorSource
                                && ((MbStyleVectorSource) entry.getValue()).getBounds().isEmpty())
                    .findFirst()
                    .map(
                        entry ->
                            (MbStyleStylesheet)
                                new ImmutableMbStyleStylesheet.Builder()
                                    .from(this)
                                    .center(this.getCenter().orElse(computeCenter(bbox)))
                                    .putSources(
                                        entry.getKey(),
                                        ImmutableMbStyleVectorSource.builder()
                                            .from((MbStyleVectorSource) entry.getValue())
                                            .bounds(
                                                ImmutableList.of(
                                                    bbox.getXmin(),
                                                    bbox.getYmin(),
                                                    bbox.getXmax(),
                                                    bbox.getYmax()))
                                            .build())
                                    .build()))
        .orElse(this);
  }

  @JsonIgnore
  public MbStyleStylesheet adjustForTileMatrixSetIfNecessary(
      Optional<TileMatrixSet> tileMatrixSet, String serviceUrl) {
    if (tileMatrixSet.isEmpty() || "WebMercatorQuad".equals(tileMatrixSet.get().getId())) {
      return this;
    }

    if (!tileMatrixSet.get().isQuadTree()) {
      throw new IllegalArgumentException(
          "This operation is not supported for the tile matrix set: "
              + tileMatrixSet.get().getId());
    }

    String tmsId = tileMatrixSet.get().getId();
    BigDecimal scaleDenominatorLevel0WebMercatorQuad = BigDecimal.valueOf(559082264.028717);
    BigDecimal scaleDenominatorLevel0Target =
        BigDecimal.valueOf(tileMatrixSet.get().getInitialScaleDenominator());
    BigDecimal ratio =
        scaleDenominatorLevel0WebMercatorQuad
            .divide(scaleDenominatorLevel0Target, MathContext.DECIMAL128)
            .setScale(5, RoundingMode.HALF_UP);
    double adjustment = Math.log(ratio.doubleValue()) / Math.log(2);
    Function<Double, Double> adjustZoomLevel =
        zoomLevel ->
            Math.min(
                tileMatrixSet.get().getMaxLevel(),
                Math.max(tileMatrixSet.get().getMinLevel(), zoomLevel - adjustment));
    AdjustZoomLevels adjustZoomLevels = new AdjustZoomLevels(adjustZoomLevel);

    return new ImmutableMbStyleStylesheet.Builder()
        .from(this)
        .zoom(getZoom().map(adjustZoomLevel))
        .metadata(
            getMetadata()
                .map(
                    md ->
                        md.entrySet().stream()
                            .filter(entry -> !entry.getKey().equals("ldproxy:layerControl"))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))))
        .sources(
            getSources().entrySet().stream()
                .map(
                    entry -> {
                      MbStyleSource source = entry.getValue();
                      if (source instanceof MbStyleVectorSource) {
                        return Map.entry(
                            entry.getKey(),
                            ImmutableMbStyleVectorSource.copyOf((MbStyleVectorSource) source)
                                .withUrl(
                                    ((MbStyleVectorSource) source)
                                        .getUrl()
                                        .map(url -> adjustUrl(url, tmsId, serviceUrl)))
                                .withTiles(
                                    ((MbStyleVectorSource) source)
                                        .getTiles().stream()
                                            .map(tileUri -> adjustUrl(tileUri, tmsId, serviceUrl))
                                            .collect(Collectors.toList()))
                                .withMinzoom(
                                    ((MbStyleVectorSource) source)
                                        .getMinzoom()
                                        .map(v -> adjustZoomLevel.apply(v.doubleValue())))
                                .withMaxzoom(
                                    ((MbStyleVectorSource) source)
                                        .getMaxzoom()
                                        .map(v -> adjustZoomLevel.apply(v.doubleValue()))));
                      } else if (source instanceof MbStyleRasterSource) {
                        return Map.entry(
                            entry.getKey(),
                            ImmutableMbStyleRasterSource.copyOf((MbStyleRasterSource) source)
                                .withUrl(
                                    ((MbStyleRasterSource) source)
                                        .getUrl()
                                        .map(url -> adjustUrl(url, tmsId, serviceUrl)))
                                .withTiles(
                                    ((MbStyleRasterSource) source)
                                        .getTiles().orElse(ImmutableList.of()).stream()
                                            .map(tileUri -> adjustUrl(tileUri, tmsId, serviceUrl))
                                            .collect(Collectors.toList()))
                                .withMinzoom(
                                    ((MbStyleRasterSource) source)
                                        .getMinzoom()
                                        .map(v -> adjustZoomLevel.apply(v.doubleValue())))
                                .withMaxzoom(
                                    ((MbStyleRasterSource) source)
                                        .getMaxzoom()
                                        .map(v -> adjustZoomLevel.apply(v.doubleValue()))));
                      }
                      return Map.entry(entry.getKey(), entry.getValue());
                    })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
        .layers(
            getLayers().stream()
                .map(
                    l ->
                        ImmutableMbStyleLayer.copyOf(l)
                            .withMinzoom(
                                l.getMinzoom().map(v -> adjustZoomLevel.apply(v.doubleValue())))
                            .withMaxzoom(
                                l.getMaxzoom().map(v -> adjustZoomLevel.apply(v.doubleValue())))
                            .withLayout(
                                l.getLayout().entrySet().stream()
                                    .map(
                                        entry ->
                                            Map.entry(
                                                entry.getKey(),
                                                entry.getValue().accept(adjustZoomLevels)))
                                    .collect(
                                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                            .withPaint(
                                l.getPaint().entrySet().stream()
                                    .map(
                                        entry ->
                                            Map.entry(
                                                entry.getKey(),
                                                entry.getValue().accept(adjustZoomLevels)))
                                    .collect(
                                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                            .withFilter(l.getFilter().map(f -> f.accept(adjustZoomLevels))))
                .toList())
        .build();
  }

  private static String adjustUrl(String url, String tmsId, String serviceUrl) {
    // includes special handling for some commonly used basemap WMTS sources in Germany
    if (url.startsWith("{serviceUrl}") || url.startsWith(serviceUrl)) {
      return url.replace("WebMercatorQuad", tmsId);
    } else if (url.startsWith("https://sgx.geodatenzentrum.de/wmts_basemapde")
        && tmsId.equals("AdV_25832")) {
      return url.replace("GLOBAL_WEBMERCATOR", "DE_EPSG_25832_ADV");
    } else if (url.startsWith("https://sgx.geodatenzentrum.de/wmts_basemapde")
        && tmsId.equals("AdV_25833")) {
      return url.replace("GLOBAL_WEBMERCATOR", "DE_EPSG_25833_ADV");
    } else if (url.startsWith("https://sg.geodatenzentrum.de/wmts_topplus_open")
        && tmsId.equals("EU_25832")) {
      return url.replace("GLOBAL_WEBMERCATOR", "EU_EPSG_25832_TOPPLUS");
    }
    return url;
  }

  private static List<Double> computeCenter(BoundingBox bbox) {
    double centerX = (bbox.getXmin() + bbox.getXmax()) / 2;
    double centerY = (bbox.getYmin() + bbox.getYmax()) / 2;
    return ImmutableList.of(centerX, centerY);
  }

  @JsonIgnore
  public MbStyleStylesheet replaceParameters(String serviceUrl) {
    // any template parameters in links?
    boolean templated =
        this.getSprite().orElse("").matches("^.*\\{serviceUrl\\}.*$")
            || this.getGlyphs().orElse("").matches("^.*\\{serviceUrl\\}.*$")
            || this.getSources().values().stream()
                .filter(
                    source ->
                        source instanceof MbStyleVectorSource
                            || source instanceof MbStyleRasterSource
                            || source instanceof MbStyleGeojsonSource)
                .anyMatch(
                    source ->
                        (source instanceof MbStyleVectorSource
                                && (((MbStyleVectorSource) source)
                                        .getTiles().stream()
                                            .anyMatch(
                                                tilesUri ->
                                                    tilesUri.matches("^.*\\{serviceUrl\\}.*$"))
                                    || ((MbStyleVectorSource) source)
                                        .getUrl()
                                        .orElse("")
                                        .matches("^.*\\{serviceUrl\\}.*$")))
                            || (source instanceof MbStyleRasterSource
                                && (((MbStyleRasterSource) source)
                                        .getTiles().orElse(ImmutableList.of()).stream()
                                            .anyMatch(
                                                tilesUri ->
                                                    tilesUri.matches("^.*\\{serviceUrl\\}.*$"))
                                    || ((MbStyleRasterSource) source)
                                        .getUrl()
                                        .orElse("")
                                        .matches("^.*\\{serviceUrl\\}.*$")))
                            || (source instanceof MbStyleGeojsonSource
                                && (((MbStyleGeojsonSource) source)
                                    .getData()
                                    .filter(data -> data instanceof String)
                                    .map(data -> (String) data)
                                    .orElse("")
                                    .matches("^.*\\{serviceUrl\\}.*$"))));
    if (!templated) return this;

    return new ImmutableMbStyleStylesheet.Builder()
        .from(this)
        .sprite(
            this.getSprite().isPresent()
                ? Optional.of(this.getSprite().get().replace("{serviceUrl}", serviceUrl))
                : Optional.empty())
        .glyphs(
            this.getGlyphs().isPresent()
                ? Optional.of(this.getGlyphs().get().replace("{serviceUrl}", serviceUrl))
                : Optional.empty())
        .sources(
            this.getSources().entrySet().stream()
                .collect(
                    Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> {
                          MbStyleSource source = entry.getValue();
                          if (source instanceof MbStyleVectorSource)
                            return ImmutableMbStyleVectorSource.builder()
                                .from((MbStyleVectorSource) source)
                                .url(
                                    ((MbStyleVectorSource) source)
                                        .getUrl()
                                        .map(url -> url.replace("{serviceUrl}", serviceUrl)))
                                .tiles(
                                    ((MbStyleVectorSource) source)
                                        .getTiles().stream()
                                            .map(tile -> tile.replace("{serviceUrl}", serviceUrl))
                                            .collect(Collectors.toList()))
                                .build();
                          else if (source instanceof MbStyleRasterSource)
                            return ImmutableMbStyleRasterSource.builder()
                                .from((MbStyleRasterSource) source)
                                .url(
                                    ((MbStyleRasterSource) source)
                                        .getUrl()
                                        .map(url -> url.replace("{serviceUrl}", serviceUrl)))
                                .tiles(
                                    ((MbStyleRasterSource) source)
                                        .getTiles().orElse(ImmutableList.of()).stream()
                                            .map(tile -> tile.replace("{serviceUrl}", serviceUrl))
                                            .collect(Collectors.toList()))
                                .build();
                          else if (source instanceof MbStyleGeojsonSource
                              && ((MbStyleGeojsonSource) source).getData().isPresent()
                              && ((MbStyleGeojsonSource) source).getData().get()
                                  instanceof String) {
                            return ImmutableMbStyleGeojsonSource.builder()
                                .from((MbStyleGeojsonSource) source)
                                .data(
                                    ((MbStyleGeojsonSource) source)
                                        .getData()
                                        .map(data -> (String) data)
                                        .map(data -> data.replace("{serviceUrl}", serviceUrl)))
                                .build();
                          }

                          return source;
                        })))
        .build();
  }

  public abstract static class Builder implements ValueBuilder<MbStyleStylesheet> {}
}
