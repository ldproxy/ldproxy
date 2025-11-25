/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tilematrixsets.domain.TileMatrixSetsConfiguration;
import de.ii.ogcapi.tiles.domain.ImmutableTilesConfiguration.Builder;
import de.ii.ogcapi.tiles.domain.TileFormatExtension;
import de.ii.ogcapi.tiles.domain.TileSetFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesConfiguration.WmtsScope;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import de.ii.ogcapi.tiles.infra.EndpointVectorTileCollection;
import de.ii.ogcapi.tiles.infra.EndpointVectorTileDataset;
import de.ii.ogcapi.tiles.infra.EndpointVectorTileSetCollection;
import de.ii.ogcapi.tiles.infra.EndpointVectorTileSetDataset;
import de.ii.ogcapi.tiles.infra.EndpointVectorTileSetsCollection;
import de.ii.ogcapi.tiles.infra.EndpointVectorTileSetsDataset;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.entities.domain.EntityFactories;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.tiles.domain.TileProviderData;
import de.ii.xtraplatform.tiles.domain.TilesFormat;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Tiles
 * @langEn Publish geographic data as tiles.
 * @langDe Veröffentlichen von Geodaten als Kacheln.
 * @scopeEn This building block supports tiles derived from feature data or tiles that are provided
 *     by an external source.
 *     <p>The supported tile formats are:
 *     <p><code>
 * - MVT (Mapbox Vector Tile)
 * - PNG
 * - WebP
 * - JPEG
 * - TIFF
 *     </code>
 *     <p>For tiles that are derived from feature data, only Mapbox Vector Tiles are supported as a
 *     file format plus PNG map tiles derived from these vector tiles using one or more MapLibre
 *     styles.
 *     <p>All tiles of an API are sourced from a single tile provider.
 * @scopeDe Dieser Baustein unterstützt Kacheln, die aus Features abgeleitet sind, oder Kacheln, die
 *     von einer externen Quelle bereitgestellt werden.
 *     <p>Die unterstützten Kachelformate sind:
 *     <p><code>
 * - MVT (Mapbox Vector Tile)
 * - PNG
 * - WebP
 * - JPEG
 * - TIFF
 *     </code>
 *     <p>Für Kacheln, die aus Features abgeleitet werden, wird nur Mapbox Vector Tiles als
 *     Kachelformat unterstützt sowie PNG-Kartenkacheln, die aus diesen Vektorkacheln unter
 *     Verwendung eines oder mehrerer MapLibre Styles abgeleitet wurden.
 *     <p>Alle Kacheln einer API kommen vom selben Tile-Provider.
 * @cfgFilesEn If the `style` configuration option is set to a custom style, the stylesheet file
 *     must be placed in the value store as `maplibre-styles/{apiId}/{styleId}.json`, if
 *     `mapClientType` is `MAP_LIBRE`. The stylesheet must be a MapLibre style. If `mapClientType`
 *     is `CESIUM`, the stylesheet file must be placed in the value store as
 *     `3dtiles-styles/{apiId}/building/{styleId}.json`, and must be a 3DTiles style. `{styleId}`
 *     must be the value of the `style` configuration option, `{apiId}` must be the `id` of the
 *     service.
 *     <p>The tile cache is located in the
 *     [Store](../../application/20-configuration/10-store-new.md) as resource with type `tiles` and
 *     relative path `{apiId}/`. The contents of the cache directory depend on the type and storage
 *     of the cache in the tile provider.
 *     <p>If the data for an API or tile configuration has been changed, then the cache directory
 *     for the API should be deleted so that the cache is rebuilt with the updated data or rules.
 * @cfgFilesDe Wenn die Konfigurationsoption `style` auf einen spezifischen Style gesetzt wird, muss
 *     die Stylesheet-Datei im Value Store unter `maplibre-styles/{apiId}/{styleId}.json` abgelegt
 *     werden, wenn `mapClientType` auf `MAP_LIBRE` gesetzt ist. Das Stylesheet muss ein
 *     MapLibre-Style sein. Wenn `mapClientType` auf `CESIUM` gesetzt ist, muss die Stylesheet-Datei
 *     im Value Store unter `3dtiles-styles/{apiId}/building/{styleId}.json` abgelegt werden und ein
 *     3DTiles-Style sein. `{styleId}` muss dem Wert der Konfigurationsoption `style` entsprechen,
 *     `{apiId}` muss die `id` des Dienstes sein.
 *     <p>Der Tile-Cache liegt im [Store](../../application/20-configuration/10-store-new.md) als
 *     Ressource mit dem Typ `tiles` und dem relativen Pfad `{apiId}/`. Die Inhalte des
 *     Cache-Verzeichnisses hängen von der Art und Speicherung des Caches im Tile-Provider ab.
 *     <p>Wenn die Daten zu einer API oder Kachelkonfiguration geändert wurden, dann sollte das
 *     Cache-Verzeichnis für die API gelöscht werden, damit der Cache mit den aktualisierten Daten
 *     oder Regeln neu aufgebaut wird.
 * @conformanceEn The building block implements the conformance classes "Core", "TileSet", "TileSets
 *     List", "Dataset TileSets", "GeoData TileSets", "Collections Selection", "DateTime", "OpenAPI
 *     Specification 3.0 API definition", "Mapbox Vector Tiles", "PNG", "JPEG", and "TIFF" of the
 *     [OGC API - Tiles - Part 1: Core 1.0 Standard](https://docs.ogc.org/is/20-057/20-057.html) and
 *     the conformance classes "TileSetMetadata", "TileMatrixSetLimits", and
 *     "JSONTileMatrixSetLimits" of the [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata
 *     2.0 Standard](https://docs.ogc.org/is/17-083r4/17-083r4.html).
 * @conformanceDe Der Baustein implementiert die Konformitätsklassen "Core", "TileSet", "TileSets
 *     List", "Dataset TileSets", "GeoData TileSets", "Collections Selection", "DateTime", "OpenAPI
 *     Specification 3.0 API definition", "Mapbox Vector Tiles", "PNG", "JPEG" und "TIFF" des
 *     Standards [OGC API - Tiles - Part 1: Core 1.0](https://docs.ogc.org/is/20-057/20-057.html)
 *     und die Konformitätsklassen "TileSetMetadata", "TileMatrixSetLimits" und
 *     "JSONTileMatrixSetLimits" des Standards [OGC Two Dimensional Tile Matrix Set and Tile Set
 *     Metadata 2.0](https://docs.ogc.org/is/17-083r4/17-083r4.html).
 * @ref:cfg {@link de.ii.ogcapi.tiles.domain.TilesConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.tiles.domain.ImmutableTilesConfiguration}
 * @ref:endpoints {@link EndpointVectorTileSetsDataset}, {@link EndpointVectorTileSetDataset},
 *     {@link EndpointVectorTileDataset}, {@link EndpointVectorTileSetsCollection}, {@link
 *     EndpointVectorTileSetCollection}, {@link EndpointVectorTileCollection}
 * @ref:queryParameters {@link QueryParameterCollections}, {@link QueryParameterDatetimeTile},
 *     {@link QueryParameterFTile}, {@link QueryParameterFTileCollection}, {@link
 *     QueryParameterFTileSet}, {@link QueryParameterFTileSets}, {@link QueryParameterLimitTile}
 * @ref:pathParameters {@link PathParameterCollectionIdTiles}, {@link PathParameterTileMatrixSetId},
 *     {@link PathParameterTileMatrix}, {@link PathParameterTileRow}, {@link PathParameterTileCol}
 */
@Singleton
@AutoBind
public class TilesBuildingBlock implements ApiBuildingBlock, ApiExtensionHealth {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/20-057/20-057.html", "OGC API - Tiles - Part 1: Core"));
  private static final Logger LOGGER = LoggerFactory.getLogger(TilesBuildingBlock.class);
  public static final String DATASET_TILES = "__all__";

  private final ExtensionRegistry extensionRegistry;
  private final TilesProviders tilesProviders;
  private final EntityFactories entityFactories;

  @Inject
  public TilesBuildingBlock(
      ExtensionRegistry extensionRegistry,
      TilesProviders tilesProviders,
      EntityFactories entityFactories) {
    this.extensionRegistry = extensionRegistry;
    this.tilesProviders = tilesProviders;
    this.entityFactories = entityFactories;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {

    return new Builder()
        .enabled(false)
        .tileSetEncodings(
            extensionRegistry.getExtensionsForType(TileSetFormatExtension.class).stream()
                .filter(FormatExtension::isEnabledByDefault)
                .map(format -> format.getMediaType().label())
                .sorted()
                .collect(ImmutableList.toImmutableList()))
        .style("DEFAULT")
        .wmts(WmtsScope.NONE)
        .build();
  }

  @Override
  public <T extends ExtensionConfiguration> T hydrateConfiguration(T cfg) {
    if (cfg instanceof TilesConfiguration) {
      TilesConfiguration config = (TilesConfiguration) cfg;
      Map<String, List<PropertyTransformation>> transformations =
          config.extendWithFlattenIfMissing();

      if (Objects.equals(transformations, config.getTransformations())) {
        return (T) config;
      }

      return (T) new Builder().from(config).transformations(transformations).build();
    }

    return cfg;
  }

  @Override
  public int getStartupPriority() {
    return 10;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    // since building block / capability components are currently always enabled,
    // we need to test, if the TILES and TILE MATRIX SETS building blocks are enabled for the API
    // and stop,
    // if not
    OgcApiDataV2 apiData = api.getData();

    if (!apiData
        .getExtension(TileMatrixSetsConfiguration.class)
        .map(ExtensionConfiguration::isEnabled)
        .orElse(false)) {
      return ValidationResult.of();
    }

    Optional<TilesConfiguration> tilesConfiguration =
        apiData.getExtension(TilesConfiguration.class).filter(ExtensionConfiguration::isEnabled);

    if (tilesConfiguration.isEmpty()) {
      return ValidationResult.of();
    }
    if (!tilesProviders.hasTileProvider(apiData)) {
      if (Objects.nonNull(tilesConfiguration.get().getTileProvider())) {
        throw new IllegalStateException(
            String.format(
                "Tile provider with id '%s' not found.",
                tilesConfiguration.get().getTileProvider()));

      } else {
        boolean tilesIdExists =
            entityFactories.getAll("providers").stream()
                .anyMatch(
                    entityFactory ->
                        TileProviderData.class.isAssignableFrom(entityFactory.dataClass())
                            && entityFactory
                                .instance(TilesProviders.toTilesId(apiData.getId()))
                                .isPresent());
        if (tilesIdExists) {
          throw new IllegalStateException(
              String.format(
                  "Tile provider with id '%s' not found.",
                  TilesProviders.toTilesId(apiData.getId())));
        }
      }
    }

    if (apiValidation == MODE.NONE) {
      return ValidationResult.of();
    }

    return validate(apiData, apiValidation);
  }

  public ValidationResult validate(OgcApiDataV2 apiData, MODE apiValidation) {

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    for (Map.Entry<String, TilesConfiguration> entry :
        apiData.getCollections().entrySet().stream()
            .filter(
                entry ->
                    entry.getValue().getEnabled()
                        && entry.getValue().getExtension(TilesConfiguration.class).isPresent())
            .map(
                entry ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(),
                        entry.getValue().getExtension(TilesConfiguration.class).get()))
            .filter(entry -> entry.getValue().isEnabled())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            .entrySet()) {
      String collectionId = entry.getKey();
      TilesConfiguration config = entry.getValue();

      List<String> formatLabels =
          extensionRegistry.getExtensionsForType(TileFormatExtension.class).stream()
              .filter(formatExtension -> formatExtension.isEnabledForApi(apiData))
              .map(format -> format.getMediaType().label())
              .collect(Collectors.toUnmodifiableList());
      Set<TilesFormat> tileEncodings =
          tilesProviders
              .getTilesetMetadataOrThrow(apiData, apiData.getCollectionData(collectionId))
              .getEncodings();
      if (Objects.isNull(tileEncodings)) {
        builder.addStrictErrors(
            MessageFormat.format(
                "No tile encoding has been specified in the TILES module configuration of collection ''{0}''.",
                collectionId));
      } else {
        for (TilesFormat encoding : tileEncodings) {
          if (!formatLabels.contains(encoding.name())) {
            builder.addStrictErrors(
                MessageFormat.format(
                    "The tile encoding ''{0}'' is specified in the TILES module configuration of collection ''{1}'', but the format does not exist.",
                    encoding, collectionId));
          }
        }
      }

      formatLabels =
          extensionRegistry.getExtensionsForType(TileSetFormatExtension.class).stream()
              .filter(formatExtension -> formatExtension.isEnabledForApi(apiData))
              .map(format -> format.getMediaType().label())
              .collect(Collectors.toUnmodifiableList());
      for (String encoding : config.getTileSetEncodings()) {
        if (!formatLabels.contains(encoding)) {
          builder.addStrictErrors(
              MessageFormat.format(
                  "The tile set encoding ''{0}'' is specified in the TILES module configuration of collection ''{1}'', but the format does not exist.",
                  encoding, collectionId));
        }
      }
    }

    return builder.build();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(tilesProviders.getTileProviderOrThrow(apiData));
  }
}
