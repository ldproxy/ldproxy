/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.values.domain.StoredValue;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/** Utility to simplify the creation of ldproxy YAML configuration files. */
public interface LdproxyCfgWriter {

  /**
   * Create a new writer for the given FS store.
   *
   * @param store the root directory of an FS store
   * @return the new {@link LdproxyCfgWriter}
   */
  static LdproxyCfgWriter create(Path store) {
    return new LdproxyCfgImpl(store, true);
  }

  /**
   * Create builders for entity data and related objects.
   *
   * @return chained interfaces to select a builder
   */
  Builders builder();

  /**
   * Write entity data as YAML file to the connected store.
   *
   * @param data an entity data object constructed using {@link builder()}
   * @throws IOException when entity data cannot be written
   */
  // <T extends EntityData> void writeEntity(T data) throws IOException;

  /**
   * Write entity data as YAML file to the connected store. Optionally apply patches from given YAML
   * files before writing.
   *
   * @param data an entity data object constructed using {@link builder()}
   * @param patches paths to patch YAML files
   * @throws IOException when entity data cannot be written
   */
  <T extends EntityData> void writeEntity(T data, Path... patches) throws IOException;

  /**
   * Write entity data as YAML file to the given OutputStream.
   *
   * @param data an entity data object constructed using {@link builder()}
   * @param outputStream the target OutputStream
   * @throws IOException when entity data cannot be written
   */
  <T extends EntityData> void writeEntity(T data, OutputStream outputStream) throws IOException;

  /**
   * Write value data as YAML file to the connected store.
   *
   * @param data a value data object constructed using {@link builder()}
   * @param name file name
   * @param path file path
   * @throws IOException when value data cannot be written
   */
  <T extends StoredValue> void writeValue(T data, String name, String... path) throws IOException;

  /**
   * Check if value exists in the connected store.
   *
   * @param type value type
   * @param name file name
   * @param path file path
   * @return true if value exists
   */
  boolean hasValue(String type, String name, String... path);

  /**
   * Write the connected store as ZIP file to the given OutputStream.
   *
   * @param outputStream the target OutputStream
   * @throws IOException when ZIP file cannot be written
   */
  void writeZippedStore(OutputStream outputStream) throws IOException;
}
