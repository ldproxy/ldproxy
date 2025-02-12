/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.StyleReader;
import de.ii.xtraplatform.values.domain.KeyValueStore;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.ValueStoreDecorator;
import java.util.List;
import java.util.Map;
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
      String apiId,
      Optional<String> collectionId,
      String styleId,
      StyleFormat styleFormat,
      OgcApiDataV2 apiData) {
    if (isMbStyle(styleFormat)) {
      boolean deriveCollectionStyles =
          hasDeriveCollectionStyles(apiData, collectionId.orElse(null));

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

  // TODO: Everything below this line is an ugly hack and should be replaced by a proper solution

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> AS_MAP = new TypeReference<>() {};

  @Deprecated(forRemoval = true)
  private static boolean hasDeriveCollectionStyles(OgcApiDataV2 apiData, String collectionId) {
    try {
      String s = MAPPER.writeValueAsString(apiData);
      Map<String, Object> api = MAPPER.readValue(s, AS_MAP);

      if (api.containsKey("collections")) {
        Map<String, Object> collections = (Map<String, Object>) api.get("collections");
        if (collections.containsKey(collectionId)) {
          Map<String, Object> collection = (Map<String, Object>) collections.get(collectionId);
          if (hasDeriveCollectionStyles(collection)) {
            return true;
          }
        }
      }

      return hasDeriveCollectionStyles(api);
    } catch (Throwable e) {
      return false;
    }
  }

  @Deprecated(forRemoval = true)
  private static boolean hasDeriveCollectionStyles(Map<String, Object> apiOrCollection) {
    if (apiOrCollection.containsKey("api")) {
      List<Map<String, Object>> api = (List<Map<String, Object>>) apiOrCollection.get("api");
      Optional<Map<String, Object>> styles =
          api.stream()
              .filter(
                  bb -> bb.containsKey("buildingBlock") && bb.get("buildingBlock").equals("STYLES"))
              .findFirst();

      if (styles.isPresent() && styles.get().containsKey("deriveCollectionStyles")) {
        return Objects.equals(Boolean.TRUE, styles.get().get("deriveCollectionStyles"));
      }
    }

    return false;
  }
}
