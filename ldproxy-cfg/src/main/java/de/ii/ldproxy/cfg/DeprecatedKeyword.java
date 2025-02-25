/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.AbstractJsonValidator;
import com.networknt.schema.AbstractKeyword;
import com.networknt.schema.CustomErrorMessageType;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonValidator;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.ValidationContext;
import com.networknt.schema.ValidationMessage;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Set;

public class DeprecatedKeyword extends AbstractKeyword {

  public static final String KEYWORD = "deprecated";

  public static boolean isDeprecated(ValidationMessage vm) {
    return Objects.equals(vm.getCode(), KEYWORD);
  }

  public DeprecatedKeyword() {
    super(KEYWORD);
  }

  @Override
  public JsonValidator newValidator(
      SchemaLocation schemaLocation,
      JsonNodePath schemaPath,
      JsonNode schemaNode,
      JsonSchema parentSchema,
      ValidationContext validationContext) {
    boolean deprecated = schemaNode.asBoolean(false);

    return new AbstractJsonValidator(schemaLocation, schemaPath, this, schemaNode) {
      @Override
      public Set<ValidationMessage> validate(
          ExecutionContext context, JsonNode node, JsonNode rootNode, JsonNodePath at) {
        if (deprecated) {
          return Set.of(
              ValidationMessage.builder()
                  .type(getValue())
                  .code(CustomErrorMessageType.of(getValue()).getErrorCode())
                  .format(new MessageFormat("{0}: is deprecated and should be upgraded"))
                  .schemaLocation(schemaLocation)
                  .evaluationPath(schemaPath)
                  .instanceLocation(at)
                  .instanceNode(node)
                  .build());
        }

        return Set.of();
      }
    };
  }
}
