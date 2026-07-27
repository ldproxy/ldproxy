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
import de.ii.xtraplatform.features.domain.FeatureSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Closes the wrapper elements that {@code GmlWriterProperties} keeps open for {@code xmlPaths}
 * chain merging whenever an event follows that must not be encoded inside a wrapper chain: an
 * object or array boundary, a geometry, or the feature end. The closing must happen before the
 * writers that emit for these events, so this writer's sort priority (22) precedes {@code
 * GmlWriterPositionVariants} (25) and {@code GmlWriterGeometry} (30); unmapped value properties are
 * handled by {@code GmlWriterProperties} itself, and the feature end element is written by {@code
 * GmlWriterSkeleton} only after the writer chain has completed.
 *
 * <p>An object property that is itself mapped by a chain is the exception: instead of closing the
 * wrappers, this writer opens the object's chain and closes it again at the object's end, so that
 * {@code GmlWriterProperties} contributes neither the property element nor the object element.
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
    Optional<FeatureSchema> schema = context.schema().filter(FeatureSchema::isObject);
    if (schema.isPresent() && isXmlPathObject(context, schema.get())) {
      XmlPathWriter.openObject(
          context.encoding(),
          context.encoding().getXmlPaths().get(schema.get().getFullPathAsString()),
          schema.get());
    } else {
      context.encoding().closeXmlPathWrappers();
    }
    next.accept(context);
  }

  @Override
  public void onObjectEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    Optional<FeatureSchema> schema = context.schema().filter(FeatureSchema::isObject);
    if (schema.isPresent()
        && isXmlPathObject(context, schema.get())
        && XmlPathWriter.inObject(context.encoding())) {
      XmlPathWriter.closeObject(context.encoding());
    } else {
      context.encoding().closeXmlPathWrappers();
    }
    next.accept(context);
  }

  @Override
  public void onArrayStart(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    // an array of chained objects keeps the wrappers its members share open across the array
    if (!isXmlPathArray(context)) {
      context.encoding().closeXmlPathWrappers();
    }
    next.accept(context);
  }

  @Override
  public void onArrayEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    if (!isXmlPathArray(context)) {
      context.encoding().closeXmlPathWrappers();
    }
    next.accept(context);
  }

  private boolean isXmlPathArray(EncodingAwareContextGml context) {
    return context
        .schema()
        .filter(FeatureSchema::isObject)
        .filter(schema -> isXmlPathObject(context, schema))
        .isPresent();
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
    // no chain can span features, and the state outlives the feature
    XmlPathWriter.resetObjects(context.encoding());
    next.accept(context);
  }
}
