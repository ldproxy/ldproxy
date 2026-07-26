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

/**
 * Closes the wrapper elements that {@code GmlWriterProperties} keeps open for {@code xmlPaths}
 * chain merging whenever an event follows that must not be encoded inside a wrapper chain: an
 * object or array boundary, a geometry, or the feature end. The closing must happen before the
 * writers that emit for these events, so this writer's sort priority (22) precedes {@code
 * GmlWriterPositionVariants} (25) and {@code GmlWriterGeometry} (30); unmapped value properties are
 * handled by {@code GmlWriterProperties} itself, and the feature end element is written by {@code
 * GmlWriterSkeleton} only after the writer chain has completed.
 */
@Singleton
@AutoBind
public class GmlWriterXmlPaths implements GmlWriter {

  @Inject
  public GmlWriterXmlPaths() {}

  @Override
  public GmlWriterXmlPaths create() {
    return new GmlWriterXmlPaths();
  }

  @Override
  public int getSortPriority() {
    return 22;
  }

  @Override
  public void onObjectStart(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().closeXmlPathWrappers();
    next.accept(context);
  }

  @Override
  public void onObjectEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().closeXmlPathWrappers();
    next.accept(context);
  }

  @Override
  public void onArrayStart(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().closeXmlPathWrappers();
    next.accept(context);
  }

  @Override
  public void onArrayEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().closeXmlPathWrappers();
    next.accept(context);
  }

  @Override
  public void onGeometry(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().closeXmlPathWrappers();
    next.accept(context);
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    context.encoding().closeXmlPathWrappers();
    next.accept(context);
  }
}
