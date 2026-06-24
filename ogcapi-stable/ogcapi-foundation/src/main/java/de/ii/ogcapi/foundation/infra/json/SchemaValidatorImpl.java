/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.json;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.resource.IriResourceLoader;
import com.networknt.schema.serialization.JsonMapperFactory;
import de.ii.ogcapi.foundation.domain.CompiledJsonSchema;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Singleton
@AutoBind
public class SchemaValidatorImpl implements SchemaValidator {

  private final SchemaRegistry schemaRegistry;

  private static final ObjectMapper MAPPER = JsonMapperFactory.getInstance();

  @Inject
  public SchemaValidatorImpl() {
    SchemaRegistryConfig config = SchemaRegistryConfig.builder().failFast(true).build();
    this.schemaRegistry =
        SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder ->
                builder
                    .schemaRegistryConfig(config)
                    .resourceLoaders(loaders -> loaders.add(IriResourceLoader.getInstance())));
  }

  @Override
  public Optional<String> validate(String schemaContent, String jsonContent) throws IOException {
    return validate(compileInternal(schemaContent), jsonContent);
  }

  @Override
  public CompiledJsonSchema compile(String schemaContent) throws IOException {
    return compileInternal(schemaContent);
  }

  @Override
  public Optional<String> validate(CompiledJsonSchema schema, String jsonContent)
      throws IOException {
    JsonNode jsonNode;
    try {
      jsonNode = MAPPER.readTree(jsonContent);
    } catch (JsonParseException e) {
      return Optional.of(e.getMessage());
    }

    List<Error> result = ((CompiledJsonSchemaImpl) schema).schema.validate(jsonNode);
    if (result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(result.toString());
  }

  private CompiledJsonSchemaImpl compileInternal(String schemaContent) throws IOException {
    JsonNode schemaNode = MAPPER.readTree(schemaContent);
    Schema schema = schemaRegistry.getSchema(schemaNode);
    schema.initializeValidators();
    return new CompiledJsonSchemaImpl(schema);
  }

  private static final class CompiledJsonSchemaImpl implements CompiledJsonSchema {
    final Schema schema;

    CompiledJsonSchemaImpl(Schema schema) {
      this.schema = schema;
    }
  }
}
