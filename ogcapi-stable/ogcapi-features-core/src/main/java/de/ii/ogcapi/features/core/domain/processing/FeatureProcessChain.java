/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain.processing;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

public class FeatureProcessChain {
  private static final String DAPA_PATH_ELEMENT = "processes";
  private final List<FeatureProcess> processes;

  public FeatureProcessChain(List<FeatureProcess> processes) {
    this.processes = processes;
  }

  @Value.Derived
  @Value.Auxiliary
  public List<FeatureProcess> asList() {
    return processes;
  }

  /**
   * @return the path after {@code /collections/{collectionId}}
   */
  @Value.Derived
  @Value.Auxiliary
  public String getSubSubPath() {
    return "/"
        + DAPA_PATH_ELEMENT
        + "/"
        + String.join(
            ":", processes.stream().map(process -> process.getName()).collect(Collectors.toList()));
  }

  /**
   * @param processNames names of the process to look for, "*" is a wildcard
   * @return {@code true}, if the process chain includes a process with any of the names in {@code
   *     processName}
   */
  public boolean includes(String... processNames) {
    for (String name : processNames) {
      if (processes.stream()
          .anyMatch(process -> process.getName().equals(name) || "*".equals(name))) return true;
    }
    return false;
  }

  public String getOperationSummary() {
    return processes.get(processes.size() - 1).getSummary();
  }

  public Optional<String> getOperationDescription() {
    return processes.get(processes.size() - 1).getDescription();
  }

  public Optional<String> getResponseDescription() {
    return Optional.empty();
  }
}
