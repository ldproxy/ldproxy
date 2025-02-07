/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.html.domain.StyleReader;
import de.ii.xtraplatform.values.domain.KeyValueStore;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.ValueStoreDecorator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class StyleReaderImpl implements StyleReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(StyleReaderImpl.class);

  private final KeyValueStore<StoredValue> mbStylesStore;
  private final KeyValueStore<StoredValue> tiles3dStylesStore;

  @Inject
  public StyleReaderImpl(ValueStore valueStore) {
    this.mbStylesStore =
        new ValueStoreDecorator<>() {
          @Override
          public KeyValueStore<StoredValue> getDecorated() {
            return (KeyValueStore<StoredValue>) valueStore;
          }

          @Override
          public List<String> getValueType() {
            return List.of(StyleFormat.MBS.valueType());
          }
        };
    this.tiles3dStylesStore =
        new ValueStoreDecorator<>() {
          @Override
          public KeyValueStore<StoredValue> getDecorated() {
            return (KeyValueStore<StoredValue>) valueStore;
          }

          @Override
          public List<String> getValueType() {
            return List.of(StyleFormat._3DTILES.valueType());
          }
        };
  }

  @Override
  public boolean exists(
      String apiId, Optional<String> collectionId, String styleId, StyleFormat styleFormat) {
    if (isMbStyle(styleFormat)) {
      return mbStylesStore.has(styleId, getPathArrayStyles(apiId, collectionId));
    }
    if (is3dTilesStyle(styleFormat)) {
      return tiles3dStylesStore.has(styleId, getPathArrayStyles(apiId, collectionId));
    }
    return false;
  }

  private String[] getPathArrayStyles(String apiId, Optional<String> collectionId) {
    return collectionId.map(s -> new String[] {apiId, s}).orElseGet(() -> new String[] {apiId});
  }

  private boolean isMbStyle(StyleFormat format) {
    return Objects.equals(format, StyleFormat.MBS);
  }

  private boolean is3dTilesStyle(StyleFormat format) {
    return Objects.equals(format, StyleFormat._3DTILES);
  }
}
