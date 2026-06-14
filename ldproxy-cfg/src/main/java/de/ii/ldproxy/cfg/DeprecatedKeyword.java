/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.ExecutionContext;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaContext;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.keyword.AbstractKeyword;
import com.networknt.schema.keyword.AbstractKeywordValidator;
import com.networknt.schema.keyword.KeywordValidator;
import com.networknt.schema.path.NodePath;
import java.text.MessageFormat;
import java.util.Objects;

public class DeprecatedKeyword extends AbstractKeyword {

  public static final String KEYWORD = "deprecated";

  public static boolean isDeprecated(Error error) {
    return Objects.equals(error.getMessageKey(), KEYWORD);
  }

  public DeprecatedKeyword() {
    super(KEYWORD);
  }

  @Override
  public KeywordValidator newValidator(
      SchemaLocation schemaLocation,
      JsonNode schemaNode,
      Schema parentSchema,
      SchemaContext schemaContext) {
    boolean deprecated = schemaNode.asBoolean(false);

    return new AbstractKeywordValidator(KEYWORD, schemaNode, schemaLocation) {
      @Override
      public void validate(
          ExecutionContext executionContext,
          JsonNode instanceNode,
          JsonNode instance,
          NodePath instanceLocation) {
        if (deprecated) {
          executionContext.addError(
              Error.builder()
                  .keyword(KEYWORD)
                  .messageKey(KEYWORD)
                  .format(new MessageFormat("{0}: is deprecated and should be upgraded"))
                  .schemaLocation(getSchemaLocation())
                  .evaluationPath(executionContext.getEvaluationPath())
                  .instanceLocation(instanceLocation)
                  .instanceNode(instanceNode)
                  .build());
        }
      }
    };
  }
}
