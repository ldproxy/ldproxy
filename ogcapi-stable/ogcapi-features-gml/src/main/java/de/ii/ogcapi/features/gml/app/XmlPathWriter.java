/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.gml.domain.FeatureTransformationContextGml;
import de.ii.ogcapi.features.gml.domain.XmlPathElement;
import de.ii.ogcapi.features.gml.domain.XmlPathObjectFrame;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Emits the element chains of the {@code xmlPaths} option and maintains the state that lets
 * consecutive properties share their leading elements. {@code GmlWriterProperties} uses it for
 * values, {@code GmlWriterXmlPaths} for the objects whose chain replaces the property and object
 * elements the schema would contribute.
 */
final class XmlPathWriter {

  private XmlPathWriter() {}

  /**
   * Aligns the open wrappers with {@code prefix}: the leading segments both have in common stay
   * open, every open element below them is closed. Returns the number of shared segments, i.e. the
   * index from which the caller opens the remainder.
   *
   * <p>{@code reopenOnRepeat} decides what a repeated occurrence of {@code path} means. For a value
   * it shares nothing, so each value of a multi-valued property gets its own wrapper instances. For
   * an object chain the repetition marker already says which segments repeat, and the segments
   * before it have to survive from one member to the next.
   */
  static int closeToShared(
      FeatureTransformationContextGml encoding,
      List<XmlPathElement> prefix,
      String path,
      boolean reopenOnRepeat)
      throws IOException {
    List<XmlPathElement> open = encoding.getState().getXmlPathOpenPrefix();
    int shared = 0;
    if (!reopenOnRepeat || encoding.getState().getXmlPathOwner().filter(path::equals).isEmpty()) {
      while (shared < open.size()
          && shared < prefix.size()
          && open.get(shared).equals(prefix.get(shared))) {
        shared++;
      }
    }
    for (int i = open.size() - 1; i >= shared; i--) {
      if (!open.get(i).isEmptyElement()) {
        encoding.writeEndElement();
      }
    }
    return shared;
  }

  /**
   * Writes one chain segment: its start tag with the configured attributes. An injected empty
   * element (trailing {@code /}) is closed immediately; every other element stays open with its
   * '&gt;' still pending, so further attributes can be added to it.
   */
  static void writeElement(FeatureTransformationContextGml encoding, XmlPathElement element)
      throws IOException {
    encoding.writeStartElement(element.getName());
    for (Map.Entry<String, String> attribute : element.getAttributes().entrySet()) {
      encoding.writeAttribute(attribute.getKey(), attribute.getValue());
    }
    if (element.isEmptyElement()) {
      encoding.writeEndElement();
    }
  }

  /**
   * Opens the element chain of an object property. The segments before the repetition marker are
   * merged with the wrappers already open; the segments from the marker on are opened for this
   * occurrence, the innermost of them holding the object's properties. The enclosing wrapper scope
   * is saved and the members start with a scope of their own, so their chains merge among
   * themselves and never close an element belonging to the object.
   */
  static void openObject(
      FeatureTransformationContextGml encoding, List<XmlPathElement> chain, FeatureSchema schema)
      throws IOException {
    String path = schema.getFullPathAsString();
    int repeatAt = repeatIndex(chain);
    List<XmlPathElement> shared = ImmutableList.copyOf(chain.subList(0, repeatAt));
    List<XmlPathElement> repeating = ImmutableList.copyOf(chain.subList(repeatAt, chain.size()));
    int reused = closeToShared(encoding, shared, path, false);
    for (int i = reused; i < shared.size(); i++) {
      writeElement(encoding, shared.get(i));
    }
    encoding
        .getState()
        .setXmlPathObjectStack(
            ImmutableList.<XmlPathObjectFrame>builder()
                .addAll(encoding.getState().getXmlPathObjectStack())
                .add(new XmlPathObjectFrame(shared, repeating, path))
                .build());
    for (XmlPathElement element : repeating) {
      writeElement(encoding, element);
    }
    // the innermost element takes the role of the object element: push the object so that the
    // members' XML-attribute placeholder is anchored to it and not to the enclosing object. The
    // element name startGmlObject derives is not used — the chain already wrote the element.
    encoding.startGmlObject(schema);
    encoding.writeXmlAttPlaceholder();
    encoding.closeStartElement();
    encoding.getState().setXmlPathOpenPrefix(ImmutableList.of());
    encoding.getState().setXmlPathOwner(Optional.empty());
  }

  /**
   * Closes the element chain of an object property opened by {@link #openObject}: the members' own
   * wrappers first, then the repeated segments. The shared segments stay open for the next member
   * and for a following property whose chain starts the same way.
   */
  static void closeObject(FeatureTransformationContextGml encoding) throws IOException {
    List<XmlPathObjectFrame> stack = encoding.getState().getXmlPathObjectStack();
    XmlPathObjectFrame frame = stack.get(stack.size() - 1);
    encoding.closeXmlPathWrappers();
    for (int i = frame.getRepeating().size() - 1; i >= 0; i--) {
      if (!frame.getRepeating().get(i).isEmptyElement()) {
        encoding.writeEndElement();
      }
    }
    encoding.closeGmlObject();
    encoding
        .getState()
        .setXmlPathObjectStack(ImmutableList.copyOf(stack.subList(0, stack.size() - 1)));
    encoding.getState().setXmlPathOpenPrefix(frame.getShared());
    encoding.getState().setXmlPathOwner(Optional.of(frame.getPath()));
  }

  /** Whether an object chain is currently open, i.e. whether {@link #closeObject} has a frame. */
  static boolean inObject(FeatureTransformationContextGml encoding) {
    return !encoding.getState().getXmlPathObjectStack().isEmpty();
  }

  /** Drops the open object chains at the feature boundary, which no chain may cross. */
  static void resetObjects(FeatureTransformationContextGml encoding) {
    encoding.getState().setXmlPathObjectStack(ImmutableList.of());
  }

  /** The segment the chain repeats from; without a marker the whole chain repeats. */
  private static int repeatIndex(List<XmlPathElement> chain) {
    for (int i = 0; i < chain.size(); i++) {
      if (chain.get(i).repeats()) {
        return i;
      }
    }
    return 0;
  }
}
