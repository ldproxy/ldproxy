/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.crs.domain.CrsConfiguration;
import de.ii.ogcapi.crs.domain.CrsSupport;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.foundation.domain.ApiExtensionCache;
import de.ii.ogcapi.foundation.domain.ApiHeader;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.transactions.domain.TransactionsConfiguration;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

@Singleton
@AutoBind
public class HeaderContentCrsTransaction extends ApiExtensionCache implements ApiHeader {

  private final SchemaValidator schemaValidator;
  private final CrsSupport crsSupport;

  @Inject
  HeaderContentCrsTransaction(SchemaValidator schemaValidator, CrsSupport crsSupport) {
    this.schemaValidator = schemaValidator;
    this.crsSupport = crsSupport;
  }

  @Override
  public String getId() {
    return "ContentCrsTransaction";
  }

  @Override
  public String getName() {
    return "Content-Crs";
  }

  @Override
  public String getDescription() {
    return "The coordinate reference system of coordinates in the transaction request body.";
  }

  @Override
  public boolean isRequestHeader() {
    return true;
  }

  @Override
  public boolean isApplicable(OgcApiDataV2 apiData, String definitionPath, HttpMethods method) {
    return computeIfAbsent(
        this.getClass().getCanonicalName() + apiData.hashCode() + definitionPath + method.name(),
        () ->
            isEnabledForApi(apiData)
                && method == HttpMethods.POST
                && "/transactions".equals(definitionPath));
  }

  private final ConcurrentMap<Integer, Schema<?>> schemaMap = new ConcurrentHashMap<>();

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    int apiHashCode = apiData.hashCode();
    if (!schemaMap.containsKey(apiHashCode)) {
      List<String> crsList =
          crsSupport.getSupportedCrsList(apiData).stream()
              .flatMap(
                  crs -> Stream.of(crs.toUriString(), crs.toAlternativeUriString().orElse(null)))
              .filter(Objects::nonNull)
              .map(this::toUriInHeader)
              .collect(ImmutableList.toImmutableList());
      String defaultCrs =
          toUriInHeader(
              apiData
                  .getExtension(FeaturesCoreConfiguration.class)
                  .map(FeaturesCoreConfiguration::getDefaultEpsgCrs)
                  .map(EpsgCrs::toUriString)
                  .orElse(OgcCrs.CRS84_URI));
      schemaMap.put(apiHashCode, new StringSchema()._enum(crsList)._default(defaultCrs));
    }
    return schemaMap.get(apiHashCode);
  }

  private String toUriInHeader(String uri) {
    return String.format("<%s>", uri);
  }

  /**
   * Parse the {@code Content-Crs} request header into an {@link EpsgCrs}, falling back to the API's
   * default CRS when the header is absent. Rejects values that are syntactically invalid or not in
   * the API's supported CRS list.
   *
   * @throws BadRequestException if the header is present but cannot be parsed or names a CRS that
   *     the API does not advertise
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
      throw new BadRequestException("Invalid Content-Crs header: " + header);
    }
    if (!crsSupport.isSupported(apiData, crs)) {
      throw new BadRequestException(
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

  @Override
  public SchemaValidator getSchemaValidator() {
    return schemaValidator;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return isExtensionEnabled(apiData, TransactionsConfiguration.class)
        && isExtensionEnabled(apiData, CrsConfiguration.class);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return TransactionsConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return TransactionsBuildingBlock.MATURITY;
  }

  @Override
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return TransactionsBuildingBlock.SPEC;
  }
}
