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
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import de.ii.ogcapi.foundation.domain.CompiledJsonSchema;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Singleton
@AutoBind
public class SchemaValidatorImpl implements SchemaValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject
  public SchemaValidatorImpl() {}

  @Override
  public Optional<String> validate(String schemaContent, String jsonContent) throws IOException {
    CompiledJsonSchema schema = compile(schemaContent);
    return validate(schema, jsonContent);
  }

  @Override
  public CompiledJsonSchema compile(String schemaContent) throws IOException {
    JsonNode schemaNode = MAPPER.readTree(schemaContent);
    SpecVersion.VersionFlag version;
    try {
      version = SpecVersionDetector.detect(schemaNode);
    } catch (Exception e) {
      version = SpecVersion.VersionFlag.V202012;
    }
    JsonSchemaFactory validatorFactory = JsonSchemaFactory.getInstance(version);
    SchemaValidatorsConfig config = new SchemaValidatorsConfig();
    config.setFailFast(true);
    config.setHandleNullableField(true);
    return new NetworkNtCompiledJsonSchema(validatorFactory.getSchema(schemaNode, config));
  }

  @Override
  public Optional<String> validate(CompiledJsonSchema schema, String jsonContent)
      throws IOException {
    if (!(schema instanceof NetworkNtCompiledJsonSchema compiled)) {
      throw new IllegalArgumentException(
          "Unsupported CompiledJsonSchema implementation: " + schema.getClass());
    }
    JsonNode jsonNode;
    try {
      jsonNode = MAPPER.readTree(jsonContent);
    } catch (JsonParseException e) {
      return Optional.of(e.getOriginalMessage());
    }
    Set<ValidationMessage> result;
    try {
      result = compiled.schema.validate(jsonNode);
    } catch (JsonSchemaException e) {
      result = e.getValidationMessages();
    }
    if (result.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(result.toString());
  }

  private static final class NetworkNtCompiledJsonSchema implements CompiledJsonSchema {
    private final JsonSchema schema;

    NetworkNtCompiledJsonSchema(JsonSchema schema) {
      this.schema = schema;
    }
  }
}
