/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import static de.ii.xtraplatform.base.domain.util.LambdaWithException.consumerMayThrow;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGml;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.ogcapi.features.gml.domain.ModifiableStateGml;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@SuppressWarnings({
  "ConstantConditions",
  "PMD.TooManyMethods"
}) // reducing the number of methods results in complex methods and refactoring does not seem to
// improve the maintainability
@Singleton
@AutoBind
public class GmlWriterProperties implements GmlWriter {

  private static final String XML_NAME_ATTRIBUTE_SEPARATOR = "___";

  @Inject
  public GmlWriterProperties() {}

  @Override
  public GmlWriterProperties create() {
    return new GmlWriterProperties();
  }

  @Override
  public int getSortPriority() {
    return 40;
  }

  @Override
  public void onPropertiesEnd(
      EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next) {

    next.accept(context);
  }

  @Override
  public void onObjectStart(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    if (context.schema().filter(FeatureSchema::isObject).isPresent()) {
      FeatureSchema schema = context.schema().orElseThrow();

      String elementNameProperty = schema.getName();
      // Open the property element; its '>' is still pending (lazy emission)
      context.encoding().writeStartElement(elementNameProperty);

      String objectType = schema.getObjectType().orElse("FIX:ME");

      if ("Link".equals(objectType) || schema.isFeatureRef()) {
        // rel-as-link is the only profile supported by the GML format.
        // xlink:* attributes are added to this element by writeLinkAttribute()
        // and the element is closed by onObjectEnd().
        context.encoding().getState().setInLink(true);
      } else if ("Measure".equals(objectType)) {
        // The uom attribute and character value are written together once both
        // sub-properties have been seen (writeMeasure). The element is closed
        // by onObjectEnd().
        context.encoding().getState().setInMeasure(true);
        context.encoding().getState().setFirstMeasureProperty(Optional.empty());
      } else {
        // Normal GML object: <propName><objectElement placeholder>…</objectElement></propName>
        String elementNameObject = context.encoding().startGmlObject(schema);
        // writeStartElement for the inner element flushes the pending '>' of propName
        context.encoding().writeStartElement(elementNameObject);
        // Inject XML-attribute placeholder in the pending start tag of objectElement
        context.encoding().writeXmlAttPlaceholder();
        // Force the '>' of objectElement to be emitted
        context.encoding().closeStartElement();
      }
    }

    next.accept(context);
  }

  @Override
  public void onObjectEnd(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    if (context.schema().filter(FeatureSchema::isObject).isPresent()) {
      boolean inLink = context.encoding().getState().getInLink();
      boolean inMeasure = context.encoding().getState().getInMeasure();

      if (inLink) {
        // Closes <propName xlink:href="…" …/>
        context.encoding().writeEndElement();
        context.encoding().getState().setInLink(false);
      } else if (inMeasure) {
        // Closes <propName uom="…">value</propName>
        context.encoding().writeEndElement();
        context.encoding().getState().setInMeasure(false);
      } else {
        // Closes </objectElement> then </propName>
        context.encoding().writeEndElement();
        context.encoding().writeEndElement();
        context.encoding().closeGmlObject();
      }
    }

    next.accept(context);
  }

  @Override
  public void onValue(EncodingAwareContextGml context, Consumer<EncodingAwareContextGml> next)
      throws IOException {
    if (!shouldSkipProperty(context)) {
      FeatureSchema schema = context.schema().orElseThrow();
      String value = context.value();

      ModifiableStateGml state = context.encoding().getState();

      if (state.getVariableNameProperty().filter(p -> p.equals(schema.getName())).isEmpty()) {
        boolean inLink = state.getInLink();
        boolean inMeasure = state.getInMeasure();

        if (inLink) {
          writeLinkAttribute(context, schema.getName(), value);
        } else if (inMeasure) {
          writeMeasure(context, schema, value);
        } else {
          if (context.encoding().getXmlAttributes().contains(schema.getFullPathAsString())) {
            // encode as XML attribute of the parent object element
            context.encoding().writeAsXmlAtt(schema.getName(), value);
          } else {
            // <propName [name="…"] [uom="…"]>value</propName>
            String[] name = schema.getName().split(XML_NAME_ATTRIBUTE_SEPARATOR, 2);
            context.encoding().writeStartElement(name[0]);
            if (name.length == 2) {
              context.encoding().writeAttribute("name", name[1]);
            }
            writeUnitIfNecessary(context, schema);
            // writeCharacters emits the pending '>' and writes the (escaped) value
            writeValue(context, value, schema.getType());
            context.encoding().writeEndElement();
          }
        }
      } else {
        setVariableObjectElementName(context, schema, value);
      }
    }

    next.accept(context);
  }

