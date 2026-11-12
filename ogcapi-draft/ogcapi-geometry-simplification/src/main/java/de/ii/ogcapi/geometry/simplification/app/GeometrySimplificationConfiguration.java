/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.geometry.simplification.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.geometry.simplification.app.ImmutableGeometrySimplificationConfiguration.Builder;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock GEOMETRY_SIMPLIFICATION
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: GEOMETRY_SIMPLIFICATION
 *   enabled: true
 *   zoomLevelPrecision: true
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "GEOMETRY_SIMPLIFICATION")
@JsonDeserialize(builder = Builder.class)
public interface GeometrySimplificationConfiguration extends ExtensionConfiguration {

  abstract class Builder extends ExtensionConfiguration.Builder {}

  /**
   * @langEn If `true`, the coordinates of geometries simplified with the query parameter
   *     `zoom-level` have only as many decimal places as the zoom level needs: the number of
   *     decimal places is derived from the simplification tolerance, so that the rounding stays
   *     below it (e.g. 4 decimal places in degrees at zoom level 12, 5 at zoom level 15). A coarser
   *     `coordinatePrecision` of the building block `FEATURES_CORE` still applies. In a projected
   *     coordinate reference system in meters, coordinates are only rounded from zoom level 18 on
   *     (1 decimal place).
   * @langDe Bei `true` haben die Koordinaten von Geometrien, die mit dem Query-Parameter
   *     `zoom-level` vereinfacht werden, nur so viele Nachkommastellen, wie die Zoomstufe braucht:
   *     Die Anzahl der Nachkommastellen ergibt sich aus der Toleranz der Vereinfachung, so dass die
   *     Rundung darunter bleibt (z.B. 4 Nachkommastellen in Grad bei Zoomstufe 12, 5 bei Zoomstufe
   *     15). Eine gröbere `coordinatePrecision` des Bausteins `FEATURES_CORE` gilt weiterhin. In
   *     einem projizierten Koordinatenreferenzsystem in Metern werden Koordinaten erst ab Zoomstufe
   *     18 gerundet (1 Nachkommastelle).
   * @default false
   * @since v5.0
   */
  @Nullable
  Boolean getZoomLevelPrecision();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean usesZoomLevelPrecision() {
    return Objects.equals(getZoomLevelPrecision(), true);
  }

  @Override
  default Builder getBuilder() {
    return new ImmutableGeometrySimplificationConfiguration.Builder();
  }
}
