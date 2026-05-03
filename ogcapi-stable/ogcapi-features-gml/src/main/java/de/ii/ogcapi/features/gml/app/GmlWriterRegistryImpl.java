/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import dagger.Lazy;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.ogcapi.features.gml.domain.GmlWriterRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
@AutoBind
public class GmlWriterRegistryImpl implements GmlWriterRegistry {

  private final Lazy<Set<GmlWriter>> gmlWriters;

  @Inject
  public GmlWriterRegistryImpl(Lazy<Set<GmlWriter>> gmlWriters) {
    this.gmlWriters = gmlWriters;
  }

  @Override
  public List<GmlWriter> getWriters() {
    return ImmutableList.copyOf(gmlWriters.get());
  }
}
