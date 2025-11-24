/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.rel.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.profile.rel.domain.ImmutableProfileRelConfiguration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Profile - References
 * @langEn Profiles for feature properties that reference other resources.
 * @langDe Profile für Objekteigenschaften, die andere Ressourcen referenzieren.
 * @scopeEn If the feature schema includes at least one property of type `FEATURE_REF` or
 *     `FEATURE_REF_ARRAY`, three profiles can be used to select the encoding of object references
 *     in the response. Supported are "rel-as-link" (a link with URI and an optional title),
 *     "rel-as-key" (the `featureId` of the referenced feature) and "rel-as-uri" (the URI of the
 *     referenced feature).
 *     <p>Formats that only support simple values will typically not support "rel-as-link" and use
 *     "rel-as-key" as the default. HTML, GeoJSON, JSON-FG and GML use "rel-as-link" as the default.
 *     GML only supports "rel-as-link".
 * @scopeDe Enthält das Feature-Schema mindestens eine Eigenschaft vom Typ `FEATURE_REF` oder
 *     `FEATURE_REF_ARRAY`, können alternativ drei Profile verwendet werden, um die Kodierung der
 *     Objektreferenzen in der Antwort auszuwählen. Unterstützt werden "rel-as-link" (ein Link mit
 *     URI und einem optionalen Titel), "rel-as-key" (die `featureId` des referenzierten Features)
 *     und "rel-as-uri" (die URI des referenzierten Features).
 *     <p>Formate, die nur einfache Werte unterstützen, unterstützen typischerweise "rel-as-link"
 *     nicht und verwenden "rel-as-key" als Default. HTML, GeoJSON, JSON-FG und GML verwenden
 *     "rel-as-link" als Default. GML unterstützt nur "rel-as-link".
 * @conformanceEn *Profile - References* implements the Requirements Class "Profiles for references"
 *     [draft of OGC API - Features - Part 5: Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html).
 * @conformanceDe Der Baustein implementiert die Requirements Class "Profiles for references"
 *     [Entwurf von OGC API - Features - Part 5:
 *     Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html).
 * @ref:cfg {@link de.ii.ogcapi.profile.rel.domain.ProfileRelConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.profile.rel.domain.ImmutableProfileRelConfiguration}
 */
@Singleton
@AutoBind
public class ProfileRelBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/23-058r1.html",
              "OGC API - Features - Part 5: Schemas (DRAFT)"));

  @Inject
  public ProfileRelBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProfileRelConfiguration.Builder().enabled(true).build();
  }
}
