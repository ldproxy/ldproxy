/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase.Role;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SchemaDeriverOpenApiFeatures extends SchemaDeriverOpenApi {

  public SchemaDeriverOpenApiFeatures(
      String label, Optional<String> description, Map<String, Codelist> codelists) {
    super(label, description, codelists);
  }

  // a response may represent a code by its title, depending on the profile
  @Override
  protected boolean withCodelistTitles() {
    return true;
  }

  @Override
  protected Schema<?> buildRootSchema(
      FeatureSchema schema,
      Map<String, Schema<?>> properties,
      Map<String, Schema<?>> definitions,
      List<String> requiredProperties) {
    Schema<?> rootSchema =
        new ObjectSchema()
            .title(label)
            .description(description.orElse(schema.getDescription().orElse(null)));

    rootSchema
        .required(ImmutableList.of("type", "geometry", "properties"))
        .addProperties("type", new StringSchema()._enum(ImmutableList.of("Feature")))
        .addProperties(
            "links", new ArraySchema().items(new Schema().$ref("#/components/schemas/Link")));

    findByRole(properties, Role.ID)
        .ifPresent(
            property -> {
              rootSchema.addProperties("id", property);
            });

    findByRole(properties, Role.PRIMARY_GEOMETRY)
        .or(() -> findByRole(properties, Role.SECONDARY_GEOMETRY))
        .ifPresentOrElse(
            property ->
                // the geometry is null, if it is skipped in a request or the feature has none;
                // OpenAPI 3.0 ignores members next to '$ref', so the reference is wrapped
                rootSchema.addProperties(
                    "geometry", new Schema<>().nullable(true).allOf(List.of(property))),
            () -> rootSchema.addProperties("geometry", new Schema<>().nullable(true)));

    Map<String, Schema<?>> propertiesWithoutRoles = withoutRoles(properties);

    Map<String, Schema<?>> propertiesWithoutRolesAndBrackets =
        withoutBrackets(propertiesWithoutRoles);

    // the schema describes responses, required properties are not listed, since a request may
    // select the properties; "properties" is null, if no property is returned
    ObjectSchema propertiesSchema = new ObjectSchema();
    propertiesSchema.nullable(true);
    propertiesSchema.properties(new LinkedHashMap<>());
    propertiesSchema.getProperties().putAll(propertiesWithoutRolesAndBrackets);
    rootSchema.addProperties("properties", propertiesSchema);

    return rootSchema;
  }

  private Map<String, Schema<?>> withoutBrackets(Map<String, Schema<?>> properties) {
    return properties.entrySet().stream()
        .map(
            property ->
                new SimpleImmutableEntry<>(
                    property.getKey().replaceAll("\\[\\]", ".1"), property.getValue()))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
