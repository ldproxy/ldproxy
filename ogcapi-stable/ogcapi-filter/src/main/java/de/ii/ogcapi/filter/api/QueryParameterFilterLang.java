/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.api;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.filter.app.FilterBuildingBlock;
import de.ii.ogcapi.filter.domain.FilterConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @title filter-lang
 * @endpoints Features, Vector Tile
 * @langEn Language of the query expression in the `filter` parameter. Supported are `cql2-text` and
 *     `cql2-json`, specified in the OGC Standard *Common Query Language (CQL2)*. `cql2-text` is an
 *     SQL-like text encoding for filter expressions that also supports spatial, temporal and array
 *     predicates. `cql2-json` is a JSON encoding of the same grammar, suitable for use as part of a
 *     JSON object that represents a query. The use of `cql2-text` is recommended for filter
 *     expressions in the `filter` parameter.
 * @langDe Sprache des Filterausdrucks im Parameter `filter`. Unterstützt werden `cql2-text` und
 *     `cql2-json`, spezifiziert im OGC-Standard *Common Query Language (CQL2)*. `cql2-text` ist
 *     eine SQL-ähnliche Textkodierung für Filterausdrücke, die auch räumliche, zeitliche und
 *     Array-Prädikate unterstützt. `cql2-json` ist eine JSON-Kodierung der gleichen Grammatik,
 *     geeignet für die Verwendung als Teil eines JSON-Objekts, das eine Query darstellt. Die
 *     Verwendung von `cql2-text` wird für Filterausdrücke im Parameter `filter` empfohlen.
 */
@Singleton
@AutoBind
public class QueryParameterFilterLang extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Cql.Format> {

  static final String FILTER_LANG_CQL2_TEXT = "cql2-text";
  static final String FILTER_LANG_CQL2_JSON = "cql2-json";

  private final FeaturesCoreProviders providers;
  private final SchemaValidator schemaValidator;

  @Inject
  public QueryParameterFilterLang(
      FeaturesCoreProviders providers, SchemaValidator schemaValidator) {
    this.providers = providers;
    this.schemaValidator = schemaValidator;
  }

  private boolean supportsCql2(OgcApiDataV2 apiData) {
    return providers
        .getFeatureProvider(apiData, FeatureProvider::queries)
        .map(FeatureQueries::supportsCql2)
        .orElse(false);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return super.isEnabledForApi(apiData) && supportsCql2(apiData);
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return super.isEnabledForApi(apiData, collectionId) && supportsCql2(apiData);
  }

  @Override
  public String getName() {
    return "filter-lang";
  }

  @Override
  public String getDescription() {
    return "Language of the query expression in the 'filter' parameter. Supported are 'cql2-text' (default) and 'cql2-json', "
        + "specified in the [OGC Standard 'Common Query Language (CQL2)'](https://docs.ogc.org/is/21-065r2/21-065r2.html). "
        + "'cql2-text' is an SQL-like text encoding for "
        + "filter expressions that also supports spatial, temporal and array predicates. 'cql2-json' is a JSON encoding of "
        + "that grammar, suitable for use as part of a JSON object that represents a query. The use of 'cql2-text' is recommended "
        + "for filter expressions in the 'filter' parameter.";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/collections/{collectionId}/items".equals(definitionPath)
        || "/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}".equals(definitionPath)
        || "/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}"
            .equals(definitionPath);
  }

  @Override
  public Cql.Format parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> collectionData) {
    return Objects.equals(value, FILTER_LANG_CQL2_JSON) ? Cql.Format.JSON : Cql.Format.TEXT;
  }

  private final Schema<String> schema =
      new StringSchema()
          ._enum(ImmutableList.of(FILTER_LANG_CQL2_TEXT, FILTER_LANG_CQL2_JSON))
          ._default(FILTER_LANG_CQL2_TEXT);

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schema;
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData, String collectionId) {
    return schema;
  }

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return FilterConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return FilterBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return FilterBuildingBlock.SPEC;
  }
}
