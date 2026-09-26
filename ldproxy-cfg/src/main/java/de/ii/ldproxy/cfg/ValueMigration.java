/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.fasterxml.jackson.databind.JsonNode;
import de.ii.ldproxy.cfg.ValueMigration.ValueMigrationContext;
import de.ii.xtraplatform.base.domain.Migration;

/**
 * A migration of deprecated content in the files of a value type (e.g. stored queries). Value
 * migrations operate on the parsed file content, since the typed values do not retain the
 * deprecated form after loading.
 */
public abstract class ValueMigration implements Migration<ValueMigrationContext, JsonNode> {

  /** Access to the value store for migrations that depend on other values. */
  @FunctionalInterface
  public interface ValueMigrationContext extends MigrationContext {
    boolean exists(String type, String name);
  }

  private final ValueMigrationContext context;

  protected ValueMigration(ValueMigrationContext context) {
    this.context = context;
  }

  @Override
  public final ValueMigrationContext getContext() {
    return context;
  }

  /**
   * @return the value type this migration applies to, i.e. the directory below `values`
   */
  public abstract String getValueType();

  /**
   * @param value the parsed content of a value file
   * @return true, if the value uses deprecated content that this migration upgrades
   */
  @Override
  public abstract boolean isApplicable(JsonNode value);

  /**
   * @param value the parsed content of a value file
   * @return the upgraded content, the given value is not modified
   */
  public abstract JsonNode migrate(JsonNode value);
}
