/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg.migrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.ii.ldproxy.cfg.ValueMigration;
import de.ii.ogcapi.features.search.domain.StoredQueryValue;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import java.util.Objects;

/**
 * Replaces the deprecated parameter form `{"$parameter": {"$ref": "#/parameters/name"}}` in stored
 * queries with the named form `{"$parameter": {"name": {"$ref": "#/parameters/name"}}}`.
 */
public class StoredQueryMigrationV5 extends ValueMigration {

  static final String PARAMETER = "$parameter";
  static final String REF = "$ref";
  static final String PARAMETERS_PREFIX = "#/parameters/";

  public StoredQueryMigrationV5(ValueMigrationContext context) {
    super(context);
  }

  @Override
  public String getValueType() {
    return StoredQueryValue.class.getAnnotation(FromValueStore.class).type();
  }

  @Override
  public String getSubject() {
    return "stored query parameters";
  }

  @Override
  public String getDescription() {
    return "use the deprecated form {\"$parameter\": {\"$ref\": \"#/parameters/<name>\"}} that will be replaced by {\"$parameter\": {\"<name>\": {\"$ref\": \"#/parameters/<name>\"}}}";
  }

  @Override
  public boolean isApplicable(JsonNode value) {
    return hasDeprecatedParameter(value);
  }

  @Override
  public JsonNode migrate(JsonNode value) {
    JsonNode migrated = value.deepCopy();

    migrateParameters(migrated);

    return migrated;
  }

  private static boolean hasDeprecatedParameter(JsonNode node) {
    if (node.isObject() && isDeprecatedParameter(node)) {
      return true;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        if (hasDeprecatedParameter(child)) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isDeprecatedParameter(JsonNode node) {
    JsonNode parameter = node.get(PARAMETER);

    return Objects.nonNull(parameter)
        && parameter.isObject()
        && parameter.has(REF)
        && parameter.get(REF).isTextual();
  }

  private static void migrateParameters(JsonNode node) {
    if (node.isObject() && isDeprecatedParameter(node)) {
      String ref = node.get(PARAMETER).get(REF).asText();
      String name =
          ref.startsWith(PARAMETERS_PREFIX) ? ref.substring(PARAMETERS_PREFIX.length()) : ref;

      ((ObjectNode) node).putObject(PARAMETER).putObject(name).put(REF, ref);

      return;
    }
    if (node.isContainerNode()) {
      for (JsonNode child : node) {
        migrateParameters(child);
      }
    }
  }
}
