/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SchemaGeneratorOpenApi;
import de.ii.ogcapi.features.search.domain.ImmutableQueryParameterTemplateParameter;
import de.ii.ogcapi.features.search.domain.SearchConfiguration;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.RuntimeQueryParametersExtension;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class QueryParametersStoredQueries implements RuntimeQueryParametersExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryParametersStoredQueries.class);

  final SchemaGeneratorOpenApi schemaGeneratorFeature;
  final FeaturesCoreProviders providers;
  final SchemaValidator schemaValidator;
  private final StoredQueryRepository repository;

  @Inject
  public QueryParametersStoredQueries(
      SchemaGeneratorOpenApi schemaGeneratorFeature,
      FeaturesCoreProviders providers,
      SchemaValidator schemaValidator,
      StoredQueryRepository repository) {
    this.schemaGeneratorFeature = schemaGeneratorFeature;
    this.providers = providers;
    this.schemaValidator = schemaValidator;
    this.repository = repository;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public List<OgcApiQueryParameter> getRuntimeParameters(
      OgcApiDataV2 apiData,
      Optional<String> collectionId,
      String definitionPath,
      HttpMethods method) {
    if (collectionId.isPresent()
        || !"/search/{queryId}".equals(definitionPath)
        || method != HttpMethods.GET) {
      return ImmutableList.of();
    }

    return repository.getAll(apiData).stream()
        .map(
            query ->
                query.getParametersWithOpenApiSchema().entrySet().stream()
                    .map(
                        entry -> {
                          String name = entry.getKey();
                          Schema<?> schema = entry.getValue();
                          StringBuilder description = new StringBuilder(name);
                          if (Objects.nonNull(schema.getTitle()) && !schema.getTitle().isEmpty()) {
                            description.append(schema.getTitle());
                            if (Objects.nonNull(schema.getDescription())
                                && !schema.getDescription().isEmpty()) {
                              description.append(": ");
                              description.append(schema.getDescription());
                            } else {
                              description.append('.');
                            }
                          }
                          return ImmutableQueryParameterTemplateParameter.builder()
                              .apiId(apiData.getId())
                              .queryId(query.getId())
                              .name(name)
                              .description(description.toString())
                              .schema(schema)
                              .schemaValidator(schemaValidator)
                              .build();
                        })
                    .collect(Collectors.toUnmodifiableList()))
        .flatMap(Collection::stream)
        .collect(Collectors.toUnmodifiableList());
  }
}
