/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.gltf.domain.GltfConfiguration;
import de.ii.ogcapi.features.gltf.domain.GltfPropertyDefinition;
import de.ii.ogcapi.features.gltf.domain.GltfSchema;
import de.ii.ogcapi.features.gltf.domain.ImmutableGltfSchema;
import de.ii.ogcapi.features.gltf.domain.ImmutableGltfSchema.Builder;
import de.ii.ogcapi.features.gltf.domain.ImmutableSchemaClass;
import de.ii.ogcapi.features.gltf.domain.ImmutableSchemaEnum;
import de.ii.ogcapi.features.gltf.domain.ImmutableSchemaEnumValue;
import de.ii.ogcapi.features.gltf.domain.ImmutableSchemaProperty;
import de.ii.ogcapi.features.gltf.domain.Metadata3dSchemaCache;
import de.ii.ogcapi.features.gltf.domain.SchemaProperty.ComponentType;
import de.ii.ogcapi.features.gltf.domain.SchemaProperty.Type;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class Metadata3dSchemaCacheImpl implements Metadata3dSchemaCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(Metadata3dSchemaCacheImpl.class);

  private final ConcurrentMap<Integer, ConcurrentMap<String, GltfSchema>> cache;

  @Inject
  protected Metadata3dSchemaCacheImpl() {
    this.cache = new ConcurrentHashMap<>();
  }

  @Override
  public final GltfSchema getSchema(
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      String collectionId,
      Map<String, Codelist> codelists) {
    int apiHashCode = apiData.hashCode();

    if (!cache.containsKey(apiHashCode) || !cache.get(apiHashCode).containsKey(collectionId)) {
      synchronized (this) {
        if (!cache.containsKey(apiHashCode)) {
          cache.put(apiHashCode, new ConcurrentHashMap<>());
        }
        if (!cache.get(apiHashCode).containsKey(collectionId)) {
          cache
              .get(apiHashCode)
              .put(collectionId, deriveSchema(featureSchema, apiData, collectionId, codelists));
        }
      }
    }

    return cache.get(apiHashCode).get(collectionId);
  }

  protected GltfSchema deriveSchema(
      FeatureSchema schema,
      OgcApiDataV2 apiData,
      String collectionId,
      Map<String, Codelist> codelists) {

    boolean withSurfaceTypes =
        apiData
                .getExtension(GltfConfiguration.class, collectionId)
                .map(GltfConfiguration::includeSurfaceType)
                .orElse(false)
            && withSurfaceTypes(schema);
    Map<String, GltfPropertyDefinition> properties =
        apiData
            .getExtension(GltfConfiguration.class, collectionId)
            .map(GltfConfiguration::getProperties)
            .orElseThrow();

    boolean hasSchema = false;
    Builder schemaBuilder = ImmutableGltfSchema.builder().id(apiData.getId() + "_" + collectionId);
    ImmutableSchemaClass.Builder classBuilder = ImmutableSchemaClass.builder();
    if (!properties.isEmpty()) {
      hasSchema = true;

      Map<String, Map<String, Integer>> propertyEnums = getEnums(schema, properties);

      for (FeatureSchema property : schema.getProperties()) {
        if (properties.containsKey(property.getName())) {
          processProperty(
              property,
              properties.get(property.getName()),
              schemaBuilder,
              classBuilder,
              propertyEnums,
              codelists);
        }
      }
    }

    if (withSurfaceTypes) {
      hasSchema = addSurfaceType(schemaBuilder, classBuilder);
    }

    if (!hasSchema) {
      return null;
    }

    schemaBuilder.putClasses(collectionId, classBuilder.build());
    return schemaBuilder.build();
  }

  private void processProperty(
      FeatureSchema property,
      GltfPropertyDefinition gltfPropertyDefinition,
      Builder schemaBuilder,
      ImmutableSchemaClass.Builder classBuilder,
      Map<String, Map<String, Integer>> propertyEnums,
      Map<String, Codelist> codelists) {
    Type type = gltfPropertyDefinition.getType();
    Optional<ComponentType> componentType = gltfPropertyDefinition.getComponentType();
    String noData = gltfPropertyDefinition.getNoData();
    ImmutableSchemaProperty.Builder propertyBuilder = ImmutableSchemaProperty.builder();

    if (type == Type.ENUM && propertyEnums.containsKey(property.getName())) {
      // 1) enum based on enum values
      processEnum(schemaBuilder, propertyEnums, property, componentType, noData, propertyBuilder);
    } else if (type == Type.ENUM
        && property.getConstraints().filter(c -> c.getCodelist().isPresent()).isPresent()) {
      // 2) enum based on a codelist with integer keys
      processCodelist(codelists, schemaBuilder, property, componentType, noData, propertyBuilder);
    } else {
      // 3) just based on the specified type for the property
      propertyBuilder.type(type);
      switch (type) {
        case STRING:
          propertyBuilder.noData(noData);
          break;
        case SCALAR:
          propertyBuilder.componentType(componentType.orElse(ComponentType.UINT32));
          propertyBuilder.noData(noData);
          break;
        default:
          // unsupported type
          if (LOGGER.isErrorEnabled()) {
            LOGGER.error(
                "Currently only SCALAR, STRING and ENUM types are supported. Found: '{}' for property '{}'. The property is ignored.",
                type.name(),
                property.getName());
          }
          return;
      }
    }

    property
        .getDescription()
        .ifPresentOrElse(
            propertyBuilder::description, () -> propertyBuilder.description(property.getLabel()));
    property
        .getConstraints()
        .flatMap(SchemaConstraints::getRequired)
        .ifPresent(propertyBuilder::required);
    classBuilder.putProperties(property.getName(), propertyBuilder.build());
  }

  private boolean addSurfaceType(Builder schemaBuilder, ImmutableSchemaClass.Builder classBuilder) {
    boolean hasSchema;
    hasSchema = true;
    schemaBuilder.putEnums(
        FeatureEncoderGltf.SURFACE_TYPE,
        ImmutableSchemaEnum.builder()
            .name("Semantic Surface Types")
            .valueType(ComponentType.INT8)
            .values(
                FeatureEncoderGltf.SURFACE_TYPE_ENUMS.entrySet().stream()
                    .map(
                        entry ->
                            ImmutableSchemaEnumValue.builder()
                                .value(entry.getValue())
                                .name(entry.getKey())
                                .build())
                    .collect(Collectors.toUnmodifiableList()))
            .build());

    classBuilder.putProperties(
        "surfaceType",
        ImmutableSchemaProperty.builder()
            .type(Type.ENUM)
            .enumType(FeatureEncoderGltf.SURFACE_TYPE)
            .build());
    return hasSchema;
  }

  private void processCodelist(
      Map<String, Codelist> codelists,
      Builder schemaBuilder,
      FeatureSchema property,
      Optional<ComponentType> componentType,
      String noData,
      ImmutableSchemaProperty.Builder propertyBuilder) {
    ImmutableSchemaEnum.Builder schemaEnumBuilder =
        getSchemaEnumBuilder(property, componentType, noData);
    property
        .getConstraints()
        .flatMap(SchemaConstraints::getCodelist)
        .flatMap(codelist -> Optional.ofNullable(codelists.get(codelist)).map(Codelist::getEntries))
        .ifPresent(
            codelistValues ->
                codelistValues.forEach(
                    (code, value) ->
                        schemaEnumBuilder.addValues(
                            ImmutableSchemaEnumValue.builder()
                                .value(Integer.parseInt(code))
                                .name(value)
                                .build())));
    schemaBuilder.putEnums(property.getName(), schemaEnumBuilder.build());
    propertyBuilder.type(Type.ENUM);
    propertyBuilder.enumType(property.getName());
    propertyBuilder.noData(noData);
  }

  private void processEnum(
      Builder schemaBuilder,
      Map<String, Map<String, Integer>> propertyEnums,
      FeatureSchema property,
      Optional<ComponentType> componentType,
      String noData,
      ImmutableSchemaProperty.Builder propertyBuilder) {
    ImmutableSchemaEnum.Builder schemaEnumBuilder =
        getSchemaEnumBuilder(property, componentType, noData);
    propertyEnums
        .get(property.getName())
        .forEach(
            (key, value) ->
                schemaEnumBuilder.addValues(
                    ImmutableSchemaEnumValue.builder().value(value).name(key).build()));
    schemaBuilder.putEnums(property.getName(), schemaEnumBuilder.build());
    propertyBuilder.type(Type.ENUM);
    propertyBuilder.enumType(property.getName());
    propertyBuilder.noData(noData);
  }

  private static Map<String, Map<String, Integer>> getEnums(
      FeatureSchema schema, Map<String, GltfPropertyDefinition> properties) {
    Map<String, Map<String, Integer>> propertyEnums = new HashMap<>();
    for (FeatureSchema property : schema.getProperties()) {
      if (properties.containsKey(property.getName())
          && property.getConstraints().filter(c -> !c.getEnumValues().isEmpty()).isPresent()) {
        List<String> values =
            property
                .getConstraints()
                .map(SchemaConstraints::getEnumValues)
                .orElse(ImmutableList.of());

        if (!values.isEmpty()) {
          ImmutableMap.Builder<String, Integer> enumBuilder = ImmutableMap.builder();
          for (int i = 0; i < values.size(); i++) {
            enumBuilder.put(values.get(i), i);
          }
          propertyEnums.put(property.getName(), enumBuilder.build());
        }
      }
    }
    return propertyEnums;
  }

  private ImmutableSchemaEnum.Builder getSchemaEnumBuilder(
      FeatureSchema property, Optional<ComponentType> componentType, String noData) {
    return ImmutableSchemaEnum.builder()
        .name(property.getLabel())
        .description(property.getDescription())
        .valueType(componentType.orElse(ComponentType.UINT16))
        .addValues(
            ImmutableSchemaEnumValue.builder()
                .value(Integer.parseInt(noData))
                .name("No data")
                .build());
  }

  private static boolean withSurfaceTypes(FeatureSchema featureSchema) {
    return featureSchema.getProperties().stream()
        .filter(property -> "surfaces".equals(property.getName()))
        .findFirst()
        .map(
            surfaces ->
                surfaces.getProperties().stream()
                    .anyMatch(property -> "surfaceType".equals(property.getName())))
        .orElse(false);
  }
}
