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
import de.ii.ogcapi.html.domain.StyleReader;
import de.ii.ogcapi.tiles.domain.TilesConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
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
  private final StyleReader styleReader;

  @Inject
  PathParameterStyleId(SchemaValidator schemaValidator, StyleReader styleReader) {
    this.schemaValidator = schemaValidator;
    this.styleReader = styleReader;
  }

  @Override
  public String getPattern() {
    return STYLE_ID_PATTERN;
  }

  @Override
  public List<String> getValues(OgcApiDataV2 apiData) {
    return Stream.concat(
            styleReader
                .getStyleIds(apiData.getId(), Optional.empty(), StyleReader.StyleFormat.MBS)
                .stream(),
            apiData.getCollections().keySet().stream()
                .flatMap(
                    collection ->
                        styleReader
                            .getStyleIds(
                                apiData.getId(),
                                Optional.of(collection),
                                StyleReader.StyleFormat.MBS)
                            .stream()))
        .toList();
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    if (!schemaMap.containsKey(apiData.hashCode())) {
      schemaMap.put(
          apiData.hashCode(), new StringSchema()._enum(ImmutableList.copyOf(getValues(apiData))));
    }

    return schemaMap.get(apiData.hashCode());
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
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
