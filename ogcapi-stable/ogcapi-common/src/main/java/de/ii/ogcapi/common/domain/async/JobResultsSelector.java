/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import java.util.Map;

@AutoMultiBind
public interface JobResultsSelector {
  boolean canHandle(Map<String, Object> context);

  Map<String, Object> selectResults(Map<String, Object> results, Map<String, Object> context);

  Object selectSpecificResult(
      Map<String, Object> results, Map<String, Object> context, String kind, String resultKey);

  Object selectSpecificResultN(
      Map<String, Object> results,
      Map<String, Object> context,
      String kind,
      String resultKey,
      int resultIndex);
}
