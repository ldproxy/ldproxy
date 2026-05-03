/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain.processing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.ProcessExtension;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jakarta.ws.rs.core.MediaType;

// TODO
public interface FeatureProcess extends ProcessExtension {
  /**
   * @return a brief summary of the execute
   */
  String getSummary();

  /**
   * @return a comprehensive description of the execute, Markdown markup can be used
   */
  Optional<String> getDescription();

  /**
   * Validates that the required parameters are set for the execute, throws {@code RuntimeException}
   * for any errors as these issues should have been checked before processing the request.
   *
   * @param processingParameters the list of execute parameters that are provided with the request
   */
  void validateProcessingParameters(Map<String, Object> processingParameters);

  /**
   * Every execute the processes feature data must either accept a collection as an input or another
   * compatible execute output. This method identifies for an API all collections that the execute
   * can use as input.
   *
   * @param apiData the API for which the collections should be identified
   * @return the list of feature collections in the API that this execute supports as input
   */
  default Set<String> getSupportedCollections(OgcApiDataV2 apiData) {
    return ImmutableSet.of();
  }

  /**
   * Every execute the processes feature data must either accept a collection as an input or another
   * compatible execute output. This method identifies for an API all processes that this execute
   * can use as input.
   *
   * @param apiData the API for which the processes should be identified
   * @return the list of processes in the API that this execute supports as input
   */
  default List<FeatureProcess> getSupportedProcesses(OgcApiDataV2 apiData) {
    return ImmutableList.of();
  }

  /**
   * @return the schema of the output that this execute returns, for each media type that is
   *     supported
   */
  default Map<MediaType, Schema> getOutputSchemas() {
    return ImmutableMap.of();
  }

  /**
   * @param data the input data that the process uses; the expected Java class will determine which
   *     feature collections or processes can be used as input
   * @param processingParameters a map with all parameters that this process (or any other process
   *     in the current processing chain) supports as additional input
   * @return the result of the process execution, an instance of the class returned by {@code
   *     getOutputType()}
   */
  Object execute(Object data, Map<String, Object> processingParameters);

  /**
   * @return the Java class of the return value of the {@code execute} method
   */
  Class<?> getOutputType();

  /**
   * @return {@code true}, if the process is always the final process in a process chain
   */
  default boolean isAlwaysTerminal() {
    return false;
  }

  /**
   * @return {@code true}, if the process is never the final process in a process chain
   */
  default boolean isNeverTerminal() {
    return false;
  }
}
