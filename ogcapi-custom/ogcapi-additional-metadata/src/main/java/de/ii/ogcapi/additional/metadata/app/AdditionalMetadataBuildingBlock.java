/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.additional.metadata.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.additional.metadata.domain.ImmutableAdditionalMetadataConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Additional Metadata
 * @langEn Additional metadata on Landing Page, Collections an Collection resources.
 * @langDe Zusätzliche Metadaten auf den Ressourcen Landing Page, Collections und Collection.
 * @scopeEn Adds additional information to the Landing Page, Collections and Collection resources
 *     that is not specified in OGC API standards. For example, license and attribution information.
 * @scopeDe Fügt der Landing Page, den Collections- und Collection-Ressourcen zusätzliche
 *     Informationen hinzu, die nicht in den OGC-API-Standards festgelegt sind. Zum Beispiel die
 *     Lizenz der Daten und Vorgaben zur Namensnennung.
 * @ref:cfg {@link de.ii.ogcapi.additional.metadata.domain.AdditionalMetadataConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.additional.metadata.domain.ImmutableAdditionalMetadataConfiguration}
 */
@Singleton
@AutoBind
public class AdditionalMetadataBuildingBlock implements ApiBuildingBlock {

  @Inject
  public AdditionalMetadataBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableAdditionalMetadataConfiguration.Builder().enabled(true).build();
  }
}
