/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app;

import static de.ii.xtraplatform.features.gml.domain.GmlVersion.GML32;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration.Conformance;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.ogcapi.features.gml.domain.GmlWriterRegistry;
import de.ii.ogcapi.features.gml.domain.ImmutableFeatureTransformationContextGml;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ConformanceClass;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

/**
 * @title GML
 */
@Singleton
@AutoBind
public class FeaturesFormatGml extends FeatureFormatExtension implements ConformanceClass {

  private static final String XML = "xml";
  private static final String GML21 = "gml21";
  private static final String GML31 = "gml31";
  private static final String GML = "gml";
  private static final String XLINK = "xlink";
  private static final String XSI = "xsi";
  private static final String SF = "sf";
  private static final String WFS = "wfs";
  private static final String GML21_NS = "http://www.opengis.net/gml";
  private static final String GML31_NS = "http://www.opengis.net/gml";
  private static final String GML_NS = "http://www.opengis.net/gml/3.2";
  private static final String XLINK_NS = "http://www.w3.org/1999/xlink";
  private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";
  private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
  private static final String SF_NS = "http://www.opengis.net/ogcapi-features-1/1.0/sf";
  private static final String WFS_NS = "http://www.opengis.net/wfs/2.0";
  static final Map<String, String> STANDARD_NAMESPACES =
      ImmutableMap.of(
          GML, GML_NS, GML21, GML21_NS, GML31, GML31_NS, XLINK, XLINK_NS, XML, XML_NS, XSI, XSI_NS,
          SF, SF_NS, WFS, WFS_NS);

  private static final String GML_XSD = "http://schemas.opengis.net/gml/3.2.1/gml.xsd";
  private static final String GML21_XSD = "https://schemas.opengis.net/gml/2.1.2/gml.xsd";
  private static final String GML31_XSD = "https://schemas.opengis.net/gml/3.1.1/base/gml.xsd";
  private static final String XLINK_XSD = "http://www.w3.org/1999/xlink.xsd";
  private static final String XML_XSD = "http://www.w3.org/2001/xml.xsd";
  private static final String SF_XSD =
      "http://schemas.opengis.net/ogcapi/features/part1/1.0/xml/core-sf.xsd";
  private static final String WFS_XSD = "http://schemas.opengis.net/wfs/2.0/wfs.xsd";
  private static final Map<String, String> STANDARD_SCHEMA_LOCATIONS =
      ImmutableMap.of(
          GML, GML_XSD, GML31, GML31_XSD, GML21, GML21_XSD, XLINK, XLINK_XSD, XML, XML_XSD, SF,
          SF_XSD, WFS, WFS_XSD);

