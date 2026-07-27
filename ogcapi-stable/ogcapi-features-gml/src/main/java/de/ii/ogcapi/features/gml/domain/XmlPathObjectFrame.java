/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * The open {@code xmlPaths} chain of one object property: the segments before the repetition marker
 * — shared with the properties following the object and reinstated as the open wrapper prefix when
 * it ends — and the segments from the marker on, which this occurrence opened and which are closed
 * again at the object's end.
 */
public final class XmlPathObjectFrame {

  private final List<XmlPathElement> shared;
  private final List<XmlPathElement> repeating;
  private final String path;

  public XmlPathObjectFrame(
      List<XmlPathElement> shared, List<XmlPathElement> repeating, String path) {
    this.shared = ImmutableList.copyOf(shared);
    this.repeating = ImmutableList.copyOf(repeating);
    this.path = path;
  }

  public List<XmlPathElement> getShared() {
    return shared;
  }

  public List<XmlPathElement> getRepeating() {
    return repeating;
  }

  /** The property path of the object, so a following property can merge with its shared prefix. */
  public String getPath() {
    return path;
  }
}
