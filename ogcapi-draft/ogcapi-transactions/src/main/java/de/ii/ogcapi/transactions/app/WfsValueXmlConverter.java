/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.gml.domain.GeometryDecoderGml;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.transcode.json.GeometryEncoderJson;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalInt;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

/**
 * Converts an XML subtree captured from a {@code <wfs:Value>} body to the {@link JsonNode} form the
 * SQL writer expects. The conversion is target-schema-aware:
 *
 * <ul>
 *   <li>{@link SchemaBase.Type#GEOMETRY}: parses the GML subtree with {@link GeometryDecoderGml}
 *       and re-encodes via {@link GeometryEncoderJson} to a GeoJSON {@link JsonNode}.
 *   <li>{@link SchemaBase.Type#OBJECT_ARRAY}: walks the XML one level deep, strips the outer
 *       object-type wrapper element, and maps each child element's local name to the matching child
 *       property's schema id (using its alias when the WFS input uses aliases).
 * </ul>
 *
 * <p>Other target types raise an {@link IllegalArgumentException} — XML content inside {@code
 * <wfs:Value>} is only meaningful for geometries and OBJECT_ARRAY elements in this phase.
 */
final class WfsValueXmlConverter {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final XMLInputFactory STAX_FACTORY = createStaxFactory();

  private static XMLInputFactory createStaxFactory() {
    XMLInputFactory f = XMLInputFactory.newInstance();
    f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    f.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
    return f;
  }

  private WfsValueXmlConverter() {}

  static JsonNode convert(
      byte[] xml, FeatureSchema targetSchema, boolean inputUseAlias, EpsgCrs crs) {
    if (targetSchema.getType() == SchemaBase.Type.GEOMETRY) {
      return geometryToGeoJson(xml, crs);
    }
    if (targetSchema.getType() == SchemaBase.Type.OBJECT_ARRAY) {
      return objectElementToJson(xml, targetSchema, inputUseAlias);
    }
    throw new IllegalArgumentException(
        "XML content inside <wfs:Value> is only supported for geometry and OBJECT_ARRAY properties"
            + " (got type "
            + targetSchema.getType()
            + " for '"
            + targetSchema.getName()
            + "').");
  }

  // GML → Geometry<?> via the aalto-based decoder, then to GeoJSON via JsonGenerator.
  private static JsonNode geometryToGeoJson(byte[] xml, EpsgCrs crs) {
    Geometry<?> geometry;
    try {
      AsyncXMLStreamReader<AsyncByteArrayFeeder> parser =
          new InputFactoryImpl().createAsyncFor(new byte[0]);
      parser.getInputFeeder().feedInput(xml, 0, xml.length);
      parser.getInputFeeder().endOfInput();
      Optional<Geometry<?>> decoded =
          new GeometryDecoderGml().decode(parser, Optional.ofNullable(crs), OptionalInt.empty());
      if (decoded.isEmpty()) {
        throw new IllegalArgumentException(
            "Could not decode <wfs:Value> XML as a GML geometry: incomplete stream.");
      }
      geometry = decoded.get();
    } catch (XMLStreamException | IOException e) {
      throw new IllegalArgumentException(
          "Could not decode <wfs:Value> XML as a GML geometry: " + e.getMessage(), e);
    }
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      JsonGenerator gen = MAPPER.getFactory().createGenerator(baos);
      geometry.accept(new GeometryEncoderJson(gen));
      gen.flush();
      gen.close();
      return MAPPER.readTree(baos.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not re-encode parsed GML geometry as GeoJSON: " + e.getMessage(), e);
    }
  }

  // <adv:AA_Modellart><adv:advStandardModell>DLKM</adv:advStandardModell></adv:AA_Modellart>
  //   →  {"stm":"DLKM"}    (when GML.useAlias is true and `stm` has alias `advStandardModell`)
  // The outer wrapper element is the object-type element; we strip it and walk its children,
  // mapping each child local name back to the schema's canonical property id.
  private static JsonNode objectElementToJson(
      byte[] xml, FeatureSchema arraySchema, boolean inputUseAlias) {
    try {
      XMLEventReader reader = STAX_FACTORY.createXMLEventReader(new ByteArrayInputStream(xml));
      StartElement root = nextStart(reader);
      if (root == null) {
        throw new IllegalArgumentException(
            "Empty <wfs:Value> XML for OBJECT_ARRAY property '" + arraySchema.getName() + "'.");
      }
      // Validate the wrapper local name matches the array's objectType if declared.
      String wrapperName = root.getName().getLocalPart();
      Optional<String> expectedObjectType = arraySchema.getObjectType();
      if (expectedObjectType.isPresent() && !expectedObjectType.get().equals(wrapperName)) {
        throw new IllegalArgumentException(
            "<wfs:Value> XML for '"
                + arraySchema.getName()
                + "' must wrap a <"
                + expectedObjectType.get()
                + "> element, got <"
                + wrapperName
                + ">.");
      }
      ObjectNode out = JsonNodeFactory.instance.objectNode();
      while (true) {
        XMLEvent e = reader.nextEvent();
        if (e.isEndElement()) {
          EndElement end = e.asEndElement();
          if (end.getName().equals(root.getName())) {
            break;
          }
        }
        if (!e.isStartElement()) {
          continue;
        }
        StartElement child = e.asStartElement();
        String childLocal = child.getName().getLocalPart();
        FeatureSchema match = matchChild(arraySchema, childLocal, inputUseAlias);
        if (match == null) {
          // Unknown child — skip silently so legacy <gml:identifier> and similar metadata
          // don't break the conversion. The mutation path validates against the writable
          // column set anyway.
          skipElement(reader);
          continue;
        }
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
          XMLEvent ev = reader.nextEvent();
          if (ev.isEndElement()) {
            depth--;
            continue;
          }
          if (ev.isStartElement()) {
            depth++;
            continue;
          }
          if (ev.isCharacters()) {
            text.append(ev.asCharacters().getData());
          }
        }
        String value = text.toString().trim();
        out.put(match.getName(), value);
      }
      return out;
    } catch (XMLStreamException e) {
      throw new IllegalArgumentException(
          "Could not parse <wfs:Value> XML for OBJECT_ARRAY property '"
              + arraySchema.getName()
              + "': "
              + e.getMessage(),
          e);
    }
  }

  private static FeatureSchema matchChild(
      FeatureSchema parent, String localName, boolean useAlias) {
    for (FeatureSchema child : parent.getProperties()) {
      String expected = useAlias ? child.getAlias().orElse(child.getName()) : child.getName();
      if (expected.equals(localName)) {
        return child;
      }
    }
    return null;
  }

  private static StartElement nextStart(XMLEventReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      XMLEvent e = reader.nextEvent();
      if (e.isStartElement()) {
        return e.asStartElement();
      }
    }
    return null;
  }

  private static void skipElement(XMLEventReader reader) throws XMLStreamException {
    int depth = 1;
    while (reader.hasNext() && depth > 0) {
      XMLEvent e = reader.nextEvent();
      if (e.isStartElement()) {
        depth++;
      } else if (e.isEndElement()) {
        depth--;
      }
    }
  }
}
