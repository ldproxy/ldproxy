/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.crud.domain.CrudConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
@AutoBind
public class HeaderPreferFeature extends HeaderPrefer {

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();

  @Inject
  HeaderPreferFeature(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "PreferCrudFeature";
  }

  @Override
  public String getDescription() {
    return "'handling=strict' creates or replaces the feature after successful validation. Status 400 is returned, "
        + "if validation fails. Where the 'rejectEmptyValues' option is enabled, a request body with an empty value "
        + "fails validation, too. 'handling=lenient' (the default) creates or replaces the feature without validation. "
        + "Where the 'returnRepresentation' option is enabled, 'return=representation' returns a representation of the "
        + "created or replaced feature in the response body; 'return=minimal' (the default) returns no response body.";
  }

  // The 'return' preference is only recognized where a collection of the API supports it, so it is
  // only stated as a value of the header where a client can use it.
  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schemaMap.computeIfAbsent(
        apiData.hashCode(),
        ignore ->
            CrudBuildingBlock.returnsRepresentation(apiData)
                ? new StringSchema()
                    ._enum(
                        ImmutableList.of(
                            "handling=strict",
                            "handling=lenient",
                            "return=representation",
                            "return=minimal"))
                    ._default("handling=lenient")
                : super.getSchema(apiData));
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData)
                && ((method == HttpMethods.PUT
                        && "/collections/{collectionId}/items/{featureId}".equals(definitionPath))
                    || (method == HttpMethods.POST
                        && "/collections/{collectionId}/items".equals(definitionPath))));
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CrudConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return CrudBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return CrudBuildingBlock.SPEC;
  }
}
