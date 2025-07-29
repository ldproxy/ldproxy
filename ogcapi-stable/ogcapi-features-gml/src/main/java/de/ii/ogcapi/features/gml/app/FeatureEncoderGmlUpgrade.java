/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import static de.ii.xtraplatform.base.domain.util.LambdaWithException.biConsumerMayThrow;
import static de.ii.xtraplatform.base.domain.util.LambdaWithException.consumerMayThrow;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.escape.Escaper;
import com.google.common.xml.XmlEscapers;
import de.ii.ogcapi.features.gml.domain.EncodingAwareContextGmlUpgrade;
import de.ii.ogcapi.features.gml.domain.FeatureTransformationContextGmlUpgrade;
import de.ii.ogcapi.features.gml.domain.ModifiableEncodingAwareContextGmlUpgrade;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoderDefault;
import de.ii.xtraplatform.features.gml.domain.GeometryEncoderGml;
import de.ii.xtraplatform.features.gml.domain.XMLNamespaceNormalizer;
import de.ii.xtraplatform.geometries.domain.Geometry;
import de.ii.xtraplatform.geometries.domain.transform.CoordinatesTransformation;
import de.ii.xtraplatform.geometries.domain.transform.CoordinatesTransformer;
import de.ii.xtraplatform.geometries.domain.transform.ImmutableCrsTransform;
import de.ii.xtraplatform.geometries.domain.transform.ImmutableSimplifyLine;
import de.ii.xtraplatform.streams.domain.OutputStreamToByteConsumer;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author zahnen
 */
