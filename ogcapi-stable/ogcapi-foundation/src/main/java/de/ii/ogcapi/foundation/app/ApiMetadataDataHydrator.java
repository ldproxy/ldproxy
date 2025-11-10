/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ImmutableApiMetadata;
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiDataHydratorExtension;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v3_0_1.expandedlicensing.ListedLicense;

@Singleton
@AutoBind
public class ApiMetadataDataHydrator implements OgcApiDataHydratorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(ApiMetadataDataHydrator.class);

  @Inject
  public ApiMetadataDataHydrator() {
    SpdxModelFactory.init();
  }

  @Override
  public int getSortPriority() {
    return 60;
  }

  @Override
  public OgcApiDataV2 getHydratedData(OgcApiDataV2 apiData) {

    if (apiData
        .getMetadata()
        .filter(
            md ->
                md.getLicense().isPresent()
                    && (md.getLicenseName().isEmpty() || md.getLicenseUrl().isEmpty()))
        .isPresent()) {
      ApiMetadata md = apiData.getMetadata().get();
      try {
        ListedLicense lic = LicenseInfoFactory.getListedLicenseById(md.getLicense().get());
        if (lic != null) {
          Optional<String> licenseName = lic.getName();
          String licenseUrl = lic.getObjectUri();
          return new ImmutableOgcApiDataV2.Builder()
              .from(apiData)
              .metadata(
                  new ImmutableApiMetadata.Builder()
                      .from(md)
                      .licenseName(licenseName)
                      .licenseUrl(licenseUrl)
                      .build())
              .build();
        }
      } catch (Exception e) {
        // ignore
      }
    }

    return apiData;
  }
}
