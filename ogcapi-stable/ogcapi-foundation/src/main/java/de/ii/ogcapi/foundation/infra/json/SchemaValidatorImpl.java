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
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Singleton
@AutoBind
public class SchemaValidatorImpl implements SchemaValidator {

  @Inject
  public SchemaValidatorImpl() {}

  @Override
  public Optional<String> validate(String schemaContent, String jsonContent) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode schemaNode = mapper.readTree(schemaContent);
    JsonNode jsonNode;
    try {
      jsonNode = mapper.readTree(jsonContent);
    } catch (JsonParseException e) {
      // without the code location of the error
      return Optional.of(e.getOriginalMessage());
    }

    SpecVersion.VersionFlag version;
    try {
      version = SpecVersionDetector.detect(schemaNode);
    } catch (Exception e) {
      // use 2020-12 as the fallback version
      version = SpecVersion.VersionFlag.V202012;
    }
    return validate(schemaNode, jsonNode, version);
  }

  private Optional<String> validate(
      JsonNode schemaNode, JsonNode jsonNode, SpecVersion.VersionFlag version) {
    JsonSchemaFactory validatorFactory = JsonSchemaFactory.getInstance(version);
    SchemaValidatorsConfig config = new SchemaValidatorsConfig();
    config.setFailFast(true);
    config.setHandleNullableField(true);
    JsonSchema schema = validatorFactory.getSchema(schemaNode, config);
    Set<ValidationMessage> result;
    try {
      result = schema.validate(jsonNode);
    } catch (JsonSchemaException e) {
      result = e.getValidationMessages();
    }
    if (result.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(result.toString());
  }
}
