/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.queryables.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.queryables.domain.ImmutableQueryablesConfiguration;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration;
import de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration.PathSeparator;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SchemaInfo;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult.Builder;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Feature Collections - Queryables
 * @langEn Metadata about the properties of the features in a feature collection that can be used in
 *     filter expressions.
 * @langDe Metadaten über die Eigenschaften von Objekten aus einer Feature Collection, die in
 *     Filter-Ausdrücken verwendet werden können.
 * @scopeEn The queryables are represented as a schema where each queryable is a property. The
 *     schema for each queryable is automatically derived from the definition of the property in the
 *     feature provider. Supported encodings are JSON Schema and HTML.
 *     <p>The following types of properties cannot be queryables: <code>
 * - objects or arrays of objects;
 * - properties with multiple sources, i.e., with `coalesce` or `concat`.
 *     </code>
 *     <p>In addition, the following types of properties should not be queryables: <code>
 * - constant values;
 * - properties with value transformations in the provider schema (`stringFormat`, `dateFormat`, `nullify`, `map`, `codelist`).
 *     </code>
 *     <p>These properties are still accepted as queryables, if explicitly included. This behavior
 *     is deprecated, these properties will not be eligible as queryables in the next major version.
 *     <p>If the queryable property is a value, e.g., a string or integer, that is nested in an
 *     array, the type of the queryable will be an array of values.
 * @scopeDe Die Queryables werden als Schema kodiert, wobei jede Queryable eine Objekteigenschaft
 *     ist. Das Schema für jede abfragbare Eigenschaft wird automatisch aus der Definition der
 *     Eigenschaft im Feature-Provider abgeleitet. Unterstützte Kodierungen sind JSON Schema und
 *     HTML.
 *     <p>Die folgenden Arten von Eigenschaften können keine Queryables sein: <code>
 * - Objekte oder Arrays von Objekten
 * - Eigenschaften mit multiplen Quellen, d.h. mit `coalesce` oder `concat`.
 *     </code>
 *     <p>Darüber hinaus sollten die folgenden Arten von Eigenschaften nicht abfragbar sein: <code>
 * - konstante Werte;
 * - Eigenschaften mit Wertumwandlungen im Provider-Schema (`stringFormat`, `dateFormat`, `nullify`, `map`, `codelist`).
 *     </code>
 *     <p>Diese Eigenschaften werden nach wie vor als Queryables akzeptiert, wenn sie explizit in
 *     `included` enthalten sind. Dieses Verhalten ist deprecated, diese Eigenschaften werden in der
 *     nächsten Hauptversion nicht mehr als Queryables akzeptiert werden.
 *     <p>Wenn die abfragbare Eigenschaft ein Wert ist, z.B. ein String oder ein Integer, die in
 *     einem Array verschachtelt ist, ist der Typ der abfragbaren Eigenschaft ein Array der Werte.
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @conformanceEn *Feature Collections - Queryables* implements the conformance classes "Queryables"
 *     and "Queryables as Query Parameters" of [OGC API - Features - Part 3:
 *     Filtering](https://docs.ogc.org/is/19-079r2/19-079r2.html).
 * @conformanceDe Der Baustein implementiert die Konformitätsklassen "Queryables" und "Queryables as
 *     Query Parameters" von [OGC API - Features - Part 3:
 *     Filtering](https://docs.ogc.org/is/19-079r2/19-079r2.html).
 * @limitationsEn OGC API - Features - Part 3 does not specify how a queryable that is a feature
 *     reference which has more variables than the local feature id should be handled. If such a
 *     property is a queryable, the current implementation uses the local feature id as the value of
 *     the queryable. That is, such queryables are only useful, if the local feature ids are
 *     globally unique. As such, the current approach is a temporary solution that may still change
 *     in the future, if the behavior is specified in a standard.
 * @limitationsDe OGC API - Features - Part 3 spezifiziert nicht, wie ein Queryable, das eine
 *     Feature-Referenz ist, die mehr Variablen als die lokale Feature-ID hat, behandelt werden
 *     soll. Wenn eine solche Eigenschaft ein Queryable ist, verwendet die aktuelle Implementierung
 *     die lokale Feature-ID als Wert des Queryables. Das heißt, solche Queryables sind nur dann
 *     sinnvoll, wenn die lokalen Feature-IDs global eindeutig sind. Der derzeitige Ansatz ist
 *     dementsprechend eine Übergangslösung, die sich in Zukunft noch ändern kann, wenn das
 *     Verhalten in einem Standard festgelegt wird.
 * @ref:cfg {@link de.ii.ogcapi.collections.queryables.domain.QueryablesConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.collections.queryables.domain.ImmutableQueryablesConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.collections.queryables.app.EndpointQueryables}
 * @ref:queryParameters {@link de.ii.ogcapi.collections.queryables.app.QueryParameterFQueryables}
 * @ref:pathParameters {@link
 *     de.ii.ogcapi.collections.queryables.app.PathParameterCollectionIdQueryables}
 */
@Singleton
@AutoBind
public class QueryablesBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/19-079r2/19-079r2.html",
              "OGC API - Features - Part 3: Filtering"));

  private final SchemaInfo schemaInfo;
  private final FeaturesCoreProviders providers;

  @Inject
  public QueryablesBuildingBlock(SchemaInfo schemaInfo, FeaturesCoreProviders providers) {
    this.schemaInfo = schemaInfo;
    this.providers = providers;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableQueryablesConfiguration.Builder()
        .enabled(true)
        .pathSeparator(PathSeparator.DOT)
        .enableEndpoint(true)
        .asQueryParameters(true)
        .defaultProfiles(Map.of("codelist", "codelists-ref"))
        .build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    // get the configurations to process
    Map<String, QueryablesConfiguration> configs = getConfigurations(api);

    if (configs.isEmpty()) {
      // nothing to do
      return ValidationResult.of();
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    // check that the feature provider supports queries
    FeatureProvider provider = providers.getFeatureProviderOrThrow(api.getData());
    if (!provider.queries().isSupported()) {
      builder.addErrors(
          MessageFormat.format(
              "Queryables is enabled, but the feature provider of the API ''{0}'' does not support queries.",
              provider.getId()));
    }

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == ValidationResult.MODE.NONE) {
      return builder.build();
    }

    for (Map.Entry<String, QueryablesConfiguration> entry : configs.entrySet()) {
      FeatureTypeConfigurationOgcApi collectionData =
          Objects.requireNonNull(api.getData().getCollections().get(entry.getKey()));
      List<String> properties =
          schemaInfo.getPropertyNames(api.getData(), entry.getKey(), true, false);
      Optional<FeatureSchema> schema = providers.getFeatureSchema(api.getData(), collectionData);
      if (schema.isEmpty()) {
        builder.addErrors(
            MessageFormat.format(
                "Queryables is enabled for collection ''{0}'', but no provider has been configured.",
                entry.getKey()));
      } else {
        checkQueryableExists(builder, entry, properties);
        checkQueryableIsEligible(builder, entry, api.getData(), collectionData, schema, providers);
      }
    }

    return builder.build();
  }

  private void checkQueryableExists(
      ImmutableValidationResult.Builder builder,
      Map.Entry<String, QueryablesConfiguration> entry,
      List<String> properties) {
    for (String queryable :
        Stream.concat(
                entry.getValue().getIncluded().stream(), entry.getValue().getExcluded().stream())
            .filter(v -> !"*".equals(v))
            .collect(Collectors.toUnmodifiableList())) {
      // does the collection include the sortable property?
      if (!properties.contains(queryable)) {
        builder.addStrictErrors(
            MessageFormat.format(
                "The queryables configuration for collection ''{0}'' includes property ''{1}'', but the property does not exist.",
                entry.getKey(), queryable));
      }
    }
  }

  private void checkQueryableIsEligible(
      Builder builder,
      Entry<String, QueryablesConfiguration> entry,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      Optional<FeatureSchema> schema,
      FeaturesCoreProviders providers) {
    List<String> queryables =
        entry
            .getValue()
            .getQueryablesSchema(apiData, collectionData, schema.get(), providers)
            .getAllNestedProperties()
            .stream()
            .map(SchemaBase::getFullPathAsString)
            .collect(Collectors.toList());
    entry.getValue().getIncluded().stream()
        .filter(propertyName -> !"*".equals(propertyName))
        .filter(propertyName -> !queryables.contains(propertyName))
        .forEach(
            propertyName ->
                builder.addStrictErrors(
                    MessageFormat.format(
                        "The queryables configuration for collection ''{0}'' includes a property ''{1}'', but the property is not eligible.",
                        entry.getKey(), propertyName)));
  }

  private Map<String, QueryablesConfiguration> getConfigurations(OgcApi api) {
    return api.getData().getCollections().entrySet().stream()
        .map(
            entry -> {
              final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
              final QueryablesConfiguration config =
                  collectionData.getExtension(QueryablesConfiguration.class).orElse(null);
              if (Objects.isNull(config) || !config.isEnabled()) {
                return null;
              }
              return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
            })
        .filter(Objects::nonNull)
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