public class FeatureEncoderGmlUpgrade
    extends FeatureTokenEncoderDefault<EncodingAwareContextGmlUpgrade> {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeatureEncoderGmlUpgrade.class);
  private static final List<String> GEOMETRY_COORDINATES =
      new ImmutableList.Builder<String>()
          .add("posList")
          .add("pos")
          .add("coordinates")
          .add("lowerCorner")
          .add("upperCorner")
          .build();
  private static final String IO_ERROR_MESSAGE = "Error writing GML";
  private static final String SCHEMA_LOCATION = "schemaLocation";
  private static final String SRS_DIMENSION = "srsDimension";
  private static final String SRS_NAME = "srsName";

  private final FeatureTransformationContextGmlUpgrade transformationContext;
  private final boolean isFeatureCollection;
  private final OutputStreamWriter writer;
  private final XMLNamespaceNormalizer namespaces;
  private final CrsTransformer crsTransformer;
  private final Escaper escaper;
  private final double maxAllowableOffset;

  private boolean inCurrentStart;
  private boolean inCurrentFeatureStart;
  private boolean inCurrentPropertyStart;
  private boolean inCurrentPropertyText;
  private boolean inCoordinates;
  private Integer currentDimension;
  private String locations;

  @SuppressWarnings({
    "PMD.TooManyMethods",
    "PMD.GodClass"
  }) // this class needs that many methods, a refactoring makes no sense
  public FeatureEncoderGmlUpgrade(FeatureTransformationContextGmlUpgrade transformationContext) {
    super();
    this.transformationContext = transformationContext;
    this.isFeatureCollection = transformationContext.isFeatureCollection();
    this.writer =
        new OutputStreamWriter(transformationContext.getOutputStream(), StandardCharsets.UTF_8);
    this.namespaces = new XMLNamespaceNormalizer(transformationContext.getNamespaces());
    this.crsTransformer = transformationContext.getCrsTransformer().orElse(null);
    //noinspection UnstableApiUsage
    this.escaper = XmlEscapers.xmlAttributeEscaper();
    this.maxAllowableOffset = transformationContext.getMaxAllowableOffset();
    this.namespaces.addNamespace("sf", "http://www.opengis.net/ogcapi-features-1/1.0/sf", true);
    this.namespaces.addNamespace("ogcapi", "http://www.opengis.net/ogcapi-features-1/1.0", true);
    this.namespaces.addNamespace("atom", "http://www.w3.org/2005/Atom", true);
  }

  @Override
  public void onStart(EncodingAwareContextGmlUpgrade context) {
    if (transformationContext.getOutputStream() instanceof OutputStreamToByteConsumer) {
      ((OutputStreamToByteConsumer) transformationContext.getOutputStream())
          .setByteConsumer(this::push);
    }
    try {
      writer.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
      if (isFeatureCollection) {
        writer.append("\n<sf:FeatureCollection");
        namespaces
            .getNamespaces()
            .keySet()
            .forEach(
                consumerMayThrow(
                    prefix -> {
                      if (!Strings.isNullOrEmpty(prefix)) {
                        writer.append(" ");
                        writer.append(namespaces.generateNamespaceDeclaration(prefix));
                      }
                    }));

        inCurrentStart = true;

        context.additionalInfo().forEach(biConsumerMayThrow(this::onGmlAttribute));
      }
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void onEnd(EncodingAwareContextGmlUpgrade context) {
    try {
      if (isFeatureCollection) {
        if (inCurrentStart) {
          writer.append(">");

          inCurrentStart = false;
        }
        writer.append("\n</sf:FeatureCollection>");
      }
      writer.flush();
      writer.close();
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void onFeatureStart(EncodingAwareContextGmlUpgrade context) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("{}", context.path());
    }
    try {
      if (inCurrentStart) {
        writer.append(">");

        inCurrentStart = false;
      }

      if (isFeatureCollection) {
        writer.append("\n<sf:featureMember>");
      }
      writer.append("\n<");
      writer.append(getNamespaceUri(context.path()));
      writer.append(":");
      writer.append(getLocalName(context.path()));

      if (!isFeatureCollection) {
        namespaces
            .getNamespaces()
            .keySet()
            .forEach(
                consumerMayThrow(
                    prefix -> {
                      writer.append(" ");
                      writer.append(namespaces.generateNamespaceDeclaration(prefix));
                    }));
        if (!Strings.isNullOrEmpty(locations)) {
          writeXmlAttribute(
              namespaces.getNamespacePrefix("http://www.w3.org/2001/XMLSchema-instance"),
              SCHEMA_LOCATION,
              locations);
        }
      }

      inCurrentFeatureStart = true;

      context.additionalInfo().forEach(biConsumerMayThrow(this::onGmlAttribute));
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void onFeatureEnd(EncodingAwareContextGmlUpgrade context) {
    try {
      writer.append("\n</");
      writer.append(getNamespaceUri(context.path()));
      writer.append(":");
      writer.append(getLocalName(context.path()));
      writer.append(">");
      if (isFeatureCollection) {
        writer.append("\n</sf:featureMember>");
      }
      writer.flush();
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void onObjectStart(EncodingAwareContextGmlUpgrade context) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("START {} {}", context.path(), getLocalName(context.path()));
    }
    try {
      if (inCurrentFeatureStart) {
        writer.append(">");
        inCurrentFeatureStart = false;
      }
      if (inCurrentPropertyStart) {
        writer.append(">");
      }

      writer.append("\n<");
      writer.append(getNamespaceUri(context.path()));
      writer.append(":");
      writer.append(getLocalName(context.path()));

      inCurrentPropertyStart = true;
      if (GEOMETRY_COORDINATES.contains(getLocalName(context.path()))) {
        inCoordinates = true;
      }

      context.additionalInfo().forEach(biConsumerMayThrow(this::onGmlAttribute));
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void onObjectEnd(EncodingAwareContextGmlUpgrade context) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("END {} {}", context.path(), getLocalName(context.path()));
    }
    try {

      if (inCurrentPropertyStart) {
        writer.append("/>");
        inCurrentPropertyStart = false;
      } else {
        if (!inCurrentPropertyText) {
          writer.append("\n");
        }
        inCurrentPropertyText = false;

        writer.append("</");
        writer.append(getNamespaceUri(context.path()));
        writer.append(":");
        writer.append(getLocalName(context.path()));
        writer.append(">");
      }
      inCoordinates = false;
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  @Override
  public void onArrayStart(EncodingAwareContextGmlUpgrade context) {
    onObjectStart(context);
  }

  @Override
  public void onArrayEnd(EncodingAwareContextGmlUpgrade context) {
    onObjectEnd(context);
  }

  private void onGmlAttribute(String name, String value) throws IOException {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("ATTR {} {}", name, value);
    }
    onGmlAttribute(getNamespaceUri(name), getLocalName(name), value);
  }

  private void onGmlAttribute(String namespace, String localName, String value) throws IOException {
    if (inCurrentStart && !SCHEMA_LOCATION.equals(localName)) {
      return;
    }

    // potentially store information for use in later steps
    processGmlAttribute(localName, value);

    if (isFeatureCollection || inCurrentFeatureStart || inCurrentPropertyStart) {
      // update attribute value, if necessary
      writeXmlAttribute(namespace, localName, processGmlAttributeValue(localName, value));
    }
  }

  private String processGmlAttributeValue(String localName, String value) {
    String newValue = value;
    switch (localName) {
      case SCHEMA_LOCATION:
        if (inCurrentStart) {
          newValue = adjustSchemaLocation(value);
        }
        break;

      case SRS_NAME:
        if (inCurrentPropertyStart && Objects.nonNull(crsTransformer)) {
          newValue = crsTransformer.getTargetCrs().toUriString();
        }
        break;

      default:
        // ignore
    }
    return newValue;
  }

  private void processGmlAttribute(String localName, String value) {
    switch (localName) {
      case SCHEMA_LOCATION:
        if (!isFeatureCollection) {
          locations = adjustSchemaLocation(value);
        }
        break;

      case SRS_DIMENSION:
        if (inCurrentPropertyStart) {
          currentDimension = getIntOrNull(value);
        }
        break;

      default:
        // ignore
    }
  }

  private Integer getIntOrNull(String value) {
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void writeXmlAttribute(String namespace, String localName, String newValue)
      throws IOException {
    writer.append(" ");
    if (!Strings.isNullOrEmpty(namespace)) {
      writer.append(namespace);
      writer.append(":");
    }
    writer.append(localName);
    writer.append("=\"");
    writer.append(escaper.escape(newValue));
    writer.append("\"");
  }

  @Override
  public void onGeometry(EncodingAwareContextGmlUpgrade context) {
    // TODO test
    Geometry<?> geometry = context.geometry();

    CoordinatesTransformation next = null;
    if (crsTransformer != null) {
      next = ImmutableCrsTransform.of(Optional.empty(), crsTransformer);
    }

    if (maxAllowableOffset > 0) {
      next = ImmutableSimplifyLine.of(Optional.ofNullable(next), maxAllowableOffset);
    }

    if (next != null && geometry != null) {
      geometry = geometry.accept(new CoordinatesTransformer(next));
    }

    if (geometry != null) {
      StringBuilder builder = new StringBuilder();
      GeometryEncoderGml encoder =
          new GeometryEncoderGml(
              builder,
              Set.of(),
              Optional.of("gml"),
              Optional.empty(),
              transformationContext.getGeometryPrecision());
      geometry.accept(encoder);
      try {
        writer.append(builder.toString());
      } catch (IOException e) {
        throw new IllegalStateException(IO_ERROR_MESSAGE, e);
      }
    }
  }

  @Override
  public void onValue(EncodingAwareContextGmlUpgrade context) {
    try {
      if (inCurrentPropertyStart) {
        writer.append(">");
        inCurrentPropertyStart = false;
      }
      writer.append(escaper.escape(Objects.requireNonNull(context.value())));
      inCurrentPropertyText = true;
    } catch (IOException e) {
      throw new IllegalStateException(IO_ERROR_MESSAGE, e);
    }
  }

  private String adjustSchemaLocation(String schemaLocation) {
    List<String> split = Splitter.on(' ').splitToList(schemaLocation);
    Map<String, String> locations = new LinkedHashMap<>();

    for (int i = 0; i < split.size() - 1; i += 2) {
      if (!split.get(i).startsWith("http://www.opengis.net/ogcapi-features-1")) {
        locations.put(split.get(i), split.get(i + 1));
      }
    }

    locations.put(
        "http://www.opengis.net/ogcapi-features-1/1.0/sf",
        "http://schemas.opengis.net/ogcapi/features/part1/1.0/xml/core-sf.xsd");
    locations.put(
        "http://www.opengis.net/ogcapi-features-1/1.0",
        "http://schemas.opengis.net/ogcapi/features/part1/1.0/xml/core.xsd");
    locations.put(
        "http://www.w3.org/2005/Atom", "http://schemas.opengis.net/kml/2.3/atom-author-link.xsd");

    return locations.entrySet().stream()
        .map(entry -> entry.getKey() + " " + entry.getValue())
        .collect(Collectors.joining(" "));
  }

  private String getLocalName(List<String> path) {
    return path.isEmpty() ? null : getLocalName(path.get(path.size() - 1));
  }

  private String getLocalName(String name) {
    return name.substring(name.lastIndexOf(':') + 1);
  }

  private String getNamespaceUri(List<String> path) {
    return path.isEmpty() ? null : getNamespaceUri(path.get(path.size() - 1));
  }

  private String getNamespaceUri(String name) {
    return name.substring(0, name.lastIndexOf(':'));
  }

  @Override
  public Class<? extends EncodingAwareContextGmlUpgrade> getContextInterface() {
    return EncodingAwareContextGmlUpgrade.class;
  }

  @Override
  public EncodingAwareContextGmlUpgrade createContext() {
    return ModifiableEncodingAwareContextGmlUpgrade.create().setEncoding(transformationContext);
  }
}
