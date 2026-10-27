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
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiPathParameter;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title styleId
 * @endpoints Dataset Tilesets, Dataset Tileset, Dataset Tile, Collection Tilesets, Collection
 *     Tileset, Collection Tile
 * @langEn The local identifier of the resource.
 * @langDe Der lokale Identifikator der Dateiressource.
 */
@Singleton
@AutoBind
public class PathParameterStyleId implements OgcApiPathParameter {

  private static final Logger LOGGER = LoggerFactory.getLogger(PathParameterStyleId.class);

  public static final String STYLE_ID_PATTERN = "[^/]+";

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();
  protected final SchemaValidator schemaValidator;
  private final TilesProviders tilesProviders;

  @Inject
  PathParameterStyleId(SchemaValidator schemaValidator, TilesProviders tilesProviders) {
    this.schemaValidator = schemaValidator;
    this.tilesProviders = tilesProviders;
  }

  @Override
  public String getPattern() {
    return STYLE_ID_PATTERN;
  }

  // only styles with map tiles in the tile provider, of the dataset or of a collection
  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return Stream.concat(
            tilesProviders.getRasterTilesetMetadata(apiData).keySet().stream(),
            apiData.getCollections().values().stream()
                .flatMap(
                    collectionData ->
                        tilesProviders
                            .getRasterTilesetMetadata(apiData, collectionData)
                            .keySet()
                            .stream()))
        .distinct()
        .sorted()
        .toList();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    Schema<?> schema = schemaMap.get(apiData.hashCode());
    if (Objects.nonNull(schema)) {
      return schema;
    }
    List<String> values = getValues(apiData);
    schema = new StringSchema()._enum(ImmutableList.copyOf(values));
    // the styles are only known once the tile provider is available
    if (!values.isEmpty()) {
      schemaMap.put(apiData.hashCode(), schema);
    }
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  // the values differ from the parameter of the styles, so it is a separate parameter in the API
  // definition
  @Override
  public String getId() {
    return "styleIdMapTiles";
  }

  @Override
  public String getName() {
    return "styleId";
  }

  @Override
  public String getDescription() {
    return "The local identifier of a style, unique within the API.";
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath) {
    return isEnabledForApi(apiData)
        && (definitionPath.startsWith("/collections/{collectionId}/styles/{styleId}/map/tiles")
            || definitionPath.startsWith("/styles/{styleId}/map/tiles"));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return TilesBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return TilesBuildingBlock.SPEC;
  }
}
