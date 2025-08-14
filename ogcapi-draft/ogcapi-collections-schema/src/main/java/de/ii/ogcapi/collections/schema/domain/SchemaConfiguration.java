/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.CachingConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ProfilesConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.Map;
import org.immutables.value.Value;

/**
 * @buildingBlock SCHEMA
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: SCHEMA
 *   enabled: true
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "SCHEMA")
@JsonDeserialize(builder = ImmutableSchemaConfiguration.Builder.class)
public interface SchemaConfiguration
    extends ExtensionConfiguration, CachingConfiguration, ProfilesConfiguration {

  /**
   * @langEn Change the default value of the [profile parameter](schemas.md#query-parameters) for
   *     schema resources. The value is an object where the key is the id of a profile set, such as
   *     `codelist`, and the value is the default profile for the profile set, e.g.,
   *     `codelists-inline`. In addition, format-specific default profiles can be specified in the
   *     building block of each schema format. Those defaults have a higher priority.
   * @langDe Spezifiziert den Standardwert des [Profile-Parameters](schemas.md#query-parameter) für
   *     Schemas. Der Wert ist ein Objekt, bei dem der Schlüssel die ID eines Profilsatzes ist, z.
   *     B. `codelist`, und der Wert das Standardprofil für den Profilsatz, z. B.
   *     `codelists-inline`. Darüber hinaus können für jedes Schema-Format im entsprechenden
   *     Baustein formatspezifische Standardprofile konfiguriert werden. Diese Standardprofile haben
   *     eine höhere Priorität.
   * @since v4.5
   * @default {"codelist": "codelists-ref"}
   */
  @Override
  Map<String, String> getDefaultProfiles();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableSchemaConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return new ImmutableSchemaConfiguration.Builder()
        .from(source)
        .from(this)
        .defaultProfiles(
            ProfilesConfiguration.super
                .mergeInto((ProfilesConfiguration) source)
                .getDefaultProfiles())
        .build();
  }
}
