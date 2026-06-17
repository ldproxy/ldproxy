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
import de.ii.ogcapi.features.core.domain.DecoderContext;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreValidation;
import de.ii.ogcapi.features.core.domain.ValidatorContext;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration;
import de.ii.ogcapi.features.gml.domain.GmlConfiguration.Conformance;
import de.ii.ogcapi.features.gml.domain.GmlWriter;
import de.ii.ogcapi.features.gml.domain.GmlWriterRegistry;
import de.ii.ogcapi.features.gml.domain.ImmutableFeatureTransformationContextGml;
import de.ii.ogcapi.features.gml.domain.SrsNameMapping;
import de.ii.ogcapi.features.gml.domain.UomMapping;
import de.ii.ogcapi.features.gml.domain.VariableName;
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
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.features.domain.SchemaMapping;
import de.ii.xtraplatform.features.domain.pipeline.FeatureEventHandlerSimple.ModifiableContext;
import de.ii.xtraplatform.features.domain.pipeline.FeatureTokenDecoderSimple;
import de.ii.xtraplatform.features.domain.transform.PropertyTransformation;
import de.ii.xtraplatform.features.gml.domain.FeatureTokenDecoderGml;
import de.ii.xtraplatform.features.gml.domain.FeatureTokenDecoderGmlInputProfile;
import de.ii.xtraplatform.features.gml.domain.ImmutableFeatureTokenDecoderGmlInputProfile;
import de.ii.xtraplatform.features.gml.domain.ImmutableVariableObjectName;
import de.ii.xtraplatform.features.gml.domain.VariableObjectName;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @title GML
 */
