/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.styles.domain.StyleFormatExtension;
import de.ii.ogcapi.styles.domain.StylesheetContent;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.tiles.domain.TileMatrixSet;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * @title SLD 1.0
 */
@Singleton
@AutoBind
public class StyleFormatSld10 implements ConformanceClass, StyleFormatExtension, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(StyleFormatSld10.class);

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.ogc.sld+xml", ImmutableMap.of("version", "1.0")))
          .label("SLD 1.0")
          .parameter("sld10")
          .fileExtension("sld")
          .build();

  private Optional<Validator> validator;

  @Inject
  StyleFormatSld10() {
    validator = Optional.empty();
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    if (isStartupAsync) {
      init();
    } else {
      Executors.newSingleThreadExecutor().submit(this::init);
    }

    return CompletableFuture.completedFuture(null);
  }

  public void init() {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      Schema schema =
          factory.newSchema(Resources.getResource(StyleFormatSld10.class, "/schemas/sld10.xsd"));

      this.validator = Optional.ofNullable(schema.newValidator());
    } catch (SAXException e) {
      LogContext.error(
          LOGGER, e, "StyleFormatSld10 initialization failed, could not process SLD 1.0 XSD");
    }
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    return ImmutableList.of("http://www.opengis.net/spec/ogcapi-styles-1/0.0/conf/sld-10");
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
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
  public String getFileExtension() {
    return "sld10";
  }

  @Override
  public String getSpecification() {
    return "https://www.ogc.org/standards/sld";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public Object getStyleEntity(
      StylesheetContent stylesheetContent,
      OgcApi api,
      Optional<String> collectionId,
      String styleId,
      Optional<TileMatrixSet> tileMatrixSet,
      ApiRequestContext requestContext) {
    return stylesheetContent.getContent();
  }

  @Override
  public Optional<String> analyze(StylesheetContent stylesheetContent, boolean strict) {

    if (strict && validator.isPresent()) {
      try {
        validator
            .get()
            .validate(
                new StreamSource(ByteSource.wrap(stylesheetContent.getContent()).openStream()));
      } catch (IOException | SAXException e) {
        throw new IllegalArgumentException(
            String.format(
                "The SLD 1.0 stylesheet '%s' is invalid.", stylesheetContent.getDescriptor()),
            e);
      }
    }

    // TODO derive name
    return Optional.empty();
  }
}
