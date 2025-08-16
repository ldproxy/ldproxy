/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import java.io.IOException;
import java.util.function.Consumer;

@AutoMultiBind
public interface FeatureWriter<T extends EncodingAwareContext<?>> {

  int getSortPriority();

  default void onEvent(T context, Consumer<T> next) throws IOException {
    switch (context.encoding().getState().getEvent()) {
      case START:
        onStart(context, next);
        break;
      case END:
        onEnd(context, next);
        break;
      case FEATURE_START:
        onFeatureStart(context, next);
        break;
      case FEATURE_END:
        // first close the properties object and then the feature object
        onPropertiesEnd(context, next);
        onFeatureEnd(context, next);
        break;
      case PROPERTY:
        onValue(context, next);
        break;
      case ARRAY_START:
        onArrayStart(context, next);
        break;
      case OBJECT_START:
        onObjectStart(context, next);
        break;
      case OBJECT_END:
        onObjectEnd(context, next);
        break;
      case ARRAY_END:
        onArrayEnd(context, next);
        break;
      case GEOMETRY:
        onGeometry(context, next);
        break;
    }
  }

  default void onStart(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onEnd(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onFeatureStart(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onPropertiesEnd(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onFeatureEnd(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onArrayStart(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onObjectStart(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onObjectEnd(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onArrayEnd(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onValue(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }

  default void onGeometry(T context, Consumer<T> next) throws IOException {
    next.accept(context);
  }
}