@Singleton
@AutoBind
public class FeaturesFormatGml extends FeatureFormatExtension implements ConformanceClass {

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesFormatGml.class);

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

  static final String XSD_CATALOG_STORE_TYPE = "xsdCatalog";

  private final Values<Codelist> codelistStore;
  private final FeaturesCoreValidation featuresCoreValidator;
  private final GmlWriterRegistry gmlWriterRegistry;
  private final ResourceStore xsdCatalogStore;
  private final ConcurrentMap<Integer, ConcurrentMap<String, Schema>> schemaCache =
      new ConcurrentHashMap<>();
  // Per-thread Validator instances keyed by their Schema (identity). Each `validate()` call
  // would otherwise pay Xerces' full Validator instantiation cost (XML11Configuration init,
  // XSDHandler.reset, addRecognizedParamsAndSetDefaults) — which is ~1 ms each and dominates
  // strict-mode overhead once schema build is cached. Validators are not thread-safe, so the
  // pool is ThreadLocal; the schema-keyed inner map lets a single thread keep separate
  // Validators for separate Schemas. `Validator.reset()` is called before every use to clear
  // per-validation state — the alternative (clobbering schemas mid-flight) is the documented
  // unsafe usage.
  private final ThreadLocal<Map<Schema, Validator>> validatorPool =
      ThreadLocal.withInitial(IdentityHashMap::new);
  private final ConcurrentMap<Integer, ConcurrentMap<String, SchemaMapping>> schemaMappingCache =
      new ConcurrentHashMap<>();

  @Inject
  public FeaturesFormatGml(
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      FeaturesCoreValidation featuresCoreValidator,
      GmlWriterRegistry gmlWriterRegistry,
      ExtensionRegistry extensionRegistry,
      ResourceStore blobStore) {
    super(extensionRegistry, providers);
    this.codelistStore = valueStore.forType(Codelist.class);
    this.featuresCoreValidator = featuresCoreValidator;
    this.gmlWriterRegistry = gmlWriterRegistry;
    this.xsdCatalogStore = blobStore.with(XSD_CATALOG_STORE_TYPE);
  }

  private static String upgradeToHttps(String url) {
    return url != null && url.startsWith("http://") ? "https://" + url.substring(7) : url;
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
  public boolean requiresPropertiesInSequence(FeatureSchema schema) {
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
    FeatureTypeConfigurationOgcApi collectionData =
        transformationContext
            .getApiData()
            .getCollections()
            .get(transformationContext.getCollectionId());
    GmlConfiguration config = collectionData.getExtension(GmlConfiguration.class).orElseThrow();
    // When useAlias is on, the rename transformer (injected by FeatureSchemaAliases) rewrites
    // schema names to their aliases, so GmlWriterProperties' runtime lookups
    // (containsKey(schema.getFullPathAsString())) come in with alias-form paths. The config map
    // keys, however, are written in technical names — that is the form the operator uses
    // everywhere else (the SchemaConstraints.codelist on the schema, the decoder side, the
    // provider config). Translate config-side technical paths to alias-form paths here so the
    // runtime lookup matches; without this remap, codelist properties silently fall through to
    // a plain <prop>value</prop> element instead of <prop xlink:href="…"/>, and xmlAttributes /
    // valueWrap config entries are silently ignored for any aliased ancestor path.
    Map<String, String> aliasRewrites =
        config.isUseAlias()
            ? providers
                .getFeatureSchema(transformationContext.getApiData(), collectionData)
                .map(FeaturesFormatGml::buildAliasPathRewrites)
                .orElse(Map.of())
            : Map.of();
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
            .srsDimension(Objects.requireNonNullElse(config.getSrsDimension(), false))
            .useSurfaceAndCurve(Objects.requireNonNullElse(config.getUseSurfaceAndCurve(), false))
            .forceCompositeCurve(Objects.requireNonNullElse(config.getForceCompositeCurve(), false))
            .xmlAttributes(remapList(config.getXmlAttributes(), aliasRewrites))
            .gmlIdPrefix(Optional.ofNullable(config.getGmlIdPrefix()))
            .srsNameStyle(Optional.ofNullable(config.getSrsNameStyle()))
            .srsNameMappings(config.getSrsNameMappings())
            .uomStyle(Optional.ofNullable(config.getUomStyle()))
            .uomMappings(config.getUomMappings())
            .featureRefTemplate(Optional.ofNullable(config.getFeatureRefTemplate()))
            .gmlIdentifier(Optional.ofNullable(config.getGmlIdentifier()))
            .appendTemporalSuffixToGmlId(
                Boolean.TRUE.equals(config.getAppendTemporalSuffixToGmlId())
                    && isDatetimeIntervalRequest(transformationContext))
            .codelistUriTemplate(Optional.ofNullable(config.getCodelistUriTemplate()))
            .codelistProperties(remapKeys(config.getCodelistProperties(), aliasRewrites))
            .codelists(resolveCodelists(config.getCodelistProperties()))
            .valueWrap(remapKeys(config.getValueWrap(), aliasRewrites))
            .build();

    return Optional.of(new FeatureEncoderGml(transformationContextGml, gmlWriters));
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
  }

  @Override
  public Optional<
          FeatureTokenDecoderSimple<
              byte[],
              FeatureSchema,
              SchemaMapping,
              ModifiableContext<FeatureSchema, SchemaMapping>>>
      getFeatureDecoder(DecoderContext decoderContext) {
    FeatureSchema featureSchema = decoderContext.getFeatureSchema();
    GmlConfiguration config =
        decoderContext
            .getApiData()
            .getCollections()
            .get(decoderContext.getCollectionId())
            .getExtension(GmlConfiguration.class)
            .orElseThrow();

    Map<String, String> namespaces = new LinkedHashMap<>(STANDARD_NAMESPACES);
    namespaces.putAll(config.getApplicationNamespaces());

    SchemaMapping mapping =
        schemaMappingCache
            .computeIfAbsent(decoderContext.getApiData().hashCode(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(
                decoderContext.getCollectionId(), k -> SchemaMapping.of(featureSchema));

    return Optional.of(
        new FeatureTokenDecoderGml(
            namespaces,
            List.of(featureTypeQName(featureSchema, config, namespaces)),
            featureSchema,
            ImmutableFeatureQuery.builder().type(featureSchema.getName()).build(),
            Map.of(featureSchema.getName(), mapping),
            decoderContext.getCrs(),
            Optional.empty(),
            Optional.empty(),
            toInputProfile(config)));
  }

  @Override
  public void validate(String content, ValidatorContext ctx) {
    Schema schema = getOrBuildSchema(ctx);
    if (schema == null) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "No schema available for validating GML input for collection '{}', skipping validation.",
            ctx.getCollectionId());
      }
      return;
    }
    Validator validator = borrowValidator(schema);
    try {
      validator.validate(new StreamSource(new StringReader(content)));
    } catch (SAXException e) {
      throw new IllegalArgumentException(
          "XML content is invalid, feature mutation is rejected: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new IllegalStateException("Could not validate feature. Reason: " + e.getMessage(), e);
    }
  }

  // Returns the per-thread Validator for `schema`, creating it on first use and resetting it on
  // every subsequent borrow. See the field-level comment on validatorPool for the rationale.
  private Validator borrowValidator(Schema schema) {
    Map<Schema, Validator> perThread = validatorPool.get();
    Validator validator = perThread.get(schema);
    if (validator == null) {
      validator = schema.newValidator();
      perThread.put(schema, validator);
    } else {
      validator.reset();
    }
    return validator;
  }

  private Schema getOrBuildSchema(ValidatorContext ctx) {
    int apiHashCode = ctx.getApiData().hashCode();
    String collectionId = ctx.getCollectionId();
    ConcurrentMap<String, Schema> perApi =
        schemaCache.computeIfAbsent(apiHashCode, k -> new ConcurrentHashMap<>());
    // Two-level lookup so an api-wide Schema is built at most once per distinct
    // schemaLocations set: (1) the per-collection slot caches the Schema reference for
    // O(1) subsequent calls; (2) the canonical-fingerprint slot deduplicates across all
    // collections that share the same schemaLocations (the common case — the GML building
    // block is api-level and inherits down). Without (2), an api with N collections would
    // pay N × full-Schema-build on the first transaction touching each collection (the
    // build cost dominates strict-mode validation overhead because it re-parses every
    // transitive XSD in the catalog, including Xerces' internal exception-driven property
    // probing).
    Schema cached = perApi.get(collectionId);
    if (cached != null) {
      return cached;
    }
    String fingerprint = schemaLocationsFingerprint(ctx.getApiData(), collectionId);
    if (fingerprint != null) {
      Schema sharedByFingerprint = perApi.get(fingerprint);
      if (sharedByFingerprint != null) {
        perApi.put(collectionId, sharedByFingerprint);
        return sharedByFingerprint;
      }
    }
    Schema built = buildSchema(ctx).orElse(null);
    if (built != null) {
      perApi.put(collectionId, built);
      if (fingerprint != null) {
        perApi.put(fingerprint, built);
      }
    }
    return built;
  }

  // Stable identifier for a collection's schemaLocations set — sorted, comma-joined, prefixed
  // with a sentinel that can't appear in a collection id so the fingerprint slot can't collide
  // with a collection-id slot in the same map. Returns null when the GML extension is absent
  // or has no schemaLocations entries (the caller skips the fingerprint dedup).
  private static String schemaLocationsFingerprint(OgcApiDataV2 apiData, String collectionId) {
    return apiData
        .getCollectionData(collectionId)
        .flatMap(c -> c.getExtension(GmlConfiguration.class))
        .map(GmlConfiguration::getSchemaLocations)
        .filter(m -> !m.isEmpty())
        .map(
            m ->
                m.values().stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.joining(",", "@xsd:", "")))
        .orElse(null);
  }

  private Optional<Schema> buildSchema(ValidatorContext ctx) {
    return ctx.getApiData()
        .getCollectionData(ctx.getCollectionId())
        .flatMap(
            collectionData ->
                collectionData
                    .getExtension(GmlConfiguration.class)
                    .flatMap(
                        cfg -> {
                          Map<String, String> catalog = cfg.getXsdCatalog();
                          SchemaFactory factory =
                              SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                          factory.setResourceResolver(new XsdCatalogResolver(catalog));
                          try {
                            return Optional.of(
                                factory.newSchema(
                                    cfg.getSchemaLocations().values().stream()
                                        .map(
                                            url -> {
                                              String resolved = upgradeToHttps(url);
                                              StreamSource cached =
                                                  openFromCatalog(catalog, resolved);
                                              if (cached != null) {
                                                return cached;
                                              }
                                              try {
                                                return new StreamSource(
                                                    new URL(resolved).openStream(), resolved);
                                              } catch (IOException e) {
                                                if (LOGGER.isWarnEnabled()) {
                                                  LOGGER.warn(
                                                      "Could not load schema from location '{}' for validating GML input for collection '{}'. Reason: {}",
                                                      resolved,
                                                      ctx.getCollectionId(),
                                                      e.getMessage());
                                                }
                                              }
                                              return null;
                                            })
                                        .filter(Objects::nonNull)
                                        .toArray(StreamSource[]::new)));
                          } catch (SAXParseException e) {
                            if (LOGGER.isWarnEnabled()) {
                              LOGGER.warn(
                                  "Could not create schema for validating GML input for collection '{}'. Source: {}. Reason: {}",
                                  ctx.getCollectionId(),
                                  e.getSystemId(),
                                  e.getMessage());
                            }
                          } catch (SAXException e) {
                            if (LOGGER.isWarnEnabled()) {
                              LOGGER.warn(
                                  "Could not create schema for validating GML input for collection '{}'. Reason: {}",
                                  ctx.getCollectionId(),
                                  e.getMessage());
                            }
                          }
                          return Optional.empty();
                        }));
  }

  private StreamSource openFromCatalog(Map<String, String> catalog, String url) {
    if (catalog == null || catalog.isEmpty() || url == null) {
      return null;
    }
    String relative = catalog.get(url);
    // Catalog entries are also matched against the http→https-upgraded form so a user can list the
    // URL exactly as it appears in their schema imports without worrying which prefix the
    // validator normalises to.
    if (relative == null && url.startsWith("https://")) {
      relative = catalog.get("http://" + url.substring("https://".length()));
    }
    if (relative == null) {
      return null;
    }
    try {
      Optional<Blob> blob = xsdCatalogStore.get(Path.of(relative));
      if (blob.isEmpty()) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(
              "xsdCatalog entry for '{}' points to '{}' but no such file exists in the xsdCatalog resource store.",
              url,
              relative);
        }
        return null;
      }
      // The resolved systemId is the remote URL — that keeps nested schemaLocation hints relative
      // to the original repository and gives them a stable lookup key for the catalog itself.
      byte[] bytes = blob.get().content();
      StreamSource source = new StreamSource(new ByteArrayInputStream(bytes));
      source.setSystemId(url);
      return source;
    } catch (IOException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Could not read xsdCatalog entry '{}' for '{}'. Reason: {}",
            relative,
            url,
            e.getMessage());
      }
      return null;
    }
  }

  private static QName featureTypeQName(
      FeatureSchema schema, GmlConfiguration config, Map<String, String> namespaces) {
    String objectType = schema.getObjectType().orElse(schema.getName());
    String prefix = config.getObjectTypeNamespaces().get(objectType);
    if (prefix == null) {
      prefix = config.getDefaultNamespace();
    }
    String namespaceUri = prefix == null ? "" : namespaces.getOrDefault(prefix, "");
    return new QName(namespaceUri, objectType);
  }

  // Package-private for unit testing of the GmlConfiguration → decoder-input-profile mapping.
  static FeatureTokenDecoderGmlInputProfile toInputProfile(GmlConfiguration config) {
    Map<String, EpsgCrs> srsNameMappings =
        config.getSrsNameMappings().stream()
            .collect(
                Collectors.toUnmodifiableMap(SrsNameMapping::getValue, SrsNameMapping::getCrs));
    // Decoder needs wire→canonical (the inverse of the encoder direction):
    // FeatureTokenDecoderGml.validateUom looks up the incoming `uom` attribute (wire form, e.g.
    // 'urn:adv:uom:m2') and compares the result against the schema's unit (canonical, e.g. 'm2').
    // The configured list-of-pairs is encoder-shaped (`uom: <canonical>, value: <wire>`), so the
    // value/key roles flip here — mirrors how srsNameMappings is reduced just above.
    Map<String, String> uomMappings =
        config.getUomMappings().stream()
            .collect(Collectors.toUnmodifiableMap(UomMapping::getValue, UomMapping::getUom));
    Map<String, VariableObjectName> variableObjectElementNames =
        config.getVariableObjectElementNames().entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, e -> toVariableObjectName(e.getValue())));

    return ImmutableFeatureTokenDecoderGmlInputProfile.builder()
        .srsNameMappings(srsNameMappings)
        .gmlIdPrefix(Objects.requireNonNullElse(config.getGmlIdPrefix(), ""))
        .codelistProperties(config.getCodelistProperties())
        .featureRefTemplate(Objects.requireNonNullElse(config.getFeatureRefTemplate(), ""))
        .codelistUriTemplate(Objects.requireNonNullElse(config.getCodelistUriTemplate(), ""))
        .uomMappings(uomMappings)
        .useAlias(config.isUseAlias())
        .applicationNamespaces(config.getApplicationNamespaces())
        .defaultNamespace(Objects.requireNonNullElse(config.getDefaultNamespace(), ""))
        .objectTypeNamespaces(config.getObjectTypeNamespaces())
        .variableObjectElementNames(variableObjectElementNames)
        .xmlAttributes(config.getXmlAttributes())
        .valueWrap(config.getValueWrap())
        .build();
  }

  private static VariableObjectName toVariableObjectName(VariableName variableName) {
    Map<String, String> reversed =
        variableName.getMapping().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
    return ImmutableVariableObjectName.builder()
        .property(variableName.getProperty())
        .mapping(reversed)
        .build();
  }

  @Override
  public boolean isComplex() {
    return true;
  }

  private static boolean isDatetimeIntervalRequest(FeatureTransformationContext context) {
    String datetime = context.getOgcApiRequest().getParameters().get("datetime");
    return datetime != null && datetime.contains("/");
  }

  // Walks the (technical-named) feature schema and builds a map from each property's full
  // technical path (".-joined) to the corresponding full alias path. Properties whose own name
  // and aliases-of-all-ancestors equal the technical names are omitted (the identity rewrite is
  // implicit in the consumers via getOrDefault). Package-private for unit testing.
  static Map<String, String> buildAliasPathRewrites(FeatureSchema schema) {
    Map<String, String> rewrites = new LinkedHashMap<>();
    collectAliasPathRewrites(schema, "", "", rewrites);
    return rewrites;
  }

  private static void collectAliasPathRewrites(
      FeatureSchema schema, String techParent, String aliasParent, Map<String, String> out) {
    for (FeatureSchema child : schema.getProperties()) {
      String techName = child.getName();
      String aliasName = child.getAlias().orElse(techName);
      String techPath = techParent.isEmpty() ? techName : techParent + "." + techName;
      String aliasPath = aliasParent.isEmpty() ? aliasName : aliasParent + "." + aliasName;
      if (!techPath.equals(aliasPath)) {
        out.put(techPath, aliasPath);
      }
      collectAliasPathRewrites(child, techPath, aliasPath, out);
    }
  }

  // Identity-pass when the rewrite map is empty, so the common (useAlias=false) path is a no-op.
  static <V> Map<String, V> remapKeys(Map<String, V> map, Map<String, String> rewrites) {
    if (rewrites.isEmpty() || map == null || map.isEmpty()) {
      return map;
    }
    Map<String, V> remapped = new LinkedHashMap<>(map.size());
    map.forEach((k, v) -> remapped.put(rewrites.getOrDefault(k, k), v));
    return remapped;
  }

  static List<String> remapList(List<String> list, Map<String, String> rewrites) {
    if (rewrites.isEmpty() || list == null || list.isEmpty()) {
      return list;
    }
    return list.stream().map(k -> rewrites.getOrDefault(k, k)).toList();
  }

  private Map<String, Codelist> resolveCodelists(Map<String, String> codelistProperties) {
    if (codelistProperties == null || codelistProperties.isEmpty()) {
      return ImmutableMap.of();
    }
    ImmutableMap.Builder<String, Codelist> result = ImmutableMap.builder();
    codelistProperties.values().stream()
        .distinct()
        .forEach(
            id -> {
              Codelist cl = codelistStore.get(id);
              if (cl != null) {
                result.put(id, cl);
              }
            });
    return result.build();
  }

  @Override
  public boolean supportsNullVsMissing() {
    return true;
  }

  // Resolves nested schemaLocation/import references during XML Schema validation. Lookup order:
  //   1. xsdCatalog entry for the systemId (or its http→https equivalent) — read from the
  //      xsdCatalog resource store so deployments behind a network restriction can run.
  //   2. https-upgrade for http:// systemIds — opens the canonical https mirror so historical
  //      http://schemas.opengis.net/... imports resolve without an external redirect.
  // Other systemIds are left to the platform default (relative URL resolution against baseURI).
  private final class XsdCatalogResolver implements LSResourceResolver {
    private final Map<String, String> catalog;

    XsdCatalogResolver(Map<String, String> catalog) {
      this.catalog = catalog;
    }

    @Override
    public LSInput resolveResource(
        String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      if (systemId == null) {
        return null;
      }
      StreamSource cached = openFromCatalog(catalog, systemId);
      if (cached != null) {
        return new SimpleLSInput(cached.getInputStream(), cached.getSystemId(), publicId, baseURI);
      }
      if (!systemId.startsWith("http://")) {
        return null;
      }
      String upgraded = upgradeToHttps(systemId);
      try {
        InputStream stream = new URL(upgraded).openStream();
        return new SimpleLSInput(stream, upgraded, publicId, baseURI);
      } catch (IOException e) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn(
              "Could not upgrade schema location '{}' to https. Reason: {}",
              systemId,
              e.getMessage());
        }
        return null;
      }
    }
  }

  private static final class SimpleLSInput implements LSInput {
    private InputStream byteStream;
    private String systemId;
    private String publicId;
    private String baseURI;
    private String encoding;
    private boolean certifiedText;

    SimpleLSInput(InputStream byteStream, String systemId, String publicId, String baseURI) {
      this.byteStream = byteStream;
      this.systemId = systemId;
      this.publicId = publicId;
      this.baseURI = baseURI;
    }

    @Override
    public Reader getCharacterStream() {
      return null;
    }

    @Override
    public void setCharacterStream(Reader characterStream) {}

    @Override
    public InputStream getByteStream() {
      return byteStream;
    }

    @Override
    public void setByteStream(InputStream byteStream) {
      this.byteStream = byteStream;
    }

    @Override
    public String getStringData() {
      return null;
    }

    @Override
    public void setStringData(String stringData) {}

    @Override
    public String getSystemId() {
      return systemId;
    }

    @Override
    public void setSystemId(String systemId) {
      this.systemId = systemId;
    }

    @Override
    public String getPublicId() {
      return publicId;
    }

    @Override
    public void setPublicId(String publicId) {
      this.publicId = publicId;
    }

    @Override
    public String getBaseURI() {
      return baseURI;
    }

    @Override
    public void setBaseURI(String baseURI) {
      this.baseURI = baseURI;
    }

    @Override
    public String getEncoding() {
      return encoding;
    }

    @Override
    public void setEncoding(String encoding) {
      this.encoding = encoding;
    }

    @Override
    public boolean getCertifiedText() {
      return certifiedText;
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {
      this.certifiedText = certifiedText;
    }
  }
}
