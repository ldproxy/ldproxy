/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.profile.codelist.domain.ImmutableProfileCodelistConfiguration;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Profile - Codelists in Schemas
 * @langEn Profiles for the representation of codelist values in schemas. Applicable to schemas.
 * @langDe Profile für die Darstellung von Codelistwerten in Schemas. Auf Schemas anwendbar.
 * @scopeEn A codelist is a list of codes with a human-readable title. Two different representations
 *     of a codelist-valued property in a schema are specified as profiles. The two profiles are:
 *     <p><code>
 * - "codelist-inline": The schema of each codelist-valued property is represented by a "oneOf" schema with a schema for each code. The schema for each code has a "const" member with the code as the value. This representation has the advantage that all information is in the schema; no external information needs to be accessed.
 * - "codelist-ref" (default): The schema of each codelist-valued property is represented by an "enum" member with the codes. In addition the codelist is referenced by a HTTP(S) URI using the keyword "x-ogc-codelistUri". This profile has the advantage that "enum" is easier to parse/handle than "oneOf" and that a separate resource for codelists may be useful for other purposes, too, and in fact may already be published.
 * </code>
 * @scopeDe Eine Codeliste ist eine Liste von Codes (String oder Integer) mit einer textlichen
 *     Bezeichnung. Zwei verschiedene Darstellungen einer Codelist-wertigen Eigenschaft in einem
 *     Schema werden als Profile unterstützt. Die beiden Profile sind:
 *     <p><code>
 * - "codelist-inline": Das Schema jeder Codelist-wertigen Eigenschaft wird durch ein "oneOf"-Schema mit einem Schema für jeden Code dargestellt. Das Schema für jeden Code hat ein "const"-Member mit dem Code als Wert und die Bezeichnung in "title". Diese Darstellung hat den Vorteil, dass alle Informationen im Schema enthalten sind. Es muss nicht auf externe Informationen zugegriffen werden.
 * - "codelist-ref" (Standardprofil): Das Schema jeder Codelist-Eigenschaft wird durch ein "enum"-Array mit den Codes dargestellt. Zusätzlich wird die Codeliste durch einen HTTP(S)-URI mit dem Schlüsselwort "x-ogc-codelistUri" referenziert. Dieses Profil hat den Vorteil, dass "enum" einfacher zu parsen/handhaben ist als "oneOf" und dass eine separate Ressource für Codelisten auch für andere Zwecke nützlich sein kann.
 * </code>
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @conformanceEn *Profile -Codelist* implements the Requirements Class "Profiles for codelists"
 *     [draft of OGC API - Features - Part 5: Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html).
 * @conformanceDe Der Baustein implementiert die Requirements Class "Profiles for codelists"
 *     [Entwurf von OGC API - Features - Part 5:
 *     Schemas](https://docs.ogc.org/DRAFTS/23-058r1.html).
 * @ref:cfg {@link de.ii.ogcapi.profile.codelist.domain.ProfileCodelistConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.profile.codelist.domain.ImmutableProfileCodelistConfiguration}
 */
@Singleton
@AutoBind
public class ProfileCodelistBuildingBlock implements ApiBuildingBlock, ConformanceClass {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/23-058r1.html",
              "OGC API - Features - Part 5: Schemas (DRAFT)"));

  @Inject
  public ProfileCodelistBuildingBlock() {}

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return List.of("http://www.opengis.net/spec/ogcapi-features-5/0.0/conf/profile-codelists");
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableProfileCodelistConfiguration.Builder().enabled(true).build();
  }
}
