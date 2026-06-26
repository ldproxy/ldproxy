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
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.features.domain.transform.FeatureRefResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

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

      String elementNameProperty =
          context
              .encoding()
              .qualifyPropertyElementName(
                  schema.getName(), schema.getOriginObjectType().orElse(null));
      // For a feature reference declared in objectTypeSuffixedProperties, append a placeholder for
      // the _{objectType} suffix; it is resolved from the reference's type value (see onValue) or
      // removed at flush when the reference has no resolvable type.
      if (schema.isFeatureRef()
          && context
              .encoding()
              .getObjectTypeSuffixedProperties()
              .contains(schema.getFullPathAsString())) {
        elementNameProperty += context.encoding().beginRefSuffix();
      }
      // Open the property element; its '>' is still pending (lazy emission)
      context.encoding().writeStartElement(elementNameProperty);

      String objectType = schema.getObjectType().orElse("FIX:ME");

      if ("Link".equals(objectType) || schema.isFeatureRef()) {
        // rel-as-link is the only profile supported by the GML format.
        // xlink:* attributes are added to this element by writeLinkAttribute()
        // and the element is closed by onObjectEnd().
        context.encoding().getState().setInLink(true);
        if (schema.isFeatureRef()) {
          // GML renders references natively: collect the resolved id/title/type children and emit
          // xlink:href / xlink:title (and the element-name suffix) at onObjectEnd.
          context.encoding().getState().setInFeatureRef(true);
          context.encoding().getState().unsetRefValues();
        }
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
        if (context.encoding().getState().getInFeatureRef()) {
          // Emit the reference's xlink attributes (and resolve the element-name suffix) from the
          // collected id/title/type, then close <propName xlink:href="…"/>.
          writeFeatureRef(context);
        }
        context.encoding().writeEndElement();
        context.encoding().getState().setInLink(false);
        context.encoding().getState().setInFeatureRef(false);
        context.encoding().getState().setRefSuffixPending(false);
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
          if (state.getInFeatureRef()) {
            // Defer: the reference's xlink:href/title and element-name suffix are emitted together
            // at onObjectEnd, once id/title/type have all been collected (see writeFeatureRef).
            state.putRefValues(schema.getName(), value);
          } else {
            writeLinkAttribute(context, schema.getName(), value);
          }
        } else if (inMeasure) {
          writeMeasure(context, schema, value);
        } else {
          if (context.encoding().getXmlAttributes().contains(schema.getFullPathAsString())) {
            // encode as XML attribute of the parent object element
            context.encoding().writeAsXmlAtt(schema.getName(), value);
          } else if (context
              .encoding()
              .getCodelistProperties()
              .containsKey(schema.getFullPathAsString())) {
            writeCodelistXlink(context, schema, value);
          } else {
            // <propName [name="…"] [uom="…"]><wrap…>value</wrap…></propName>
            String[] name = schema.getName().split(XML_NAME_ATTRIBUTE_SEPARATOR, 2);
            context
                .encoding()
                .writeStartElement(
                    context
                        .encoding()
                        .qualifyPropertyElementName(
                            name[0], schema.getOriginObjectType().orElse(null)));
            if (name.length == 2) {
              context.encoding().writeAttribute("name", name[1]);
            }
            writeUnitIfNecessary(context, schema);
            List<String> wrapElements =
                context
                    .encoding()
                    .getValueWrap()
                    .getOrDefault(schema.getFullPathAsString(), List.of());
            for (String wrapEl : wrapElements) {
              context.encoding().writeStartElement(wrapEl);
              writeIso19139CodeListAttributes(context, schema, wrapEl, value);
            }
            // writeCharacters emits the pending '>' and writes the (escaped) value
            writeValue(context, value, schema.getType());
            for (int i = wrapElements.size() - 1; i >= 0; i--) {
              context.encoding().writeEndElement();
            }
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
          .ifPresent(
              consumerMayThrow(
                  uom -> context.encoding().writeAttribute("uom", context.encoding().mapUom(uom))));
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
      context.encoding().writeAttribute("uom", context.encoding().mapUom(uom));
      writeValue(context, val, schema.getType());
    } else {
      state.setFirstMeasureProperty(Optional.ofNullable(value));
    }
  }

  private void writeLinkAttribute(
      EncodingAwareContextGml context, String xlinkAttribute, String value) throws IOException {
    // Still in START_ELEMENT state for the property element; add an xlink:* attribute.
    // XMLStreamWriter handles value escaping automatically. This path serves literal "Link"
    // objects; feature references are encoded natively (see writeFeatureRef).
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

  /**
   * Encodes a feature reference natively as a GML xlink. Builds {@code xlink:href} from the
   * reference {@code id} (the only profile-independent way to render a reference in GML), emits
   * {@code xlink:title}, and resolves the {@code _<objectType>} element-name suffix from {@code
   * type} — instead of relying on the generic {@code rel} reduction, which would have collapsed the
   * reference to {@code {title, href}} and dropped the type. Called at object end, once all
   * children have been collected.
   */
  private void writeFeatureRef(EncodingAwareContextGml context) throws IOException {
    ModifiableStateGml state = context.encoding().getState();
    Map<String, String> ref = state.getRefValues();
    String href = buildFeatureRefHref(context, ref);
    if (href != null) {
      context.encoding().writeAttribute("xlink:href", href);
    }
    String title = ref.get(FeatureRefResolver.TITLE);
    if (title != null) {
      context.encoding().writeAttribute("xlink:title", title);
    }
    if (state.getRefSuffixPending()) {
      context.encoding().setCurrentRefSuffix(ref.get(FeatureRefResolver.TYPE));
    }
  }

  /**
   * Builds the {@code xlink:href} for a feature reference from its collected children, mirroring
   * the options the generic reduction offered: local fragment when all links are local, else the
   * configured {@code featureRefTemplate}, else a per-reference {@code uriTemplate}, else the
   * canonical {@code {serviceUrl}/collections/{type}/items/{id}}.
   */
  private String buildFeatureRefHref(EncodingAwareContextGml context, Map<String, String> ref) {
    String id = ref.get(FeatureRefResolver.ID);
    if (id == null) {
      return null;
    }
    String type = ref.get(FeatureRefResolver.TYPE);
    if (context.encoding().getAllLinksAreLocal()) {
      String prefix = context.encoding().getGmlIdPrefix().orElse("");
      return context.encoding().getIdsIncludeCollectionId() && type != null
          ? String.format("#%s%s.%s", prefix, type, id)
          : String.format("#%s%s", prefix, id);
    }
    Optional<String> template = context.encoding().getFeatureRefTemplate();
    if (template.isPresent()) {
      return template.get().replace("{{value}}", id);
    }
    String uriTemplate = ref.get(FeatureRefResolver.URI_TEMPLATE);
    if (uriTemplate != null) {
      return uriTemplate.replace("{{id}}", id).replace("{{type}}", type == null ? "" : type);
    }
    return String.format(
        "%s/collections/%s/items/%s",
        context.encoding().getServiceUrl(), type == null ? "" : type, id);
  }

  private void writeCodelistXlink(
      EncodingAwareContextGml context, FeatureSchema schema, String value) throws IOException {
    String[] name = schema.getName().split(XML_NAME_ATTRIBUTE_SEPARATOR, 2);
    context
        .encoding()
        .writeStartElement(
            context
                .encoding()
                .qualifyPropertyElementName(name[0], schema.getOriginObjectType().orElse(null)));
    if (name.length == 2) {
      context.encoding().writeAttribute("name", name[1]);
    }
    String propPath = schema.getFullPathAsString();
    Optional<String> href = context.encoding().resolveCodelistUri(propPath, value);
    if (href.isPresent()) {
      context.encoding().writeAttribute("xlink:href", href.get());
      context
          .encoding()
          .writeAttribute("xlink:title", context.encoding().resolveCodelistLabel(propPath, value));
      context.encoding().writeEndElement();
    } else {
      writeValue(context, value, schema.getType());
      context.encoding().writeEndElement();
    }
  }

  /**
   * For a property that references a codelist (via its {@code codelist} constraint) and is wrapped
   * in an element whose local name equals the codelist id, emits the ISO 19139 {@code codeList} and
   * {@code codeListValue} attributes on that wrapper, turning {@code
   * <gmd:CI_RoleCode>v</gmd:CI_RoleCode>} into {@code <gmd:CI_RoleCode
   * codeList="<base>#CI_RoleCode" codeListValue="v">v</gmd:CI_RoleCode>}. The {@code codeList} URI
   * is built from the configured {@code codeListUriTemplateIso19139} (with {@code {{codelistId}}}
   * replaced by the codelist id); when it is absent the method is a no-op. Must be called while the
   * wrapper element's start tag is still open (before any characters or child element).
   */
  private void writeIso19139CodeListAttributes(
      EncodingAwareContextGml context, FeatureSchema schema, String wrapElement, String value)
      throws IOException {
    Optional<String> codelistId = schema.getConstraints().flatMap(SchemaConstraints::getCodelist);
    if (codelistId.isEmpty()) {
      return;
    }
    Optional<String> template = context.encoding().getCodeListUriTemplateIso19139();
    if (template.isEmpty()) {
      return;
    }
    int colon = wrapElement.indexOf(':');
    String localName = colon < 0 ? wrapElement : wrapElement.substring(colon + 1);
    if (!localName.equals(codelistId.get())) {
      return;
    }
    context
        .encoding()
        .writeAttribute("codeList", template.get().replace("{{codelistId}}", codelistId.get()));
    context.encoding().writeAttribute("codeListValue", value);
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