  private void setVariableObjectElementName(
      EncodingAwareContextGml context, FeatureSchema schema, String value) {
    ModifiableStateGml state = context.encoding().getState();
    state
        .getVariableNameProperty()
        .ifPresent(
            p -> {
              // check for variable object element name property
              if (p.equals(schema.getName())) {
                String mappedValue =
                    Objects.requireNonNullElse(state.getVariableNameMapping().get(value), value);
                context.encoding().setCurrentObjectElement(mappedValue);
              }
            });
  }

  private void writeUnitIfNecessary(EncodingAwareContextGml context, FeatureSchema schema)
      throws IOException {
    if (schema.getType() == Type.FLOAT || schema.getType() == Type.INTEGER) {
      // write as gml:MeasureType, if we have a numeric property with a 'unit'
      // property in the provider schema
      schema
          .getUnit()
          .ifPresent(consumerMayThrow(uom -> context.encoding().writeAttribute("uom", uom)));
    }
  }

  private void writeMeasure(EncodingAwareContextGml context, FeatureSchema schema, String value)
      throws IOException {
    ModifiableStateGml state = context.encoding().getState();
    if (state.getFirstMeasureProperty().isPresent()) {
      String other = state.getFirstMeasureProperty().get();
      String uom = "uom".equals(schema.getName()) ? value : other;
      String val = "value".equals(schema.getName()) ? value : other;
      // Still in START_ELEMENT state for the property element: add uom attribute, then write value
      context.encoding().writeAttribute("uom", uom);
      writeValue(context, val, schema.getType());
    } else {
      state.setFirstMeasureProperty(Optional.ofNullable(value));
    }
  }

  private void writeLinkAttribute(
      EncodingAwareContextGml context, String xlinkAttribute, String value) throws IOException {
    // Still in START_ELEMENT state for the property element; add an xlink:* attribute.
    // XMLStreamWriter handles value escaping automatically.
    String attrValue;
    if ("href".equals(xlinkAttribute) && context.encoding().getAllLinksAreLocal()) {
      attrValue =
          context.encoding().getIdsIncludeCollectionId()
              ? String.format(
                  "#%s%s",
                  context.encoding().getGmlIdPrefix().orElse(""),
                  value.substring(value.indexOf("/collections/") + 13).replace("/items/", "."))
              : String.format(
                  "#%s%s",
                  context.encoding().getGmlIdPrefix().orElse(""),
                  value.substring(value.indexOf("/items/") + 7));
    } else {
      attrValue = value;
    }
    context.encoding().writeAttribute("xlink:" + xlinkAttribute, attrValue);
  }

  private boolean shouldSkipProperty(EncodingAwareContextGml context) {
    return !hasMappingAndValue(context) || context.schema().orElseThrow().isId();
  }

  private boolean hasMappingAndValue(EncodingAwareContextGml context) {
    return context.schema().filter(FeatureSchema::isValue).isPresent()
        && Objects.nonNull(context.value());
  }

  /**
   * Writes a property value using XMLStreamWriter, which handles XML escaping automatically.
   * Replaces the former manual {@code escapeText()} approach.
   */
  private void writeValue(EncodingAwareContextGml context, String value, Type type)
      throws IOException {
    if (type == Type.BOOLEAN) {
      boolean boolValue =
          Boolean.parseBoolean(value) || "t".equalsIgnoreCase(value) || "1".equals(value);
      context.encoding().writeCharacters(String.valueOf(boolValue));
    } else {
      context.encoding().writeCharacters(value);
    }
  }
}
