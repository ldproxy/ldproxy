/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.features.domain.DatasetChangeListener;
import de.ii.xtraplatform.features.domain.FeatureChangeListener;
import de.ii.xtraplatform.features.domain.FeatureChanges;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// ignore in bindings
@AutoMultiBind(exclude = {WithChangeListeners.class})
public interface WithChangeListeners {

  Map<String, DatasetChangeListener> DATASET_CHANGE_LISTENERS =
      Collections.synchronizedMap(new HashMap<>());
  Map<String, FeatureChangeListener> FEATURE_CHANGE_LISTENERS =
      Collections.synchronizedMap(new HashMap<>());

  DatasetChangeListener onDatasetChange(OgcApi api);

  FeatureChangeListener onFeatureChange(OgcApi api);

  default void updateChangeListeners(FeatureChanges changeHandler, OgcApi api) {
    // since the maps are static,the api id is not sufficient, the key needs to include the class
    // name
    String key = api.getId() + this.getClass().getName();

    if (DATASET_CHANGE_LISTENERS.containsKey(key)) {
      changeHandler.removeListener(DATASET_CHANGE_LISTENERS.get(key));
    }

    DatasetChangeListener datasetChangeListener = onDatasetChange(api);
    changeHandler.addListener(datasetChangeListener);
    DATASET_CHANGE_LISTENERS.put(key, datasetChangeListener);

    if (FEATURE_CHANGE_LISTENERS.containsKey(key)) {
      changeHandler.removeListener(FEATURE_CHANGE_LISTENERS.get(key));
    }

    FeatureChangeListener featureChangeListener = onFeatureChange(api);
    changeHandler.addListener(featureChangeListener);
    FEATURE_CHANGE_LISTENERS.put(key, featureChangeListener);
  }

  default void removeChangeListeners(FeatureChanges changeHandler, OgcApi api) {
    // since the maps are static,the api id is not sufficient, the key needs to include the class
    // name
    String key = api.getId() + this.getClass().getName();

    if (DATASET_CHANGE_LISTENERS.containsKey(key)) {
      changeHandler.removeListener(DATASET_CHANGE_LISTENERS.get(key));
      DATASET_CHANGE_LISTENERS.remove(key);
    }

    if (FEATURE_CHANGE_LISTENERS.containsKey(key)) {
      changeHandler.removeListener(FEATURE_CHANGE_LISTENERS.get(key));
      FEATURE_CHANGE_LISTENERS.remove(key);
    }
  }
}
