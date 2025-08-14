/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.flatgeobuf.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.features.core.domain.SfFlatConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformations;
import java.util.Map;
import org.immutables.value.Value;

/**
 * @buildingBlock FLATGEOBUF
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: FLATGEOBUF
 *   enabled: true
 * ```
 *     </code>
 */
@Value.Immutable
@Value.Style(builder = "new", deepImmutablesDetection = true, attributeBuilderDetection = true)
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "FLATGEOBUF")
@JsonDeserialize(builder = ImmutableFlatgeobufConfiguration.Builder.class)
public interface FlatgeobufConfiguration extends SfFlatConfiguration {

  /**
   * @langEn Change the default value of the [profile parameter](features.md#query-parameters) for
   *     this feature format. The value is an object where the key is the id of a profile set, such
   *     as `rel`, and the value is the default profile for the profile set, e.g., `rel-as-key`.
   *     These defaults override the defaults specified in the [Features](features.md) building
   *     block.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](features.md#query-parameter) für
   *     Features. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `rel`, und der Wert das Standardprofil für den Profilsatz, z. B. `rel-as-key`. Diese
   *     Vorgaben haben Vorrang vor den im [Features](features.md)-Baustein angegebenen
   *     Standardprofilen.
   * @since v4.2
   * @default {}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableFlatgeobufConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return ((ImmutableFlatgeobufConfiguration.Builder) source.getBuilder())
        .from(source)
        .from(this)
        .transformations(
            SfFlatConfiguration.super
                .mergeInto((PropertyTransformations) source)
                .getTransformations())
        .defaultProfiles(
            SfFlatConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
