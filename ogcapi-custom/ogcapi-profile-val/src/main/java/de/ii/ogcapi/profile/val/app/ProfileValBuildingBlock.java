/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.val.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.profile.val.domain.ImmutableProfileValConfiguration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Profile - Codelist Values
 * @langEn Profiles for representing feature properties with enumerated values.
 * @langDe Profile für die Darstellung von Objekteigenschaften mit Aufzählungswerten.
 * @scopeEn If the feature schema includes at least one property with a "codelist" constraint, one
 *     of two profiles can be used to select the representation of coded values in the response.
 *     Supported are "val-as-code" (the code) and "val-as-title" (the label associated with the
 *     code).
 *     <p>HTML uses "val-as-title" as the default, all other feature encodings use "val-as-code" as
 *     the default.
 *     <p>Note: Explicit codelist transformations in the provider or in the service configuration
 *     are always executed, the "profile" parameter with a value "val-as-code" does not disable
 *     these transformations.
 * @scopeDe Wenn das Feature-Schema mindestens eine Eigenschaft mit einer "Codelist"-Einschränkung
 *     enthält, können alternativ zwei Profile verwendet werden, um die Darstellung von codierten
 *     Werten in der Antwort auszuwählen. Unterstützt werden "val-as-code" (der Code) und
 *     "val-as-title" (die mit dem Code verbundene Bezeichnung).
 *     <p>HTML verwendet "val-as-title" als Default, alle anderen Formate "val-as-code".
 *     <p>Hinweis: Explizite Codelisten-Transformationen im Provider oder in der
 *     Service-Konfiguration werden immer ausgeführt, der "profile"-Parameter mit dem Wert
 *     "val-as-code" deaktiviert nicht diese Transformationen.
 * @ref:cfg {@link de.ii.ogcapi.profile.val.domain.ProfileValConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.profile.val.domain.ImmutableProfileValConfiguration}
 */
@Singleton
@AutoBind
public class ProfileValBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public ProfileValBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProfileValConfiguration.Builder().enabled(true).build();
  }
}
