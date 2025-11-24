/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.cityjson.domain.CityJsonConfiguration;
import de.ii.ogcapi.features.cityjson.domain.ImmutableCityJsonConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - CityJSON
 * @langEn Encode CityGML building features (LoD1, LoD2) as CityJSON.
 * @langDe Kodierung von CityGML Gebäuden (LoD1, LoD2) als CityJSON.
 * @scopeEn The building block *Features CityJSON* adds support for CityJSON 1.0 and 1.1 as a
 *     feature encoding. Supported are the feature types `Building` and `BuildingPart`.
 * @scopeDe Der Baustein *Features CityJSON* unterstützt CityJSON 1.0 und 1.1 als Kodierung für
 *     Features. Unterstützt werden die Objektarten `Building` und `BuildingPart`.
 * @conformanceEn *Features - CityJSON* implements support for [CityJSON
 *     1.0](https://www.cityjson.org/specs/1.0.3/) and [CityJSON
 *     1.1](https://www.cityjson.org/specs/1.1.3/).
 * @conformanceDe *Features - CityJSON* implementiert Unterstützung für [CityJSON
 *     1.0](https://www.cityjson.org/specs/1.0.3/) und [CityJSON
 *     1.1](https://www.cityjson.org/specs/1.1.3/).
 * @limitationsEn Support is restricted to LoD1 and LoD2 buildings with building parts. All other
 *     object types including Appearance objects are not supported.
 * @limitationsDe Die Unterstützung ist auf LoD1 und LoD2 Gebäude mit Gebäudeteilen beschränkt. Alle
 *     anderen Objekttypen, einschließlich Appearance-Objekte, werden nicht unterstützt.
 * @ref:cfg {@link de.ii.ogcapi.features.cityjson.domain.CityJsonConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.cityjson.domain.ImmutableCityJsonConfiguration}
 */
@Singleton
@AutoBind
public class CityJsonBuildingBlock implements ApiBuildingBlock {

  @Inject
  public CityJsonBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableCityJsonConfiguration.Builder()
        .enabled(false)
        .textSequences(false)
        .version(CityJsonConfiguration.Version.V11)
        .build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, ValidationResult.MODE apiValidation) {

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == ValidationResult.MODE.NONE) {
      return ValidationResult.of();
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    OgcApiDataV2 apiData = api.getData();

    // check that text sequences are not enabled for version 1.0
    apiData
        .getCollections()
        .forEach(
            (key, value) ->
                value
                    .getExtension(CityJsonConfiguration.class)
                    .ifPresent(
                        config -> {
                          if (config.isEnabled()
                              && config.getTextSequences().orElse(false)
                              && config
                                  .getVersion()
                                  .filter(v -> v.equals(CityJsonConfiguration.Version.V10))
                                  .isPresent()) {
                            builder.addErrors(
                                "CityJSON Text Sequences can only be enabled for CityJSON 1.1 or later, not for CityJSON 1.0.");
                          }
                        }));

    return builder.build();
  }
}
