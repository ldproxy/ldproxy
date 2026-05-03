/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterF;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.tiles.domain.TileFormatExtension;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import de.ii.ogcapi.tiles.domain.TilesProviders;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * @title f
 * @endpoints Collection Tile
 * @langEn Select the output format of the response. If no value is provided, the standard HTTP
 *     rules apply, i.e., the "Accept" header will be used to determine the format.
 * @langDe Wählt das Ausgabeformat der Antwort. Wenn kein Wert angegeben wird, gelten die
 *     Standard-HTTP Regeln, d.h. der "Accept"-Header wird zur Bestimmung des Formats verwendet.
 */
@Singleton
@AutoBind
public class QueryParameterFTileCollection extends QueryParameterF {

  private final TilesProviders tilesProviders;

  @Inject
  protected QueryParameterFTileCollection(
      ExtensionRegistry extensionRegistry,
      SchemaValidator schemaValidator,
      TilesProviders tilesProviders) {
    super(extensionRegistry, schemaValidator);
    this.tilesProviders = tilesProviders;
  }

  @Override
  public String getId() {
    return "fTileCollection";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.startsWith("/collections/{collectionId}")
        && definitionPath.endsWith("/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}");
  }

  @Override
  protected Class<? extends FormatExtension> getFormatClass() {
    return TileFormatExtension.class;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TilesConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getExtension(TilesConfiguration.class, collectionId)
        .filter(TilesConfiguration::isEnabled)
        .filter(cfg -> cfg.hasCollectionTiles(tilesProviders, apiData, collectionId))
        .isPresent();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) schemaMap.put(apiHashCode, new ConcurrentHashMap<>());
    if (!schemaMap.get(apiHashCode).containsKey("*")) {
      List<String> fEnum = new ArrayList<>();
      extensionRegistry.getExtensionsForType(getFormatClass()).stream()
          .filter(
              f ->
                  apiData.getCollections().keySet().stream()
                      .anyMatch(collectionId -> f.isEnabledForApi(apiData, collectionId)))
          .filter(f -> !f.isInternal())
          .filter(f -> !f.getMediaType().parameter().equals("*"))
          .map(f -> f.getMediaType().parameter())
          .distinct()
          .sorted()
          .forEach(fEnum::add);
      schemaMap.get(apiHashCode).put("*", new StringSchema()._enum(fEnum));
    }
    return schemaMap.get(apiHashCode).get("*");
  }

  @Override
  public ApiMediaType parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    return extensionRegistry.getExtensionsForType(getFormatClass()).stream()
        .filter(
            f ->
                optionalCollectionData
                    .map(cd -> f.isEnabledForApi(api.getData(), cd.getId()))
                    .orElse(f.isEnabledForApi(api.getData())))
        .map(FormatExtension::getMediaType)
        .filter(mt -> Objects.equals(mt.parameter(), value))
        .findFirst()
        .orElse(null);
  }
}
