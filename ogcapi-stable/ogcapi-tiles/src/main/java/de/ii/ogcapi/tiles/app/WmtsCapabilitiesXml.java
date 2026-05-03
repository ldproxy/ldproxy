/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlAnnotationIntrospector;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.tiles.domain.WmtsCapabilitiesFormatExtension;
import de.ii.ogcapi.tiles.domain.WmtsServiceMetadata;
import de.ii.xtraplatform.tiles.domain.JacksonXmlAnnotation.XmlIgnore;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;

/**
 * @title WMTS 1.0.0 Capabilities
 */
@Singleton
@AutoBind
public class WmtsCapabilitiesXml implements WmtsCapabilitiesFormatExtension {

  public class XmlAnnotationIntrospector extends JacksonXmlAnnotationIntrospector {
    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
      return m.hasAnnotation(XmlIgnore.class) || super.hasIgnoreMarker(m);
    }
  }

  public static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "xml"))
          .label("XML")
          .parameter("xml")
          .build();

  @Inject
  WmtsCapabilitiesXml() {
    super();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(OBJECT_SCHEMA)
        .schemaRef(OBJECT_SCHEMA_REF)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getEntity(
      WmtsServiceMetadata capabilities, OgcApi api, ApiRequestContext requestContext) {
    try {
      // QGIS requires "ows" as the namespace prefix, so we have to override the standard Jackson
      // behavior
      String defaultNamespace = "http://www.opengis.net/wmts/1.0";
      Map<String, String> otherNamespaces =
          ImmutableMap.of(
              "xlink",
              "http://www.w3.org/1999/xlink",
              "ows",
              "http://www.opengis.net/ows/1.1",
              "xsi",
              "http://www.w3.org/2001/XMLSchema-instance");

      return XmlMapper.builder(new NamespaceXmlFactory(defaultNamespace, otherNamespaces))
          .annotationIntrospector(new XmlAnnotationIntrospector())
          .addModule(new Jdk8Module())
          .enable(Feature.WRITE_BIGDECIMAL_AS_PLAIN)
          .serializationInclusion(JsonInclude.Include.NON_EMPTY)
          .build()
          .writeValueAsString(capabilities);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