  private static final String GML_UPPERCASE = "GML";
  private static final String APPLICATION = "application";
  private static final String GML_XML = "gml+xml";
  private static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType(APPLICATION, GML_XML))
          .label(GML_UPPERCASE)
          .parameter(XML)
          .build();
  private static final String VERSION = "version";
  private static final String PROFILE = "profile";
  private static final String V_3_2 = "3.2";
  private static final String GMLSF0_PROFILE = "http://www.opengis.net/def/profile/ogc/2.0/gml-sf0";
  private static final ApiMediaType MEDIA_TYPE_GMLSF0 =
      new ImmutableApiMediaType.Builder()
          .type(
              new MediaType(
                  APPLICATION, GML_XML, ImmutableMap.of(VERSION, V_3_2, PROFILE, GMLSF0_PROFILE)))
          .label(GML_UPPERCASE)
          .parameter(XML)
          .build();
  private static final String GMLSF2_PROFILE = "http://www.opengis.net/def/profile/ogc/2.0/gml-sf2";
  private static final ApiMediaType MEDIA_TYPE_GMLSF2 =
      new ImmutableApiMediaType.Builder()
          .type(
              new MediaType(
                  APPLICATION, GML_XML, ImmutableMap.of(VERSION, V_3_2, PROFILE, GMLSF2_PROFILE)))
          .label(GML_UPPERCASE)
          .parameter(XML)
          .build();
  private static final String XML_UPPERCASE = "XML";
  public static final ApiMediaType COLLECTION_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType(APPLICATION, XML))
          .label(XML_UPPERCASE)
          .parameter(XML)
          .build();

  private static final Map<Conformance, ApiMediaType> MEDIA_TYPE_MAP =
      ImmutableMap.of(
          Conformance.NONE, MEDIA_TYPE,
          Conformance.GMLSF0, MEDIA_TYPE_GMLSF0,
          Conformance.GMLSF2, MEDIA_TYPE_GMLSF2);
  private static final String SF_FEATURE_COLLECTION = "sf:FeatureCollection";
  private static final String SF_FEATURE_MEMBER = "sf:featureMember";
  private static final String GMLSF0_CC =
      "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/gmlsf0";
  private static final String GMLSF2_CC =
      "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/gmlsf2";

  private final Values<Codelist> codelistStore;
  private final FeaturesCoreValidation featuresCoreValidator;
  private final GmlWriterRegistry gmlWriterRegistry;

  @Inject
  public FeaturesFormatGml(
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      FeaturesCoreValidation featuresCoreValidator,
      GmlWriterRegistry gmlWriterRegistry,
      ExtensionRegistry extensionRegistry) {
    super(extensionRegistry, providers);
    this.codelistStore = valueStore.forType(Codelist.class);
    this.featuresCoreValidator = featuresCoreValidator;
    this.gmlWriterRegistry = gmlWriterRegistry;
  }

  @Override
  public List<String> getConformanceClassUris(OgcApiDataV2 apiData) {
    switch (getConformance(apiData)) {
      case GMLSF0:
        return ImmutableList.of(GMLSF0_CC);
      case GMLSF2:
        return ImmutableList.of(GMLSF2_CC);
      case NONE:
      default:
        return ImmutableList.of();
    }
  }

  private Conformance getConformance(OgcApiDataV2 apiData) {
    Set<Conformance> conformance =
        apiData.getCollections().values().stream()
            .filter(
                collectionData ->
                    collectionData
                        .getExtension(GmlConfiguration.class)
                        .map(ExtensionConfiguration::isEnabled)
                        .orElse(false))
            .map(
                collectionData ->
                    getConformance(collectionData.getExtension(GmlConfiguration.class)))
            .collect(Collectors.toUnmodifiableSet());
    if (!conformance.contains(Conformance.NONE)) {
      if (conformance.contains(Conformance.GMLSF0)) {
        return Conformance.GMLSF0;
      } else if (conformance.contains(Conformance.GMLSF2)) {
        return Conformance.GMLSF2;
      }
    }
    return Conformance.NONE;
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private Conformance getConformance(Optional<GmlConfiguration> configuration) {
    return configuration
        .filter(c -> !SF_FEATURE_COLLECTION.equals(c.getFeatureCollectionElementName()))
        .filter(c -> !SF_FEATURE_MEMBER.equals(c.getFeatureMemberElementName()))
        .map(c -> Conformance.NONE)
        .orElse(configuration.map(GmlConfiguration::getConformance).orElse(Conformance.NONE));
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
  public ApiMediaTypeContent getFeatureContent(
      OgcApiDataV2 apiData, Optional<String> collectionId, boolean featureCollection) {
    return new ImmutableApiMediaTypeContent.Builder()
        .from(getContent())
        .ogcApiMediaType(MEDIA_TYPE_MAP.get(getConformance(apiData)))
        .build();
  }

  @Override
  public ApiMediaType getCollectionMediaType() {
    return COLLECTION_MEDIA_TYPE;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return GmlConfiguration.class;
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {

    // no additional operational checks for now, only validation; we can stop, if no validation is
    // requested
    if (apiValidation == MODE.NONE) {
      return ValidationResult.of();
    }

    ImmutableValidationResult.Builder builder =
        ImmutableValidationResult.builder().mode(apiValidation);

    Map<String, FeatureSchema> featureSchemas = providers.getFeatureSchemas(api.getData());

    // get GML configurations to process
    Map<String, GmlConfiguration> gmlConfigurationMap =
        api.getData().getCollections().entrySet().stream()
            .map(
                entry -> {
                  final FeatureTypeConfigurationOgcApi collectionData = entry.getValue();
                  final GmlConfiguration config =
                      collectionData.getExtension(GmlConfiguration.class).orElse(null);
                  if (Objects.isNull(config)) {
                    return null;
                  }
                  return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), config);
                })
            .filter(Objects::nonNull)
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    Map<String, Collection<String>> keyMap =
        gmlConfigurationMap.entrySet().stream()
            .map(
                entry ->
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(), entry.getValue().getTransformations().keySet()))
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

    for (Map.Entry<String, Collection<String>> stringCollectionEntry :
        featuresCoreValidator.getInvalidPropertyKeys(keyMap, featureSchemas).entrySet()) {
      for (String property : stringCollectionEntry.getValue()) {
        builder.addStrictErrors(
            MessageFormat.format(
                "A transformation for property ''{0}'' in collection ''{1}'' is invalid, because the property was not found in the provider schema.",
                property, stringCollectionEntry.getKey()));
      }
    }

    for (Map.Entry<String, GmlConfiguration> entry : gmlConfigurationMap.entrySet()) {
      String collectionId = entry.getKey();
      for (Map.Entry<String, List<PropertyTransformation>> entry2 :
          entry.getValue().getTransformations().entrySet()) {
        String property = entry2.getKey();
        for (PropertyTransformation transformation : entry2.getValue()) {
          builder = transformation.validate(builder, collectionId, property, codelistStore.ids());
        }
      }
    }

    return builder.build();
  }

  @Override
  public boolean isRestrictedToSimpleFeaturesGeometries(List<Profile> profiles) {
    return false;
  }

  @Override
  public boolean canEncodeFeatures() {
    return true;
  }

  @Override
  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {

    ImmutableSortedSet<GmlWriter> gmlWriters =
        gmlWriterRegistry.getWriters().stream()
            .map(GmlWriter::create)
            .collect(
                ImmutableSortedSet.toImmutableSortedSet(
                    Comparator.comparingInt(GmlWriter::getSortPriority)));

    @SuppressWarnings("ConstantConditions")
    GmlConfiguration config =
        transformationContext
            .getApiData()
            .getCollections()
            .get(transformationContext.getCollectionId())
            .getExtension(GmlConfiguration.class)
            .orElseThrow();
    ImmutableFeatureTransformationContextGml transformationContextGml =
        ImmutableFeatureTransformationContextGml.builder()
            .from(transformationContext)
            .gmlVersion(Objects.requireNonNullElse(config.getGmlVersion(), GML32))
            .putAllNamespaces(config.getApplicationNamespaces())
            .putAllNamespaces(STANDARD_NAMESPACES)
            .defaultNamespace(Optional.ofNullable(config.getDefaultNamespace()))
            .putAllSchemaLocations(config.getSchemaLocations())
            .putAllSchemaLocations(
                STANDARD_SCHEMA_LOCATIONS.entrySet().stream()
                    .filter(entry -> !config.getSchemaLocations().containsKey(entry.getKey()))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .objectTypeNamespaces(config.getObjectTypeNamespaces())
            .variableObjectElementNames(config.getVariableObjectElementNames())
            .featureCollectionElementName(
                Optional.ofNullable(config.getFeatureCollectionElementName()))
            .featureMemberElementName(Optional.ofNullable(config.getFeatureMemberElementName()))
            .supportsStandardResponseParameters(
                Objects.requireNonNullElse(config.getSupportsStandardResponseParameters(), false))
            .gmlIdOnGeometries(Objects.requireNonNullElse(config.getGmlIdOnGeometries(), false))
            .xmlAttributes(config.getXmlAttributes())
            .gmlIdPrefix(Optional.ofNullable(config.getGmlIdPrefix()))
            .build();

    return Optional.of(new FeatureEncoderGml(transformationContextGml, gmlWriters));
  }

  @Override
  public boolean isComplex() {
    return true;
  }
}
