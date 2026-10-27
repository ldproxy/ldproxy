/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.reflect.TypeToken;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectSchema extends io.swagger.v3.oas.models.media.ObjectSchema {
  private static final Logger LOGGER = LoggerFactory.getLogger(ObjectSchema.class);

  public ObjectSchema(@NotNull Class<?> clazz, @NotNull ClassSchemaCache classSchemaCache) {
    super();
    Class<?> currentClazz = clazz;
    while (currentClazz != null && currentClazz != Object.class) {
      processMethods(clazz, currentClazz, classSchemaCache);
      currentClazz = currentClazz.getSuperclass();
    }
  }

  private void processMethods(
      Class<?> baseClazz, Class<?> clazz, ClassSchemaCache classSchemaCache) {
    for (Method m : clazz.getDeclaredMethods()) {
      // methods without a value, e.g. Immutables checks, are not properties
      if (m.getParameterCount() == 0
          && Modifier.isPublic(m.getModifiers())
          && m.getReturnType() != void.class
          && !hasIgnoreAnnotation(m)) {
        processMethod(baseClazz, m, classSchemaCache);
      }
    }
  }

  private boolean hasIgnoreAnnotation(Method m) {
    return Arrays.stream(m.getAnnotations())
        .anyMatch(
            a ->
                Objects.equals(
                        a.annotationType(), com.fasterxml.jackson.annotation.JsonIgnore.class)
                    || Objects.equals(
                        a.annotationType(), com.fasterxml.jackson.annotation.JsonAnyGetter.class));
  }

  private void processMethod(Class<?> baseClazz, Method m, ClassSchemaCache classSchemaCache) {
    String name = getPropertyName(m);
    Type type = m.getGenericReturnType();

    // special treatment for some classes
    if (type.equals(OptionalDouble.class)) {
      type = new TypeToken<Optional<Double>>() {}.getType();
    } else if ("java.util.OptionalInt".equals(type.getTypeName())) {
      type = new TypeToken<Optional<Integer>>() {}.getType();
    }

    if (type instanceof ParameterizedType) {
      this.addProperties(
          name,
          withFormat(m, generateSchema((ParameterizedType) type, baseClazz, classSchemaCache)));
      boolean required = false;
      for (Annotation a : m.getAnnotations()) {
        if (Objects.equals(a.annotationType(), JsonProperty.class)) {
          if (m.getAnnotation(JsonProperty.class).required()) {
            required = true;
            break;
          }
        }
      }
      if (required && !isOmittedWhenEmpty(baseClazz, m)) {
        this.addRequiredItem(name);
      }
    } else {
      Class<?> returnType = m.getReturnType();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Type: {}", returnType);
      }
      boolean required = true;
      for (Annotation a : m.getAnnotations()) {
        if (Objects.equals(a.annotationType(), javax.annotation.Nullable.class)) {
          required = false;
          break;
        }
      }
      if (required) {
        this.addRequiredItem(name);
      }
      if (Objects.equals(returnType, baseClazz)) {
        // prevent infinite recursion
        this.addProperties(
            name,
            new Schema<>()
                .$ref(
                    String.format(
                        ClassSchemaCache.SCHEMA_PATH_TEMPLATE,
                        ClassSchemaCache.getSchemaId(returnType))));
      } else {
        this.addProperties(name, withFormat(m, classSchemaCache.getSchema(returnType, baseClazz)));
      }
    }
  }

  // the schemas of basic types are shared, a format is set on a new schema
  private static Schema<?> withFormat(Method m, Schema<?> schema) {
    SchemaFormat format = m.getAnnotation(SchemaFormat.class);
    if (Objects.isNull(format)
        || Objects.isNull(schema)
        || Objects.nonNull(schema.get$ref())
        || !"string".equals(schema.getType())) {
      return schema;
    }
    return new StringSchema().format(format.value());
  }

  // an empty collection or map is not serialized with Include.NON_EMPTY, so the property cannot be
  // required, even if it is declared as required in a supertype
  private static boolean isOmittedWhenEmpty(Class<?> baseClazz, Method m) {
    JsonInclude include = m.getAnnotation(JsonInclude.class);
    if (Objects.isNull(include)) {
      include = findJsonInclude(baseClazz);
    }
    return Objects.nonNull(include) && include.value() == JsonInclude.Include.NON_EMPTY;
  }

  private static JsonInclude findJsonInclude(Class<?> clazz) {
    if (Objects.isNull(clazz) || clazz == Object.class) {
      return null;
    }
    JsonInclude include = clazz.getDeclaredAnnotation(JsonInclude.class);
    if (Objects.nonNull(include)) {
      return include;
    }
    include = findJsonInclude(clazz.getSuperclass());
    if (Objects.nonNull(include)) {
      return include;
    }
    for (Class<?> anInterface : clazz.getInterfaces()) {
      include = findJsonInclude(anInterface);
      if (Objects.nonNull(include)) {
        return include;
      }
    }
    return null;
  }

  private String getPropertyName(Method m) {
    String name = m.getName();

    if (Arrays.stream(m.getAnnotations())
        .anyMatch(a -> Objects.equals(a.annotationType(), JsonProperty.class))) {
      String value = m.getAnnotation(JsonProperty.class).value();
      if (!value.isBlank()) {
        name = value;
      }
    }

    if (name.startsWith("get")) {
      name = name.substring(3);
    } else if (name.startsWith("is")) {
      name = name.substring(2);
    }
    return Character.toLowerCase(name.charAt(0)) + name.substring(1);
  }

  private Schema<?> generateSchema(
      ParameterizedType pType, Class<?> referencingClazz, ClassSchemaCache classSchemaCache) {
    Type rType = pType.getRawType();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace(
          "Raw type: {} - Type args: {}", rType, Arrays.toString(pType.getActualTypeArguments()));
    }
    switch (rType.getTypeName()) {
      case "java.util.List":
      case "java.util.Set":
      case "java.util.Collection":
        return generateArraySchema(
            pType.getActualTypeArguments()[0], referencingClazz, classSchemaCache);
      case "java.util.Optional":
        return generateOptionalSchema(
            pType.getActualTypeArguments()[0], referencingClazz, classSchemaCache);
      case "java.util.Map":
        return generateMapSchema(
            pType.getActualTypeArguments()[0],
            pType.getActualTypeArguments()[1],
            referencingClazz,
            classSchemaCache);
      default:
        // no match
    }

    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Unhandled parameterized type during JSON Schema generation, a generic Object schema is used. Raw type: {} - Type arguments: {}",
          rType,
          Arrays.toString(pType.getActualTypeArguments()));
    }

    return new io.swagger.v3.oas.models.media.ObjectSchema();
  }

  private Schema<?> generateArraySchema(
      Type t, Class<?> referencingClazz, ClassSchemaCache classSchemaCache) {
    if (t instanceof ParameterizedType) {
      return new io.swagger.v3.oas.models.media.ArraySchema()
          .items(generateSchema((ParameterizedType) t, referencingClazz, classSchemaCache));
    } else if (t instanceof Class<?>) {
      if (Objects.equals(t, referencingClazz)) {
        // prevent infinite recursion
        return new io.swagger.v3.oas.models.media.ArraySchema()
            .items(
                new Schema<>()
                    .$ref(
                        String.format(
                            ClassSchemaCache.SCHEMA_PATH_TEMPLATE,
                            ClassSchemaCache.getSchemaId(referencingClazz))));
      }
      return new ArraySchema(referencingClazz, (Class<?>) t, classSchemaCache);
    }

    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Unhandled type during JSON Schema generation, a generic Object schema is used. Type: {}",
          t);
    }

    return new io.swagger.v3.oas.models.media.ArraySchema()
        .items(new io.swagger.v3.oas.models.media.ObjectSchema());
  }

  private Schema<?> generateOptionalSchema(
      Type t, Class<?> referencingClazz, ClassSchemaCache classSchemaCache) {
    if (t instanceof ParameterizedType) {
      return generateSchema((ParameterizedType) t, referencingClazz, classSchemaCache);
    } else if (t instanceof Class<?>) {
      return classSchemaCache.getSchema((Class<?>) t, referencingClazz);
    }

    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Unhandled type during JSON Schema generation, a generic Object schema is used. Type: {}",
          t);
    }

    return new io.swagger.v3.oas.models.media.ObjectSchema();
  }

  private Schema<?> generateMapSchema(
      @SuppressWarnings("unused") Type t1,
      Type t2,
      Class<?> referencingClazz,
      ClassSchemaCache classSchemaCache) {
    if (t2 instanceof Class<?>) {
      return new io.swagger.v3.oas.models.media.ObjectSchema()
          .additionalProperties(classSchemaCache.getSchema((Class<?>) t2, referencingClazz));
    } else if (t2 instanceof ParameterizedType) {
      return new io.swagger.v3.oas.models.media.ObjectSchema()
          .additionalProperties(
              generateSchema((ParameterizedType) t2, referencingClazz, classSchemaCache));
    }

    if (LOGGER.isWarnEnabled()) {
      LOGGER.warn(
          "Unhandled type during JSON Schema generation, a generic Object schema is used. Type: {}",
          t2);
    }

    return new io.swagger.v3.oas.models.media.ObjectSchema();
  }
}
