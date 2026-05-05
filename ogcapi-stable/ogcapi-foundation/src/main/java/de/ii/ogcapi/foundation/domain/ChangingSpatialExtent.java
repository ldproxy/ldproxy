/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.ChangingValue;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
public interface ChangingSpatialExtent extends ChangingValue<BoundingBox> {

  static ChangingSpatialExtent of(BoundingBox bbox) {
    return new ImmutableChangingSpatialExtent.Builder()
        .value(Objects.requireNonNullElse(bbox, BoundingBox.of(-180, -90, 180, 90, OgcCrs.CRS84)))
        .build();
  }

  @Override
  default Optional<ChangingValue<BoundingBox>> updateWith(ChangingValue<BoundingBox> delta) {
    BoundingBox deltaExtent = delta.getValue();
    if (this.getValue().getEpsgCrs().getCode() != deltaExtent.getEpsgCrs().getCode()
        || this.getValue().getEpsgCrs().getForceAxisOrder()
            != deltaExtent.getEpsgCrs().getForceAxisOrder()) {
      throw new IllegalStateException("Both bounding boxes must have the same CRS.");
    }

    if (getValue().getXmin() <= deltaExtent.getXmin()
        && getValue().getYmin() <= deltaExtent.getYmin()
        && getValue().getXmax() >= deltaExtent.getXmax()
        && getValue().getYmax() >= deltaExtent.getYmax()) {
      return Optional.empty();
    }

    return Optional.of(
        ChangingSpatialExtent.of(
            OgcCrs.CRS84.equals(deltaExtent.getEpsgCrs())
                ? BoundingBox.of(
                    Math.min(getValue().getXmin(), deltaExtent.getXmin()),
                    Math.min(getValue().getYmin(), deltaExtent.getYmin()),
                    Math.max(getValue().getXmax(), deltaExtent.getXmax()),
                    Math.max(getValue().getYmax(), deltaExtent.getYmax()),
                    OgcCrs.CRS84)
                : BoundingBox.of(
                    Math.min(getValue().getXmin(), deltaExtent.getXmin()),
                    Math.min(getValue().getYmin(), deltaExtent.getYmin()),
                    Math.min(getValue().getZmin(), deltaExtent.getZmin()),
                    Math.max(getValue().getXmax(), deltaExtent.getXmax()),
                    Math.max(getValue().getYmax(), deltaExtent.getYmax()),
                    Math.max(getValue().getZmax(), deltaExtent.getZmax()),
                    OgcCrs.CRS84h)));
  }
}
