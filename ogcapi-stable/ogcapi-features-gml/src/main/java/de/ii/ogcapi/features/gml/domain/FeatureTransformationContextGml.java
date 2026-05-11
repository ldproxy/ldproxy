/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.gml.domain.GmlVersion;
import de.ii.xtraplatform.geometries.domain.GeometryType;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.immutables.value.Value;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({
  "ConstantConditions",
  "PMD.TooManyMethods"
}) // this class needs that many methods, a refactoring makes no sense
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public abstract class FeatureTransformationContextGml implements FeatureTransformationContext {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(FeatureTransformationContextGml.class);

  private static final String XML_ATTRIBUTE_PLACEHOLDER = "_zz_XML_ATTRIBUTE_i_zz_";
  private static final String GML_ID_PLACEHOLDER = "_zz_GML_ID_i_zz_";
  private static final String GML_IDENTIFIER_VALUE_PLACEHOLDER = "_zz_GML_IDENTIFIER_VALUE_i_zz_";
  private static final String OBJECT_ELEMENT_PLACEHOLDER = "_zz_OBJECT_ELEMENT_i_zz_";
  private static final String SURFACE_MEMBER_PLACEHOLDER = "_zz_SURFACE_MEMBER_i_zz_";

  /**
   * Internal string buffer to buffer information. The buffer is flushed for every feature. Also
   * used by GeometryEncoderGml which requires a StringBuilder.
   */
  @SuppressWarnings({
    "PMD.AvoidStringBufferField"
  }) // memory leak is not a risk and we need to incrementally build a string
  private final StringBuilder buffer = new StringBuilder();

  private final XMLStreamWriter xmlWriter;

  FeatureTransformationContextGml() {
    try {
      Writer writer =
          new Writer() {
            @Override
            public void write(char @NonNull [] cbuf, int off, int len) {
              buffer.append(cbuf, off, len);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
          };
      XMLOutputFactory factory = XMLOutputFactory.newInstance();
      xmlWriter = factory.createXMLStreamWriter(writer);
    } catch (XMLStreamException e) {
      throw new IllegalStateException("Failed to create XML stream writer", e);
    }
  }

  @Override
  @Value.Default
  // This is never null, but the parent is marked as nullable.
  // Warnings about potential NullPointerExceptions can be ignored.
  public ModifiableStateGml getState() {
    return ModifiableStateGml.create();
  }

  public abstract GmlVersion getGmlVersion();

  public abstract Map<String, String> getNamespaces();

  public abstract Map<String, String> getSchemaLocations();

  public abstract Optional<String> getDefaultNamespace();

  public abstract Map<String, String> getObjectTypeNamespaces();

  public abstract Map<String, VariableName> getVariableObjectElementNames();

  public abstract Optional<String> getFeatureCollectionElementName();

  public abstract Optional<String> getFeatureMemberElementName();

  public abstract boolean getSupportsStandardResponseParameters();

  public abstract boolean getGmlIdOnGeometries();

  public abstract boolean getSrsDimension();

  public abstract boolean getUseSurfaceAndCurve();

  public abstract List<String> getXmlAttributes();

  public abstract Optional<String> getGmlIdPrefix();

  public abstract Optional<GmlConfiguration.SrsNameStyle> getSrsNameStyle();

  public abstract List<SrsNameMapping> getSrsNameMappings();

  public abstract Optional<GmlConfiguration.UomStyle> getUomStyle();

  public abstract List<UomMapping> getUomMappings();

  public abstract Optional<String> getFeatureRefTemplate();

  public abstract Optional<GmlIdentifier> getGmlIdentifier();

  @Value.Default
  public boolean getAppendTemporalSuffixToGmlId() {
    return false;
  }

  public abstract Optional<String> getCodelistUriTemplate();

  @Value.Default
  public Map<String, String> getCodelistProperties() {
    return ImmutableMap.of();
  }

  @Value.Default
  public Map<String, Codelist> getCodelists() {
    return ImmutableMap.of();
  }

  @Value.Derived
  @Value.Auxiliary
  public Optional<String> resolveCodelistUri(String propPath, String value) {
    if (value == null || getCodelistUriTemplate().isEmpty()) {
      return Optional.empty();
    }
    String codelistId = getCodelistProperties().get(propPath);
    if (codelistId == null) {
      return Optional.empty();
    }
    return Optional.of(
        getCodelistUriTemplate()
            .get()
            .replace("{{codelistId}}", codelistId)
            .replace("{{value}}", value));
  }

  @Value.Derived
  @Value.Auxiliary
  public String resolveCodelistLabel(String propPath, String value) {
    if (value == null) {
      return value;
    }
    String codelistId = getCodelistProperties().get(propPath);
    if (codelistId == null) {
      return value;
    }
    Codelist cl = getCodelists().get(codelistId);
    return cl == null ? value : cl.getValue(value);
  }

  @Value.Derived
  @Value.Auxiliary
  public String mapUom(String raw) {
    if (raw == null
        || getUomStyle().orElse(GmlConfiguration.UomStyle.RAW)
            != GmlConfiguration.UomStyle.TEMPLATE) {
      return raw;
    }
    for (UomMapping m : getUomMappings()) {
      if (raw.equals(m.getUom())) {
        return m.getValue();
      }
    }
    return raw;
  }

  /** Returns the underlying buffer, used by GeometryEncoderGml which requires a StringBuilder. */
  @Value.Derived
  @Value.Auxiliary
  public XMLStreamWriter getWriter() {
    return xmlWriter;
  }

  // -------------------------------------------------------------------------
  // XMLStreamWriter wrapper methods
  // -------------------------------------------------------------------------

  /** Write the XML prolog */
  public void writeProlog() throws IOException {
    try {
      xmlWriter.writeStartDocument("UTF-8", "1.0");
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /** Write the XML prolog */
  public void endDocument() throws IOException {
    try {
      xmlWriter.writeEndDocument();
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * Writes an XML start element. Supports qualified names (prefix:localName); the namespace URI is
   * resolved from {@link #getNamespaces()}.
   */
  public void writeStartElement(String qualifiedName) throws IOException {
    try {
      int colonIdx = qualifiedName.indexOf(':');
      if (colonIdx == -1) {
        xmlWriter.writeStartElement(qualifiedName);
      } else {
        String prefix = qualifiedName.substring(0, colonIdx);
        String localName = qualifiedName.substring(colonIdx + 1);
        String nsUri = getNamespaces().getOrDefault(prefix, "");
        xmlWriter.writeStartElement(prefix, localName, nsUri);
      }
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /** Writes the closing tag for the current element. */
  public void writeEndElement() throws IOException {
    try {
      xmlWriter.writeEndElement();
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * Writes an XML attribute on the current element. Supports qualified names (prefix:localName);
   * the namespace URI is resolved from {@link #getNamespaces()}.
   */
  public void writeAttribute(String name, String value) throws IOException {
    try {
      int colonIdx = name.indexOf(':');
      if (colonIdx == -1) {
        xmlWriter.writeAttribute(name, value);
      } else {
        String prefix = name.substring(0, colonIdx);
        String localName = name.substring(colonIdx + 1);
        String nsUri = getNamespaces().getOrDefault(prefix, "");
        xmlWriter.writeAttribute(prefix, nsUri, localName, value);
      }
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /** Writes a namespace declaration (xmlns:prefix="uri") on the current element. */
  public void writeNamespace(String prefix, String uri) throws IOException {
    try {
      xmlWriter.writeNamespace(prefix, uri);
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /** Writes a default namespace declaration (xmlns="uri") on the current element. */
  public void writeDefaultNamespace(String uri) throws IOException {
    try {
      xmlWriter.writeDefaultNamespace(uri);
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * Writes character content with automatic XML escaping (e.g. {@code &} → {@code &amp;}). Replaces
   * the former manual {@code escapeText()} approach.
   */
  public void writeCharacters(String text) throws IOException {
    try {
      xmlWriter.writeCharacters(text);
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * Forces the pending {@code >} of the current start element to be emitted. Must be called after
   * all {@link #writeAttribute} / {@link #writeNamespace} calls and before any raw buffer writes
   * that need to appear in element content (not attribute) position.
   */
  public void closeStartElement() throws IOException {
    try {
      xmlWriter.writeCharacters("");
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  // -------------------------------------------------------------------------
  // Raw write – used only for the XML prolog and placeholder injection
  // -------------------------------------------------------------------------

  /**
   * Appends a raw string directly to the underlying buffer, bypassing XMLStreamWriter. Use only for
   * the placeholder strings that are replaced before output.
   */
  public void write(String value) {
    buffer.append(value);
  }

  // -------------------------------------------------------------------------
  // Flush
  // -------------------------------------------------------------------------

  /**
   * Write the contents of the string buffer to response stream. Before this requires that we first
   * process all placeholders in the buffer. Placeholders are inserted when we have to write
   * content, but do not yet know it. There are three cases:
   *
   * <p>1. The gml:id of a feature is part of the feature element, but we only know the tag once we
   * have processed the properties.
   *
   * <p>2. Properties that are mapped to XML attributes have to be written to the parent GML object
   * element.
   *
   * <p>3. If the tag of a GML object element is determined from a property, we only know the tag
   * once we have processed the properties.
   *
   * @throws IOException the buffer could not be written to the stream
   */
  public void flush() throws IOException {
    try {
      xmlWriter.flush();
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }

    if (!buffer.isEmpty()) {
      // replace placeholders
      getState()
          .getPlaceholders()
          .forEach(
              (key, value) -> {
                // Most placeholders cannot be empty - if they are, there is an error that
                // should be reported
                if (!key.startsWith("_zz_XML_ATTRIBUTE_") && value.isEmpty()) {
                  return;
                }
                // variable object elements appear twice - opening and closing tag
                IntStream.rangeClosed(1, 2)
                    .forEach(
                        i -> {
                          int idx = buffer.lastIndexOf(key);
                          if (idx != -1) {
                            buffer.replace(idx, idx + key.length(), value);
                          }
                        });
              });

      int unresolvedPos = buffer.indexOf("_zz_");
      if (unresolvedPos == -1) {
        getOutputStream().write(buffer.toString().getBytes());
      } else {
        if (LOGGER.isErrorEnabled()) {
          LOGGER.error(
              "GML feature buffer for a feature contains unresolved placeholders, the feature is ignored: {}",
              buffer.substring(0, Math.min(unresolvedPos + 50, buffer.length())));
        }
      }

      // clear the buffer and the placeholder information
      getState().unsetPlaceholders();
      buffer.setLength(0);
    }
  }

  // -------------------------------------------------------------------------
  // GML object / element management
  // -------------------------------------------------------------------------

  /**
   * Properties can be mapped to XML attributes of the parent GML object. Since GML object elements
   * are nested, we need to maintain a stack with the current GML object elements.
   *
   * <p>This method writes a unique placeholder for the XML attributes so that the XML attributes
   * can be added later when the object properties are processed. This method has to be called after
   * {@link #writeStartElement(String)} and any known {@link #writeAttribute} calls, but before
   * {@link #closeStartElement()}, so that the placeholder appears in attribute position.
   */
  @Value.Auxiliary
  public void writeXmlAttPlaceholder() {
    int i = getState().getLastObject();
    String xmlAttPlaceholder = XML_ATTRIBUTE_PLACEHOLDER.replace("i", String.valueOf(i));
    // Raw write so the placeholder appears inside the start tag (attribute position)
    write(xmlAttPlaceholder);
    getState().putPlaceholders(xmlAttPlaceholder, "");
  }

  /**
   * Properties can be mapped to XML attributes of the parent GML object. Since GML object elements
   * are nested, we need to maintain a stack with the current GML object elements.
   *
   * <p>This method writes an XML attribute to the current GML object element.
   *
   * @param name the name of the XML attribute
   * @param value the value of the XML attribute
   */
  public void writeAsXmlAtt(String name, String value) {
    // get the current object index to determine the placeholder
    List<Integer> indices = getState().getObjects();
    int idx = indices.get(indices.size() - 1);
    String xmlAttPlaceholder = XML_ATTRIBUTE_PLACEHOLDER.replace("i", String.valueOf(idx));
    String current = getState().getPlaceholders().get(xmlAttPlaceholder);
    if (Objects.isNull(current)) {
      throw new IllegalStateException(
          String.format("Placeholder '%s' not set in GML output.", xmlAttPlaceholder));
    }
    getState().putPlaceholders(xmlAttPlaceholder, current + " " + name + "=\"" + value + "\"");
  }

  /**
   * The gml:id of a feature is part of the feature element, but we only know the value once we have
   * processed the properties.
   *
   * <p>Writes a {@code gml:id} attribute whose value is a unique placeholder, to be replaced when
   * the ID property is processed.
   */
  @Value.Auxiliary
  public void writeGmlIdAttribute() throws IOException {
    int i = getState().getLastObject();
    String placeholder = GML_ID_PLACEHOLDER.replace("i", String.valueOf(i));
    writeAttribute(getGmlPrefix() + ":id", placeholder);
  }

  /**
   * The gml:id of a feature is part of the feature element, but we only know the tag once we have
   * processed the properties.
   *
   * <p>This method writes the gml:id attribute to the current GML feature element.
   *
   * @param value the value of the ID property of the current feature
   */
  public void setCurrentGmlId(String value) {
    int i = getState().getLastObject();
    getState().putCurrentRawGmlIds(i, value);
    String suffix = getState().getCurrentGmlIdSuffixes().getOrDefault(i, "");
    getState().putPlaceholders(GML_ID_PLACEHOLDER.replace("i", String.valueOf(i)), value + suffix);
    getGmlIdentifier()
        .ifPresent(
            cfg -> {
              String resolved =
                  cfg.getValueTemplate() == null
                      ? value
                      : cfg.getValueTemplate().replace("{{value}}", value);
              getState()
                  .putPlaceholders(
                      GML_IDENTIFIER_VALUE_PLACEHOLDER.replace("i", String.valueOf(i)), resolved);
            });
  }

  public void appendGmlIdSuffix(String suffix) {
    int i = getState().getLastObject();
    getState().putCurrentGmlIdSuffixes(i, suffix);
    String raw = getState().getCurrentRawGmlIds().get(i);
    if (raw != null) {
      getState().putPlaceholders(GML_ID_PLACEHOLDER.replace("i", String.valueOf(i)), raw + suffix);
    }
  }

  @Value.Auxiliary
  public void writeGmlIdentifierElement() throws IOException {
    if (getGmlIdentifier().isEmpty()) {
      return;
    }
    GmlIdentifier cfg = getGmlIdentifier().get();
    int i = getState().getLastObject();
    String placeholder = GML_IDENTIFIER_VALUE_PLACEHOLDER.replace("i", String.valueOf(i));
    getState().putPlaceholders(placeholder, "");
    writeStartElement(getGmlPrefix() + ":identifier");
    writeAttribute("codeSpace", cfg.getCodeSpace());
    writeCharacters(placeholder);
    writeEndElement();
  }

  /** Get the gml:id of the current feature. */
  @Value.Auxiliary
  public String getCurrentGmlId() {
    int i = getState().getObjects().get(0);
    return getState().getPlaceholders().get(GML_ID_PLACEHOLDER.replace("i", String.valueOf(i)));
  }

  /**
   * The name of a GML object element may depend on some property value. Since GML object elements
   * are nested, we need to maintain a stack with the current GML object elements.
   *
   * <p>This method adds a new GML object element for the current object to the stack.
   *
   * <p>If the element has a fixed name that name is used, otherwise a unique placeholder for the
   * element name is returned, so that the element name can be updated later when the object
   * properties are processed.
   *
   * @param schema the OBJECT schema that is mapped to a GML object
   */
  public String startGmlObject(FeatureSchema schema) {
    String objectType = schema.getObjectType().orElse("FIX:ME");
    VariableName variableName = getVariableObjectElementNames().get(objectType);

    int idx = getState().getLastObject() + 1;
    getState().setLastObject(idx);
    getState().addObjects(idx);

    String elementName;
    if (Objects.nonNull(variableName)) {
      int i = getState().getLastObject();
      elementName = OBJECT_ELEMENT_PLACEHOLDER.replace("i", String.valueOf(i));
      getState().setVariableNameProperty(variableName.getProperty());
      getState().putAllVariableNameMapping(variableName.getMapping());
    } else {
      Optional<String> nsPrefix = Optional.ofNullable(getObjectTypeNamespaces().get(objectType));
      elementName = nsPrefix.map(s -> s + ":" + objectType).orElse(objectType);
      getState().setVariableNameProperty(Optional.empty());
      getState().unsetVariableNameMapping();
    }
    return elementName;
  }

  /**
   * The name of a GML object element may depend on some property value. Since GML object elements
   * are nested, we need to maintain a stack with the current GML object elements.
   *
   * <p>This method removes the innermost GML object element from the stack.
   */
  @Value.Auxiliary
  public void closeGmlObject() {
    List<Integer> indices = getState().getObjects();
    int idx = indices.size() - 1;
    List<Integer> newList = ImmutableList.copyOf(indices.subList(0, idx));
    if (newList.isEmpty()) {
      getState().unsetObjects();
    } else {
      getState().setObjects(newList);
    }
  }

  /**
   * The name of a GML object element may depend on some property value. Since GML object elements
   * are nested, we need to maintain a stack with the current GML object elements.
   *
   * <p>This method replaces the placeholder element name with the proper element name.
   *
   * @param value the qualified element name
   */
  public void setCurrentObjectElement(String value) {
    int i = getState().getLastObject();
    getState().putPlaceholders(OBJECT_ELEMENT_PLACEHOLDER.replace("i", String.valueOf(i)), value);
  }

  /**
   * This capability is specific to CityGML LoD2 buildings.
   *
   * <p>The gml:ids of the gml:Polygon elements that make up the shell of the LoD 2 solid and that
   * must be referenced from the gml:Solid geometry will only be known when those polygons are
   * written as part of the boundary surfaces.
   *
   * <p>This method writes a unique placeholder for the gml:surfaceMember elements so that they can
   * be added later when the polygons are processed.
   */
  @Value.Auxiliary
  public void writeSurfaceMemberPlaceholder() throws IOException {
    int i = getState().getLastObject();
    String surfaceMemberPlaceholder = SURFACE_MEMBER_PLACEHOLDER.replace("i", String.valueOf(i));
    closeStartElement();
    // Raw write: placeholder contains no XML special chars and is replaced before output
    write(surfaceMemberPlaceholder);
    getState().putPlaceholders(surfaceMemberPlaceholder, "");
  }

  /**
   * This capability is specific to CityGML LoD2 buildings.
   *
   * <p>The gml:ids of the gml:Polygon elements that make up the shell of the LoD 2 solid and that
   * must be referenced from the gml:Solid geometry will only be known when those polygons are
   * written as part of the boundary surfaces.
   *
   * <p>This method adds another gml:surfaceMember element to the placeholder.
   */
  public void writeAsSurfaceMemberLink(String elementName, String polygonId) {
    // get the current object index to determine the placeholder
    List<Integer> indices = getState().getObjects();
    int idx = indices.get(0);
    String surfaceMemberPlaceholder = SURFACE_MEMBER_PLACEHOLDER.replace("i", String.valueOf(idx));
    String current = getState().getPlaceholders().get(surfaceMemberPlaceholder);
    if (Objects.isNull(current)) {
      throw new IllegalStateException(
          String.format("Placeholder '%s' not set in GML output.", surfaceMemberPlaceholder));
    }
    getState()
        .putPlaceholders(
            surfaceMemberPlaceholder,
            current + "<" + elementName + " xlink:href=\"#" + polygonId + "\"/>");
  }

  @Value.Derived
  @Value.Auxiliary
  public String getGmlPrefix() {
    final GmlVersion v = getGmlVersion();
    if (GmlVersion.GML21.equals(v)) {
      return "gml21";
    }
    if (GmlVersion.GML31.equals(v)) {
      return "gml31";
    }
    return "gml";
  }

  @Value.Modifiable
  public abstract static class StateGml extends State {

    @Value.Default
    public boolean getInLink() {
      return false;
    }

    @Value.Default
    public boolean getInFeatureRef() {
      return false;
    }

    @Value.Default
    public boolean getInMeasure() {
      return false;
    }

    public abstract Optional<String> getFirstMeasureProperty();

    @Value.Default
    public boolean getInGeometry() {
      return false;
    }

    @Value.Default
    public boolean getCompositeGeometry() {
      return false;
    }

    @Value.Default
    public boolean getClosedGeometry() {
      return false;
    }

    @Value.Default
    public boolean getDeferredSolidGeometry() {
      return false;
    }

    @Value.Default
    public int getDeferredPolygonId() {
      return 0;
    }

    public abstract Optional<GeometryType> getCurrentGeometryType();

    @Value.Default
    public List<Integer> getCurrentGeometryNesting() {
      return ImmutableList.of();
    }

    @Value.Default
    public Map<String, String> getPlaceholders() {
      return ImmutableMap.of();
    }

    @Value.Default
    public List<Integer> getObjects() {
      return ImmutableList.of();
    }

    @Value.Default
    public int getLastObject() {
      return 0;
    }

    public abstract Optional<String> getVariableNameProperty();

    @Value.Default
    public Map<String, String> getVariableNameMapping() {
      return ImmutableMap.of();
    }

    @Value.Default
    public Map<Integer, String> getCurrentRawGmlIds() {
      return ImmutableMap.of();
    }

    @Value.Default
    public Map<Integer, String> getCurrentGmlIdSuffixes() {
      return ImmutableMap.of();
    }
  }
}
