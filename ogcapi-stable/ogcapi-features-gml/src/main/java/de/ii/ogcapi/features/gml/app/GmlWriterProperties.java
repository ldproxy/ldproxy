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
import javax.inject.Inject;
import javax.inject.Singleton;

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
      context.encoding().write("<");
      context.encoding().write(elementNameProperty);

      String objectType = schema.getObjectType().orElse("FIX:ME");

      if ("Link".equals(objectType) || schema.isFeatureRef()) {
        // rel-as-link is the only profile supported by the GML format
        context.encoding().getState().setInLink(true);
      } else if ("Measure".equals(objectType)) {
        context.encoding().getState().setInMeasure(true);
        context.encoding().getState().setFirstMeasureProperty(Optional.empty());
        context.encoding().pushElement(elementNameProperty);
      } else {
        String elementNameObject = context.encoding().startGmlObject(schema);

        context.encoding().write("><");
        context.encoding().write(elementNameObject);
        context.encoding().writeXmlAttPlaceholder();
        context.encoding().write(">");

        context.encoding().pushElement(elementNameProperty, elementNameObject);
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
        context.encoding().write("/>");
        context.encoding().getState().setInLink(false);
      } else if (inMeasure) {
        context.encoding().write("</");
        context.encoding().write(context.encoding().popElement());
        context.encoding().write(">");
        context.encoding().getState().setInMeasure(false);
      } else {
        context.encoding().write("</");
        context.encoding().write(context.encoding().popElement());
        context.encoding().write(">");

        context.encoding().write("</");
        context.encoding().write(context.encoding().popElement());
        context.encoding().write(">");

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
            // encode as XML attribute
            context.encoding().writeAsXmlAtt(schema.getName(), value);
          } else {
            // opening tag of property element
            context.encoding().write("<");
            String[] name = schema.getName().split(XML_NAME_ATTRIBUTE_SEPARATOR, 2);
            context.encoding().write(name[0]);
            if (name.length == 2) {
              context.encoding().write(" name=\"");
              context.encoding().write(name[1]);
              context.encoding().write("\"");
            }
            writeUnitIfNecessary(context, schema);
            context.encoding().write(">");

            // value
            writeValue(context, value, schema.getType());

            // closing tag
            context.encoding().write("</");
            context.encoding().write(name[0]);
            context.encoding().write(">");
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

  private void writeUnitIfNecessary(EncodingAwareContextGml context, FeatureSchema schema) {
    if (schema.getType() == Type.FLOAT || schema.getType() == Type.INTEGER) {
      // write as gml:MeasureType, if we have a numeric property with a 'unit'
      // property in the provider schema
      schema
          .getUnit()
          .ifPresent(
              consumerMayThrow(
                  uom -> {
                    context.encoding().write(" uom=\"");
                    context.encoding().write(uom);
                    context.encoding().write("\"");
                  }));
    }
  }

  private void writeMeasure(EncodingAwareContextGml context, FeatureSchema schema, String value) {
    ModifiableStateGml state = context.encoding().getState();
    state
        .getFirstMeasureProperty()
        .ifPresentOrElse(
            consumerMayThrow(
                other -> {
                  String uom = "uom".equals(schema.getName()) ? value : other;
                  String val = "value".equals(schema.getName()) ? value : other;
                  context.encoding().write(" uom=\"");
                  context.encoding().write(uom);
                  context.encoding().write("\"");
                  context.encoding().write(">");
                  writeValue(context, val, schema.getType());
                }),
            () -> state.setFirstMeasureProperty(Optional.ofNullable(value)));
  }

  private void writeLinkAttribute(
      EncodingAwareContextGml context, String xlinkAttribute, String value) {
    // we are already in the property element that has the link object as its value,
    // just add the XLink attribute
    context.encoding().write(" xlink:");
    context.encoding().write(xlinkAttribute);
    context.encoding().write("=\"");
    if (xlinkAttribute.equals("href") && context.encoding().getAllLinksAreLocal()) {
      String localHref =
          context.encoding().getIdsIncludeCollectionId()
              ? String.format(
                  "#%s%s",
                  context.encoding().getGmlIdPrefix().orElse(""),
                  value.substring(value.indexOf("/collections/") + 13).replace("/items/", "."))
              : String.format(
                  "#%s%s",
                  context.encoding().getGmlIdPrefix().orElse(""),
                  value.substring(value.indexOf("/items/") + 7));
      writeValue(context, localHref, Type.STRING);
    } else {
      writeValue(context, value, Type.STRING);
    }
    context.encoding().write("\"");
  }

  private boolean shouldSkipProperty(EncodingAwareContextGml context) {
    return !hasMappingAndValue(context) || context.schema().orElseThrow().isId();
  }

  private boolean hasMappingAndValue(EncodingAwareContextGml context) {
    return context.schema().filter(FeatureSchema::isValue).isPresent()
        && Objects.nonNull(context.value());
  }

  private void writeValue(EncodingAwareContextGml context, String value, Type type) {
    if (type == Type.BOOLEAN) {
      context
          .encoding()
          .write(Boolean.parseBoolean(value) || value.equalsIgnoreCase("t") || value.equals("1"));
    } else {
      context.encoding().write(escapeText(value));
    }
  }

  private String escapeText(String text) {
    return text.replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&apos;");
  }
}
