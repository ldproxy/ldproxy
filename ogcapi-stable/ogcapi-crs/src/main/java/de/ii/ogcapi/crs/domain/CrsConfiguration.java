/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.Set;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock CRS
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: CRS
 *   additionalCrs:
 *   - code: 25832
 *     forceAxisOrder: NONE
 *     alternativeUri: 'urn:adv:crs:ETRS89_UTM32'
 *   - code: 4258
 *     forceAxisOrder: NONE
 *   - code: 4326
 *     forceAxisOrder: NONE
 *   - code: 3857
 *     forceAxisOrder: NONE
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "CRS")
@JsonDeserialize(builder = ImmutableCrsConfiguration.Builder.class)
public interface CrsConfiguration extends ExtensionConfiguration {

  abstract class Builder extends ExtensionConfiguration.Builder {}

  /**
   * @langEn Lists additional coordinate reference systems to be supported in the API or for a
   *     feature collection. The native coordinate reference system of the data and the default
   *     coordinate reference system of the API are automatically enabled. Coordinate reference
   *     systems are identified by their EPSG code (`code`). Additionally, the order of the
   *     coordinate axes must be specified in `forceAxisOrder` (`NONE`: as in the coordinate
   *     reference system, `LON_LAT` or `LAT_LON`: the order in the coordinate reference system is
   *     ignored and the specified order is used). Optionally, an alternative identifier under which
   *     the CRS is known in a community may be declared in `alternativeUri` (e.g. the AdV
   *     identifier `urn:adv:crs:ETRS89_UTM32` for EPSG:25832); it is used by feature encodings that
   *     render CRS identifiers on the wire (e.g. the GML `srsName` with `srsNameStyle: TEMPLATE`)
   *     and when decoding such identifiers on input — requests and the `Content-Crs` header always
   *     use the OGC URI.
   * @langDe Steuert, welche weitere Koordinatenreferenzsysteme in einer API oder für eine Feature
   *     Collection unterstützt werden sollen. Das native Koordinatenreferenzsystem der Daten und
   *     das Default-Koordinatenreferenzsystem der API sind automatisch aktiviert.
   *     Koordinatenreferenzsysteme werden über ihren EPSG-Code identifiziert (`code`). Zusätzlich
   *     ist in `forceAxisOrder` die Reihenfolge der Koordinatenachsen anzugeben (`NONE`: wie im
   *     Koordinatenreferenzsystem, `LON_LAT` oder `LAT_LON`: die Reihenfolge im
   *     Koordinatenreferenzsystem wird ignoriert und die angegebene Reihenfolge wird verwendet).
   *     Optional kann in `alternativeUri` eine alternative Kennung deklariert werden, unter der das
   *     CRS in einer Community bekannt ist (z.B. die AdV-Kennung `urn:adv:crs:ETRS89_UTM32` für
   *     EPSG:25832); sie wird von Feature-Kodierungen verwendet, die CRS-Kennungen kodieren (z.B.
   *     das GML-`srsName` mit `srsNameStyle: TEMPLATE`), sowie beim Einlesen solcher Kennungen —
   *     Requests und der `Content-Crs`-Header verwenden immer die OGC-URI.
   * @default {}
   */
  Set<EpsgCrs> getAdditionalCrs();

  /**
   * @langEn If `true`, the coordinate reference systems will be included in every Collection
   *     resource that is embedded in the Collections resource. The global `crs` array will not be
   *     used or referenced. Use this option, if the API is intended to be used with a client that
   *     does not support the global `crs` array.
   * @langDe Bei `true` werden die Koordinatenreferenzsysteme in jede Collection-Ressource
   *     aufgenommen, die in die Collections-Ressource eingebettet ist. Das globale `crs`-Array wird
   *     nicht verwendet oder referenziert. Verwenden Sie diese Option, wenn die API mit Clients
   *     verwendet werden soll, die das globale `crs`-Array nicht unterstützen.
   * @default false
   */
  @Nullable
  Boolean getSuppressGlobalCrsList();

  @Value.Derived
  @JsonIgnore
  default boolean shouldSuppressGlobalCrsList() {
    return Boolean.TRUE.equals(getSuppressGlobalCrsList());
  }

  @Override
  default Builder getBuilder() {
    return new ImmutableCrsConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ImmutableCrsConfiguration.Builder builder = getBuilder().from(source).from(this);

    getAdditionalCrs()
        .forEach(
            epsgCrs -> {
              if (!((CrsConfiguration) source).getAdditionalCrs().contains(epsgCrs)) {
                builder.addAdditionalCrs(epsgCrs);
              }
            });

    return builder.build();
  }
}
