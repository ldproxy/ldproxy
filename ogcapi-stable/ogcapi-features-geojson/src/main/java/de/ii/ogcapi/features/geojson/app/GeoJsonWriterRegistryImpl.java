/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import dagger.Lazy;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriter;
import de.ii.ogcapi.features.geojson.domain.GeoJsonWriterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;

/**
 * @author zahnen
 */
@Singleton
@AutoBind
public class GeoJsonWriterRegistryImpl implements GeoJsonWriterRegistry {

  private final Lazy<Set<GeoJsonWriter>> geoJsonWriters;

  @Inject
  public GeoJsonWriterRegistryImpl(Lazy<Set<GeoJsonWriter>> geoJsonWriters) {
    this.geoJsonWriters = geoJsonWriters;
  }

  @Override
  public List<GeoJsonWriter> getWriters() {
    return ImmutableList.copyOf(geoJsonWriters.get());
  }
}
