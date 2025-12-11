/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.foundation.domain.ArraySchema;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.EnumSchema;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.ObjectSchema;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ClassSchemaCacheImpl implements ClassSchemaCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClassSchemaCacheImpl.class);

  private static final Schema<?> OBJECT_SCHEMA = new io.swagger.v3.oas.models.media.ObjectSchema();
  private static final Schema<?> STRING_SCHEMA = new StringSchema();
  private static final Schema<?> NUMBER_SCHEMA = new NumberSchema();
  private static final Schema<?> INTEGER_SCHEMA = new IntegerSchema();
  private static final Schema<?> BOOLEAN_SCHEMA = new BooleanSchema();
  private static final Schema<?> URI_SCHEMA = new StringSchema().format("uri-reference");
  private static final Schema<?> DATE_SCHEMA = new StringSchema().format("date");
  private static final Schema<?> DATE_TIME_SCHEMA = new StringSchema().format("date-time");
  private static final Schema<?> LOCALE_SCHEMA =
      new StringSchema().pattern("^[a-zA-Z]{1,8}(?:\\-[a-zA-Z0-9]{1,8})?$");
  private static final Schema<?> LINK_SCHEMA =
      new io.swagger.v3.oas.models.media.ObjectSchema()
          .addProperties("href", new StringSchema().format("uri-reference"))
          .addProperties("rel", new StringSchema())
          .addProperties("type", new StringSchema())
          .addProperties("title", new StringSchema())
          .required(List.of("href", "rel"));

  private final ConcurrentMap<Class<?>, Schema<?>> namedSchemaMap;
  private final ConcurrentMap<Class<?>, Schema<?>> embeddedSchemaMap;
  private final ConcurrentMap<Class<?>, List<Class<?>>> dependencyMap;

  @Inject
  public ClassSchemaCacheImpl() {
    // initialize schema map with basic data types
    this.embeddedSchemaMap = new ConcurrentHashMap<>();
    this.embeddedSchemaMap.put(String.class, STRING_SCHEMA);
    this.embeddedSchemaMap.put(EpsgCrs.class, URI_SCHEMA);
    this.embeddedSchemaMap.put(URI.class, URI_SCHEMA);
    this.embeddedSchemaMap.put(Locale.class, LOCALE_SCHEMA);
    this.embeddedSchemaMap.put(LocalDate.class, DATE_SCHEMA);
    this.embeddedSchemaMap.put(Instant.class, DATE_TIME_SCHEMA);
    this.embeddedSchemaMap.put(Integer.class, INTEGER_SCHEMA);
    this.embeddedSchemaMap.put(Long.class, INTEGER_SCHEMA);
    this.embeddedSchemaMap.put(int.class, INTEGER_SCHEMA);
    this.embeddedSchemaMap.put(long.class, INTEGER_SCHEMA);
    this.embeddedSchemaMap.put(byte.class, INTEGER_SCHEMA);
    this.embeddedSchemaMap.put(short.class, INTEGER_SCHEMA);
    this.embeddedSchemaMap.put(BigDecimal.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(BigInteger.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(double.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(Double.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(float.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(Float.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(Number.class, NUMBER_SCHEMA);
    this.embeddedSchemaMap.put(boolean.class, BOOLEAN_SCHEMA);
    this.embeddedSchemaMap.put(Boolean.class, BOOLEAN_SCHEMA);
    this.embeddedSchemaMap.put(Object.class, OBJECT_SCHEMA);
    this.embeddedSchemaMap.put(JsonNode.class, OBJECT_SCHEMA);

    // initialize schema map with basic data types
    this.namedSchemaMap = new ConcurrentHashMap<>();
    this.namedSchemaMap.put(Link.class, LINK_SCHEMA);

    this.dependencyMap = new ConcurrentHashMap<>();
  }

  @Override
  public Schema<?> getSchema(Class<?> clazz, Class<?> referencingClazz) {
    if (embeddedSchemaMap.containsKey(clazz)) {
      return embeddedSchemaMap.get(clazz);
    }

    if (!namedSchemaMap.containsKey(clazz)) {
      namedSchemaMap.put(clazz, deriveSchema(clazz));
    }

    if (Objects.nonNull(referencingClazz)) {
      addDependency(referencingClazz, clazz);
      return new Schema<>()
          .$ref(
              String.format(
                  ClassSchemaCache.SCHEMA_PATH_TEMPLATE, ClassSchemaCache.getSchemaId(clazz)));
    }
    return namedSchemaMap.get(clazz);
  }

  @Override
  public void registerSchema(Class<?> clazz, Schema<?> schema, List<Class<?>> referencedSchemas) {
    namedSchemaMap.put(clazz, schema);
    referencedSchemas.forEach(referencedClazz -> addDependency(clazz, referencedClazz));
  }

  @Override
  public Map<String, Schema<?>> getReferencedSchemas(Class<?> clazz) {
    if (!dependencyMap.containsKey(clazz)) {
      return ImmutableMap.of();
    }
    return getAllReferencedClasses(clazz).stream()
        .filter(c -> !c.equals(clazz))
        .map(
            referencedClazz ->
                new AbstractMap.SimpleEntry<String, Schema<?>>(
                    ClassSchemaCache.getSchemaId(referencedClazz),
                    namedSchemaMap.get(referencedClazz)))
        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Set<Class<?>> getAllReferencedClasses(Class<?> clazz) {
    if (dependencyMap.containsKey(clazz)) {
      return Stream.concat(
              Stream.of(clazz),
              dependencyMap.get(clazz).stream()
                  .map(this::getAllReferencedClasses)
                  .flatMap(Collection::stream))
          .collect(Collectors.toUnmodifiableSet());
    }

    return ImmutableSet.of(clazz);
  }

  private Schema<?> deriveSchema(Class<?> clazz) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Generating schema for class '{}'.", clazz.getName());
    }

    if (clazz.isEnum()) {
      return new EnumSchema((Object[]) clazz.getEnumConstants());
    } else if (clazz.isArray()) {
      return new ArraySchema(clazz, clazz.getComponentType(), this);
    }

    return new ObjectSchema(clazz, this);
  }

  private void addDependency(Class<?> clazz, Class<?> referencedClazz) {
    if (dependencyMap.containsKey(clazz)) {
      dependencyMap.get(clazz).add(referencedClazz);
    } else {
      ArrayList<Class<?>> dependencies = new ArrayList<>();
      dependencies.add(referencedClazz);
      dependencyMap.put(clazz, dependencies);
    }
  }
}
