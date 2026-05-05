/*
 * Copyright 2022 interactive instruments GmbH
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.OptionalLong;
import java.util.function.Consumer;

@Singleton
@AutoBind
public class GmlWriterMetadata implements GmlWriter {

  @Inject
  public GmlWriterMetadata() {}

  @Override
  public GmlWriterMetadata create() {
    return new GmlWriterMetadata();
  }

  @Override
  public int getSortPriority() {
    return 20;
  }

  @Override
  public void onStart(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {

    if (context.encoding().isFeatureCollection()
        && context.encoding().getSupportsStandardResponseParameters()) {
      OptionalLong numberReturned = context.metadata().getNumberReturned();
      OptionalLong numberMatched = context.metadata().getNumberMatched();
      if (numberReturned.isPresent()) {
        context.encoding().write(" numberReturned=\"");
        context.encoding().write(String.valueOf(numberReturned.getAsLong()));
        context.encoding().write("\"");
      }
      if (numberMatched.isPresent()) {
        context.encoding().write(" numberMatched=\"");
        context.encoding().write(String.valueOf(numberMatched.getAsLong()));
        context.encoding().write("\"");
      }
      context.encoding().write(" timeStamp=\"");
      context.encoding().write(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
      context.encoding().write("\"");
    }

    next.accept(context);
  }
}
