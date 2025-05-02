/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.html.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.html.domain.StyleReader;
import de.ii.xtraplatform.values.domain.Identifier;
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
      if (mbStylesStore.has(styleId, getPathArrayStyles(apiId, collectionId))) {
        return true;
      }

      // TODO: This is a hack and also fails to detect, if a collection style cannot be derived from
      //       the dataset style for some reason (however, the deriveCollectionStyles option should
      //       only be enabled where the derivation works, so this should be ok as a temporary
      //       hack).
      return collectionId
          .filter(id -> hasDeriveCollectionStyles(apiData, id))
          .map(id -> mbStylesStore.has(styleId, getPathArrayStyles(apiId, Optional.empty())))
          .orElse(false);
    }
    if (is3dTilesStyle(styleFormat)) {
      return tiles3dStylesStore.has(styleId, getPathArrayStyles(apiId, collectionId));
    }
    return false;
  }

  @Override
  public List<String> getStyleIds(
      String apiId, Optional<String> collectionId, StyleFormat styleFormat) {
    if (isMbStyle(styleFormat)) {
      return mbStylesStore.identifiers(getPathArrayStyles(apiId, collectionId)).stream()
          .map(Identifier::id)
          .toList();
    }
    if (is3dTilesStyle(styleFormat)) {
      return tiles3dStylesStore.identifiers(getPathArrayStyles(apiId, collectionId)).stream()
          .map(Identifier::id)
          .toList();
    }

    return List.of();
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

  // TODO: Everything below this line is an ugly hack and should be replaced by a proper solution;
  //       also remove the module dependency to 'de.interactive_instruments:xtraplatform-features'

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module());
  private static final TypeReference<List<Map<String, Object>>> AS_LIST = new TypeReference<>() {};

  @Deprecated(forRemoval = true)
  private static boolean hasDeriveCollectionStyles(OgcApiDataV2 apiData, String collectionId) {
    if (apiData
        .getCollectionData(collectionId)
        .map(FeatureTypeConfigurationOgcApi::getExtensions)
        .map(StyleReaderImpl::hasDeriveCollectionStyles)
        .orElse(false)) {
      return true;
    }

    return hasDeriveCollectionStyles(apiData.getExtensions());
  }

  @Deprecated(forRemoval = true)
  private static boolean hasDeriveCollectionStyles(List<ExtensionConfiguration> extensions) {
    try {
      for (Map<String, Object> ext :
          MAPPER.readValue(MAPPER.writeValueAsString(extensions), AS_LIST)) {
        if (Objects.equals(ext.get("buildingBlock"), "STYLES")) {
          if (Objects.equals(Boolean.TRUE, ext.get("deriveCollectionStyles"))) {
            return true;
          }
        }
      }
    } catch (JsonProcessingException ignore) {
    }

    return false;
  }
}
