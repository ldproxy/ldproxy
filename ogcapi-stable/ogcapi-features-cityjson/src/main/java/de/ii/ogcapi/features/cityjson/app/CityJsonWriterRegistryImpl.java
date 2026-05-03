/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.cityjson.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import dagger.Lazy;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriter;
import de.ii.ogcapi.features.cityjson.domain.CityJsonWriterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
@AutoBind
public class CityJsonWriterRegistryImpl implements CityJsonWriterRegistry {

  private final Lazy<Set<CityJsonWriter>> cityJsonWriters;

  @Inject
  public CityJsonWriterRegistryImpl(Lazy<Set<CityJsonWriter>> cityJsonWriters) {
    this.cityJsonWriters = cityJsonWriters;
  }

  @Override
  public List<CityJsonWriter> getCityJsonWriters() {
    return ImmutableList.copyOf(cityJsonWriters.get());
  }
}
