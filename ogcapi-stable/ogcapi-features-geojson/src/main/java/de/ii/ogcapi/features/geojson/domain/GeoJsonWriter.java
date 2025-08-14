/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeatureWriter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zahnen
 */
@AutoMultiBind
public interface GeoJsonWriter extends FeatureWriter<EncodingAwareContextGeoJson> {
  Logger LOGGER = LoggerFactory.getLogger(GeoJsonWriter.class);

  GeoJsonWriter create();

  default Optional<ProfileGeoJson> getProfile(FeatureTransformationContext transformationContext) {
    return transformationContext.getProfiles().stream()
        .filter(profile -> profile instanceof ProfileGeoJson)
        .map(profile -> (ProfileGeoJson) profile)
        .findFirst();
  }

  default boolean writeJsonFgExtensions(FeatureTransformationContext transformationContext) {
    return getProfile(transformationContext)
        .map(ProfileGeoJson::writeJsonFgExtensions)
        .orElse(false);
  }

  default boolean writeSecondaryGeometry(FeatureTransformationContext transformationContext) {
    return getProfile(transformationContext)
        .map(ProfileGeoJson::writeSecondaryGeometry)
        .orElse(false);
  }
}
