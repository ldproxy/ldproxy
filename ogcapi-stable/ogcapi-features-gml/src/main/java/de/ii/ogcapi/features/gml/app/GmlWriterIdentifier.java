/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.function.Consumer;

@Singleton
@AutoBind
public class GmlWriterIdentifier implements GmlWriter {

  @Inject
  public GmlWriterIdentifier() {}

  @Override
  public GmlWriterIdentifier create() {
    return new GmlWriterIdentifier();
  }

  @Override
  public int getSortPriority() {
    return 15;
  }

  @Override
  public void onFeatureStart(
      EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next) throws IOException {
    context.encoding().writeGmlIdentifierElement();
    next.accept(context);
  }
}
