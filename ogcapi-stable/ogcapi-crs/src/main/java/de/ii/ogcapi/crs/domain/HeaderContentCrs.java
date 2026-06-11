/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crs.domain;

import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ApiExtensionCache;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Shared base for the {@code Content-Crs} header. Provides the OpenAPI schema (an enum of the API's
 * supported CRS URIs wrapped in {@code <…>}), the static request-side {@link #parse parser}, and
 * the common header metadata. Subclasses supply only the per-building-block bits: id, description,
 * applicability gate, building-block configuration, and (for requests) which methods/paths apply.
 */
public abstract class HeaderContentCrs extends ApiExtensionCache implements ApiHeader {

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();
  protected final SchemaValidator schemaValidator;
  protected final CrsSupport crsSupport;

  protected HeaderContentCrs(SchemaValidator schemaValidator, CrsSupport crsSupport) {
    this.schemaValidator = schemaValidator;
    this.crsSupport = crsSupport;
  }

  @Override
  public final String getName() {
    return "Content-Crs";
  }

  @Override
  public final Schema<?> getSchema(OgcApiDataV2 apiData) {
    return schemaMap.computeIfAbsent(
        apiData.hashCode(),
        h -> {
          List<String> crsList =
              crsSupport.getSupportedCrsList(apiData).stream()
                  .flatMap(
                      crs ->
                          Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
                  .filter(Objects::nonNull)
                  .map(HeaderContentCrs::toUriInHeader)
                  .collect(ImmutableList.toImmutableList());
          String defaultCrs =
              toUriInHeader(
                  apiData
                      .getExtension(FeaturesCoreConfiguration.class)
                      .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
                      .map(EpsgCrs::toUriString)
                      .orElse(OgcCrs.CRS84_URI));
          return new StringSchema()._enum(crsList)._default(defaultCrs);
        });
  }

  @Override
  public final SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  private static String toUriInHeader(String uri) {
    return String.format("<%s>", uri);
  }

  /**
   * Parse the {@code Content-Crs} request header into an {@link EpsgCrs}, falling back to the API's
   * default CRS when the header is absent. Rejects values that are syntactically invalid or not in
   * the API's supported CRS list.
   *
   * @throws IllegalArgumentException if the header is present but cannot be parsed or names a CRS
   *     that the API does not advertise
   */
  public static EpsgCrs parse(String header, OgcApiDataV2 apiData, CrsSupport crsSupport) {
    if (header == null || header.isBlank()) {
      return defaultCrs(apiData);
    }
    String value = header.trim();
    if (value.startsWith("<") && value.endsWith(">")) {
      value = value.substring(1, value.length() - 1);
    }
    EpsgCrs crs;
    try {
      crs = EpsgCrs.fromString(value);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid Content-Crs header: " + header);
    }
    if (!crsSupport.isSupported(apiData, crs)) {
      throw new IllegalArgumentException(
          "Content-Crs '" + header + "' is not a supported CRS for this API");
    }
    return crs;
  }

  private static EpsgCrs defaultCrs(OgcApiDataV2 apiData) {
    return apiData.getCollections().values().stream()
        .findFirst()
        .flatMap(cd -> cd.getExtension(FeaturesCoreConfiguration.class))
        .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
        .orElse(OgcCrs.CRS84);
  }
}
