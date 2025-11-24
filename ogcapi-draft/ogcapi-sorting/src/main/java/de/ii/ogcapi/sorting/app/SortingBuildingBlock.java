/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.sorting.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
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
import de.ii.ogcapi.sorting.domain.ImmutableSortingConfiguration;
import de.ii.ogcapi.sorting.domain.SortingConfiguration;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Sorting
 * @langEn Sort features in a response.
 * @langDe Sortieren von Features in Rückgaben.
 * @conformanceEn *Sorting* implements the conformance class "Sortables" of the [draft of OGC API -
 *     Features - Part 5: Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html#rc_sortables) as well
 *     as the conformance classes "Sorting" and "Features Sorting" of the [draft of OGC API -
 *     Features - Part 8: Sorting](https://docs.ogc.org/DRAFTS/24-030.html).
 * @conformanceDe Der Baustein *Sorting* implementiert die Konformitätsklasse "Sortables" des
 *     [Entwurfs OGC API - Features - Part 5:
 *     Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html#rc_sortables) sowie die
 *     Konformitätsklassen "Sorting" und "Features Sorting" des [Entwurfs OGC API - Features - Part
 *     8: Sorting](https://docs.ogc.org/DRAFTS/24-030.html).
 * @ref:endpoints {@link de.ii.ogcapi.sorting.app.EndpointSortables}
 * @ref:cfg {@link de.ii.ogcapi.sorting.domain.SortingConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.sorting.domain.ImmutableSortingConfiguration}
 * @ref:queryParameters {@link de.ii.ogcapi.sorting.app.QueryParameterSortbyFeatures}, {@link
 *     de.ii.ogcapi.sorting.app.QueryParameterFSortables}
 */
@Singleton
@AutoBind
public class SortingBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/24-030.html",
              "OGC API - Features - Part 8: Sorting (DRAFT)"));
  private final SchemaInfo schemaInfo;
  private final FeaturesCoreProviders providers;

  @Inject
  public SortingBuildingBlock(FeaturesCoreProviders providers, SchemaInfo schemaInfo) {
    this.providers = providers;
    this.schemaInfo = schemaInfo;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableSortingConfiguration.Builder()
        .enabled(false)
        .pathSeparator(PathSeparator.DOT)
        .build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    // get the sorting configurations to process
    Map<String, SortingConfiguration> configs =
        api.getData().getCollections().entrySet().stream()
            .map(
                entry -> {
                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  final SortingConfiguration config =
                      collectionData.getExtension(SortingConfiguration.class).orElse(null);
                  if (Objects.isNull(config) || !config.isEnabled()) {
                    return null;
                  }
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    if (configs.isEmpty()) {
      // nothing to do
      return ValidationResult.of();
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    // check that the feature provider supports sorting
    FeatureProvider provider = providers.getFeatureProviderOrThrow(api.getData());
    if (!provider.supportsSorting()) {
      builder.addErrors(
          MessageFormat.format(
              "Sorting is enabled, but the feature provider of the API '{0}' does not support sorting.",
              provider.getId()));
    }

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == ValidationResult.MODE.NONE) {
      return builder.build();
    }

    for (Map.Entry<String, SortingConfiguration> entry : configs.entrySet()) {
      // check that there is at least one sortable for each collection where sorting is enabled
      if (entry.getValue().getIncluded().isEmpty()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "Sorting is enabled for collection ''{0}'', but no sortable property has been configured.",
                entry.getKey()));
      }

      List<String> properties = schemaInfo.getPropertyNames(api.getData(), entry.getKey());
      FeatureTypeConfigurationOgcApi collectionData =
          api.getData().getCollections().get(entry.getKey());
      Optional<FeatureSchema> schema = providers.getFeatureSchema(api.getData(), collectionData);
      if (schema.isEmpty()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "Sorting is enabled for collection ''{0}'', but no provider has been configured.",
                entry.getKey()));
      } else {
        checkSortablesExist(builder, entry, properties);
        checkSortablesAreEligible(builder, entry, api.getData(), collectionData, schema, providers);
      }
    }

    return builder.build();
  }

  private void checkSortablesExist(
      ImmutableValidationResult.Builder builder,
      Map.Entry<String, SortingConfiguration> entry,
      List<String> properties) {
    for (String sortable :
        Stream.concat(
                entry.getValue().getIncluded().stream(), entry.getValue().getExcluded().stream())
            .filter(v -> !"*".equals(v))
            .collect(Collectors.toUnmodifiableList())) {
      // does the collection include the sortable property?
      if (!properties.contains(sortable)) {
        builder.addErrors(
            MessageFormat.format(
                "The sorting configuration for collection ''{0}'' includes property ''{1}'', but the property does not exist.",
                entry.getKey(), sortable));
      }
    }
  }

  private void checkSortablesAreEligible(
      ImmutableValidationResult.Builder builder,
      Map.Entry<String, SortingConfiguration> entry,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      Optional<FeatureSchema> schema,
      FeaturesCoreProviders providers) {
    List<String> sortables =
        entry
            .getValue()
            .getSortablesSchema(apiData, collectionData, schema.get(), providers)
            .getAllNestedProperties()
            .stream()
            .map(SchemaBase::getFullPathAsString)
            .collect(Collectors.toList());
    entry.getValue().getIncluded().stream()
        .filter(propertyName -> !"*".equals(propertyName))
        .filter(propertyName -> !sortables.contains(propertyName))
        .forEach(
            propertyName ->
                builder.addStrictErrors(
                    MessageFormat.format(
                        "The sorting configuration for collection ''{0}'' includes a sortable property ''{1}'', but the property is not eligible.",
                        entry.getKey(), propertyName)));
  }
}
