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
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.CrsVariants;
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
  private static final String PROPERTY_LINKS_PLACEHOLDER = "_zz_PROPERTY_LINKS_i_zz_";
  private static final String REF_SUFFIX_PLACEHOLDER = "_zz_REF_SUFFIX_i_zz_";

  /**
   * Internal string buffer to buffer information. The buffer is flushed for every feature. Also
   * used by GeometryEncoderGml which requires a StringBuilder.
   */
  @SuppressWarnings({
    "PMD.AvoidStringBufferField"
  }) // memory leak is not a risk and we need to incrementally build a string
  private final StringBuilder buffer = new StringBuilder();

  private final XMLStreamWriter xmlWriter;

  // Tracks whether any start element has been written. Used by endDocument() to skip
  // writeEndDocument() on an empty stream: a single-feature item request whose query
  // returns no rows produces no root element (the root is written inside onFeatureStart),
  // and wstx would throw "no root" if we called writeEndDocument() unconditionally.
  // Skipping it lets the upstream stream complete cleanly so failIfNoFeatures can turn
  // the empty result into a 404 instead of a 500.
  private boolean rootElementWritten;

  FeatureTransformationContextGml() {
    rootElementWritten = false;
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

  // -------------------------------------------------------------------------
  // Document-level options. A single response may mix several collections (the Search building
  // block); these options are written once for the whole document (root element, namespace
  // declarations) or are required to be uniform, so they are taken from / unioned across the
  // response's collections rather than resolved per feature.
  // -------------------------------------------------------------------------

  public abstract GmlVersion getGmlVersion();

  public abstract Map<String, String> getNamespaces();

  public abstract Map<String, String> getSchemaLocations();

  public abstract Optional<String> getDefaultNamespace();

  /**
   * Maps a collection id to the object type of that collection. Used to derive the {@code
   * _{objectType}} suffix appended to the property element name of a feature reference declared in
   * {@link #getObjectTypeSuffixedProperties()}: the reference's type value (the target collection
   * id) is looked up here to obtain the object type for the suffix. Built from all feature schemas
   * of the API, so it is not collection-specific.
   */
  @Value.Default
  public Map<String, String> getRefTargetObjectTypes() {
    return ImmutableMap.of();
  }

  public abstract Optional<String> getFeatureCollectionElementName();

  public abstract Optional<String> getFeatureMemberElementName();

  public abstract boolean getSupportsStandardResponseParameters();

  // -------------------------------------------------------------------------
  // Per-collection options. Keyed by collection id, one entry per collection in the response.
  // The active bundle is resolved from the type of the feature currently being encoded (set on
  // the state at feature start), so every property is encoded with the configuration of the
  // collection it belongs to. See CollectionEncodingGml.
  // -------------------------------------------------------------------------

  public abstract Map<String, CollectionEncodingGml> getCollectionEncodings();

  private static final CollectionEncodingGml EMPTY_ENCODING =
      ImmutableCollectionEncodingGml.builder().build();

  /**
   * The per-collection encoding options of the collection the feature currently being encoded
   * belongs to. Resolves via the collection id recorded on the state at feature start. Before any
   * feature is active — where per-collection options are not actually read — it returns any present
   * bundle (or an empty one if there are none), so document-level writers never NPE.
   */
  @Value.Auxiliary
  public CollectionEncodingGml currentEncoding() {
    return resolveCurrentEncoding(getCollectionEncodings(), getState().getCurrentCollectionId());
  }

  /**
   * Picks the encoding bundle for the active collection: the one keyed by {@code
   * currentCollectionId} when set and present, else any present bundle, finally an empty bundle.
   * Package-private and static for unit testing.
   */
  static CollectionEncodingGml resolveCurrentEncoding(
      Map<String, CollectionEncodingGml> encodings, Optional<String> currentCollectionId) {
    CollectionEncodingGml active = currentCollectionId.map(encodings::get).orElse(null);
    if (active != null) {
      return active;
    }
    return encodings.isEmpty() ? EMPTY_ENCODING : encodings.values().iterator().next();
  }

  // Options read straight off the active collection's GmlConfiguration (with the same null/Optional
  // normalization the context applied before). Only the five derived options below are taken from
  // the bundle's pre-computed fields.

  public Map<String, String> getObjectTypeNamespaces() {
    return currentEncoding().getConfig().getObjectTypeNamespaces();
  }

  public Map<String, VariableName> getVariableObjectElementNames() {
    return currentEncoding().getConfig().getVariableObjectElementNames();
  }

  public List<String> getObjectTypeSuffixedProperties() {
    // Alias-rewritten in the bundle (technical id → alias path) when useAlias is on, so this
    // matches the alias-form paths GmlWriterProperties sees at runtime (as for getXmlAttributes).
    return currentEncoding().getObjectTypeSuffixedProperties();
  }

  public boolean getGmlIdOnGeometries() {
    return Boolean.TRUE.equals(currentEncoding().getConfig().getGmlIdOnGeometries());
  }

  public boolean getSrsDimension() {
    return Boolean.TRUE.equals(currentEncoding().getConfig().getSrsDimension());
  }

  public boolean getUseSurfaceAndCurve() {
    return Boolean.TRUE.equals(currentEncoding().getConfig().getUseSurfaceAndCurve());
  }

  public boolean getForceCompositeCurve() {
    return Boolean.TRUE.equals(currentEncoding().getConfig().getForceCompositeCurve());
  }

  public Optional<String> getGmlIdPrefix() {
    return Optional.ofNullable(currentEncoding().getConfig().getGmlIdPrefix());
  }

  public Optional<GmlConfiguration.SrsNameStyle> getSrsNameStyle() {
    return Optional.ofNullable(currentEncoding().getConfig().getSrsNameStyle());
  }

  public List<EpsgCrs> getAlternativeCrss() {
    return currentEncoding().getAlternativeCrss();
  }

  public Optional<GmlConfiguration.UomStyle> getUomStyle() {
    return Optional.ofNullable(currentEncoding().getConfig().getUomStyle());
  }

  public List<UomMapping> getUomMappings() {
    return currentEncoding().getConfig().getUomMappings();
  }

  public Optional<String> getFeatureRefTemplate() {
    return Optional.ofNullable(currentEncoding().getConfig().getFeatureRefTemplate());
  }

  public Optional<GmlIdentifier> getGmlIdentifier() {
    return Optional.ofNullable(currentEncoding().getConfig().getGmlIdentifier());
  }

  public Optional<String> getCodelistUriTemplate() {
    return Optional.ofNullable(currentEncoding().getConfig().getCodelistUriTemplate());
  }

  public Optional<String> getCodeListUriTemplateIso19139() {
    return Optional.ofNullable(currentEncoding().getConfig().getCodeListUriTemplateIso19139());
  }

  // Derived options, pre-computed per collection (alias rewrites / codelist resolution / request).

  public List<String> getXmlAttributes() {
    return currentEncoding().getXmlAttributes();
  }

  public boolean getAppendTemporalSuffixToGmlId() {
    return currentEncoding().getAppendTemporalSuffixToGmlId();
  }

  public Map<String, String> getCodelistProperties() {
    return currentEncoding().getCodelistProperties();
  }

  public Map<String, List<ValueWrapElement>> getValueWrap() {
    return currentEncoding().getValueWrap();
  }

  public Map<String, CrsVariants> getPositionVariants() {
    return currentEncoding().getPositionVariants();
  }

  public Map<String, Codelist> getCodelists() {
    return currentEncoding().getCodelists();
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

  /** Write the XML end-of-document, or no-op if no element was ever written. */
  public void endDocument() throws IOException {
    if (!rootElementWritten) {
      return;
    }
    try {
      xmlWriter.writeEndDocument();
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * Returns the property element name qualified with the namespace prefix derived from the
   * property's origin object type, or — when no origin is given — from the containing nested
   * object's {@code objectType}.
   *
   * <p>Qualification chain:
   *
   * <ol>
   *   <li>If {@code name} already contains a {@code :}, it is returned unchanged (explicit prefix
   *       wins).
   *   <li>If {@code originObjectType} is non-null, its mapping in {@link
   *       #getObjectTypeNamespaces()} prefixes the name; if it has no mapping, the bare name is
   *       returned (the writer emits it in the default namespace). The property's own origin
   *       suppresses the object-type-stack walk in step 3.
   *   <li>Otherwise the immediate containing object type (top of {@link
   *       de.ii.ogcapi.features.gml.domain.StateGml#getObjectTypeStack()}) is consulted — but only
   *       when there is a nested ancestor (stack size ≥ 2). The feature root's {@code objectType}
   *       pins the namespace of the feature element itself and must not propagate down to property
   *       children that originate elsewhere; nested OBJECTs (e.g. ISO 19115 {@code LI_Lineage}) do
   *       propagate, since their inline children belong to the nested object's own namespace.
   *   <li>Otherwise the bare name is returned (default namespace).
   * </ol>
   */
  @Value.Auxiliary
  public String qualifyPropertyElementName(String name, String originObjectType) {
    return qualifyPropertyElementName(
        name, originObjectType, getState().getObjectTypeStack(), getObjectTypeNamespaces());
  }

  /**
   * Pure-function variant of {@link #qualifyPropertyElementName(String, String)} that takes the
   * object-type stack explicitly, for unit testing.
   */
  public static String qualifyPropertyElementName(
      String name,
      String originObjectType,
      List<String> objectTypeStack,
      Map<String, String> objectTypeNamespaces) {
    if (name == null || name.indexOf(':') >= 0) {
      return name;
    }
    if (originObjectType != null) {
      String nsPrefix = objectTypeNamespaces.get(originObjectType);
      return nsPrefix == null ? name : nsPrefix + ":" + name;
    }
    if (objectTypeStack.size() < 2) {
      return name;
    }
    String parentObjectType = objectTypeStack.get(objectTypeStack.size() - 1);
    if (parentObjectType == null) {
      return name;
    }
    String nsPrefix = objectTypeNamespaces.get(parentObjectType);
    return nsPrefix == null ? name : nsPrefix + ":" + name;
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
      rootElementWritten = true;
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

  /** Writes an XML comment ({@code <!-- text -->}) in element-content position. */
  public void writeComment(String text) throws IOException {
    try {
      xmlWriter.writeComment(text);
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
  }

  /**
   * Reserves a slot right after the current feature's start tag for content that is only known at
   * feature end (e.g. link captures from {@code FeatureTokenTransformerPropertyLinks}). Returns the
   * placeholder key; pass it to {@link #setPropertyLinksContent(String, String)} from {@code
   * onFeatureEnd}. An unset (empty) value is silently dropped at flush.
   *
   * <p>The slot is emitted as an XML comment containing the placeholder key, e.g. {@code <!--
   * _zz_PROPERTY_LINKS_1_zz_ -->}. Going through {@code xmlWriter.writeComment} (instead of a raw
   * buffer append) ensures the parent start tag is flushed first, so the slot lands inside the
   * feature element. At flush time the placeholder text inside the comment is substituted; the
   * surrounding {@code <!-- ... -->} stays put. To emit multiple comments (e.g. one per link),
   * embed {@code --><!--} sequences inside the substituted content.
   */
  @Value.Auxiliary
  public String reservePropertyLinksPlaceholder() throws IOException {
    int i = getState().getLastObject();
    String placeholder = PROPERTY_LINKS_PLACEHOLDER.replace("i", String.valueOf(i));
    writeComment(" " + placeholder + " ");
    getState().putPlaceholders(placeholder, "");
    return placeholder;
  }

  /** Populates a placeholder previously returned by {@link #reservePropertyLinksPlaceholder()}. */
  public void setPropertyLinksContent(String placeholder, String content) {
    getState().putPlaceholders(placeholder, content);
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
                // should be reported. XML_ATTRIBUTE placeholders are an exception: an empty
                // value means "no XML attribute was added", and the placeholder is removed
                // in place (inside attribute position).
                // REF_SUFFIX placeholders are also allowed to be empty: an empty value means the
                // feature reference has no resolvable object type, so the placeholder is removed in
                // place (no suffix is appended to the property element name).
                if (!key.startsWith("_zz_XML_ATTRIBUTE_")
                    && !key.startsWith("_zz_PROPERTY_LINKS_")
                    && !key.startsWith("_zz_REF_SUFFIX_")
                    && value.isEmpty()) {
                  return;
                }
                // PROPERTY_LINKS placeholders live inside an XML comment ("<!-- KEY -->").
                // When empty, drop the entire comment from the buffer; otherwise the
                // unwrapped key is replaced in place inside the comment.
                if (key.startsWith("_zz_PROPERTY_LINKS_") && value.isEmpty()) {
                  String wrapped = "<!-- " + key + " -->";
                  int idx = buffer.lastIndexOf(wrapped);
                  if (idx != -1) {
                    buffer.delete(idx, idx + wrapped.length());
                  }
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

  /**
   * Override only the {@code gml:identifier}-value placeholder, leaving the {@code gml:id}
   * placeholder untouched. Used when a profile rewrites the {@code gml:id} attribute to a composite
   * value (e.g. {@code versions-as-features-unique-ids}) but the {@code gml:identifier} element
   * should still carry the canonical feature id.
   */
  public void setCurrentGmlIdentifierValue(String canonicalValue) {
    if (getGmlIdentifier().isEmpty()) {
      return;
    }
    GmlIdentifier cfg = getGmlIdentifier().get();
    int i = getState().getLastObject();
    String resolved =
        cfg.getValueTemplate() == null
            ? canonicalValue
            : cfg.getValueTemplate().replace("{{value}}", canonicalValue);
    getState()
        .putPlaceholders(
            GML_IDENTIFIER_VALUE_PLACEHOLDER.replace("i", String.valueOf(i)), resolved);
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

    getState()
        .setObjectTypeStack(
            ImmutableList.<String>builder()
                .addAll(getState().getObjectTypeStack())
                .add(objectType)
                .build());

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

    List<String> types = getState().getObjectTypeStack();
    if (!types.isEmpty()) {
      getState().setObjectTypeStack(ImmutableList.copyOf(types.subList(0, types.size() - 1)));
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
   * The property element name of a feature reference declared in {@link
   * #getObjectTypeSuffixedProperties()} carries a {@code _{objectType}} suffix that is only known
   * once the reference's type value has been processed. This method reserves a unique placeholder
   * for that suffix and returns it; it is appended to the property element name on the start tag
   * and resolved by {@link #setCurrentRefSuffix(String)} (or removed at {@link #flush()} when the
   * reference has no resolvable type). Feature references are not nested and their properties are
   * processed in sequence, so the placeholder index ({@link StateGml#getLastRefSuffix()})
   * identifies the reference currently being written.
   */
  public String beginRefSuffix() {
    int i = getState().getLastRefSuffix() + 1;
    getState().setLastRefSuffix(i);
    getState().setRefSuffixPending(true);
    String placeholder = REF_SUFFIX_PLACEHOLDER.replace("i", String.valueOf(i));
    getState().putPlaceholders(placeholder, "");
    return placeholder;
  }

  /**
   * Resolves the placeholder reserved by {@link #beginRefSuffix()} for the feature reference
   * currently being written: the {@code collectionId} (the reference's type value) is looked up in
   * {@link #getRefTargetObjectTypes()} to obtain the object type, and the suffix is set to {@code
   * _{objectType}}. When the collection id is unknown, the suffix stays empty (no suffix is added).
   */
  public void setCurrentRefSuffix(String collectionId) {
    int i = getState().getLastRefSuffix();
    String objectType = getRefTargetObjectTypes().get(collectionId);
    String suffix = objectType == null ? "" : "_" + objectType;
    getState().putPlaceholders(REF_SUFFIX_PLACEHOLDER.replace("i", String.valueOf(i)), suffix);
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

    /**
     * The collection id of the feature currently being encoded, set at feature start from the
     * feature's provider type via {@link #getCollectionIdForType(String)}. Drives {@link
     * #currentEncoding()} so per-collection options resolve to the right collection in a
     * multi-collection response. Empty before the first feature.
     */
    public abstract Optional<String> getCurrentCollectionId();

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

    /** Counter assigning a unique index to each feature-reference property-element-name suffix. */
    @Value.Default
    public int getLastRefSuffix() {
      return 0;
    }

    /**
     * True while a feature reference whose property element name carries an object-type suffix is
     * being written, so its type value is captured for the suffix instead of emitted as an
     * attribute.
     */
    @Value.Default
    public boolean getRefSuffixPending() {
      return false;
    }

    /**
     * Collects the resolved children of the feature reference currently being written ({@code
     * id}/{@code title}/{@code type}/{@code uriTemplate}). GML encodes references natively, so the
     * {@code xlink:href}/{@code xlink:title} attributes and the {@code _<objectType>} suffix are
     * emitted together once all children have been seen (at object end), independent of child
     * order. Cleared at the start of each reference.
     */
    @Value.Default
    public Map<String, String> getRefValues() {
      return ImmutableMap.of();
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
    public List<String> getObjectTypeStack() {
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
