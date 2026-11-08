/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectSchema extends io.swagger.v3.oas.models.media.ObjectSchema {
  private static final Logger LOGGER = LoggerFactory.getLogger(ObjectSchema.class);

  public ObjectSchema(@NotNull Class<?> clazz, @NotNull ClassSchemaCache classSchemaCache) {
    super();
    // the class and its superclasses first, then the interfaces, e.g. the interface of a
    // specification schema that the class implements; the most specific declaration of a method
    // determines the property, but like Jackson the annotations of the methods it overrides apply,
    // too, unless the overriding method has the same annotation
    List<Class<?>> types = getTypeHierarchy(clazz);
    Set<String> seen = new HashSet<>();
    for (Class<?> type : types) {
      for (Method m : type.getDeclaredMethods()) {
        if (isAccessorCandidate(m) && seen.add(m.getName())) {
          Accessor accessor = new Accessor(m, getDeclarations(types, m.getName()));
          if (isProperty(accessor, type.isInterface())) {
            processMethod(clazz, accessor, classSchemaCache);
          }
        }
      }
    }
  }

  private static List<Class<?>> getTypeHierarchy(Class<?> clazz) {
    List<Class<?>> types = new ArrayList<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      types.add(c);
    }
    types.addAll(getAllInterfaces(clazz));
    return types;
  }

  // methods with parameters, static or private methods and the bridges of covariant return types
  // neither are properties nor override a property
  private static boolean isAccessorCandidate(Method m) {
    return m.getParameterCount() == 0
        && !Modifier.isStatic(m.getModifiers())
        && !Modifier.isPrivate(m.getModifiers())
        && !m.isBridge()
        && !m.isSynthetic();
  }

  private static List<Method> getDeclarations(List<Class<?>> types, String name) {
    List<Method> declarations = new ArrayList<>();
    for (Class<?> type : types) {
      try {
        Method m = type.getDeclaredMethod(name);
        if (isAccessorCandidate(m)) {
          declarations.add(m);
        }
      } catch (NoSuchMethodException e) {
        // not declared in this type
      }
    }
    return declarations;
  }

  // methods without a value, e.g. Immutables checks, are not properties; other than in classes,
  // interfaces declare also methods that are not serialized, e.g. a conversion or an Immutables
  // check that returns a normalized instance, so only getters are properties, as for Jackson
  private static boolean isProperty(Accessor accessor, boolean declaredInInterface) {
    Method m = accessor.method();
    return Modifier.isPublic(m.getModifiers())
        && m.getReturnType() != void.class
        && !isIgnored(accessor)
        && (!declaredInInterface || isGetter(accessor));
  }

  private static boolean isGetter(Accessor accessor) {
    String name = accessor.method().getName();
    return Objects.nonNull(accessor.annotation(JsonProperty.class))
        || hasPrefix(name, "get")
        || hasPrefix(name, "is");
  }

  private static boolean hasPrefix(String name, String prefix) {
    return name.startsWith(prefix)
        && name.length() > prefix.length()
        && Character.isUpperCase(name.charAt(prefix.length()));
  }

  private static Set<Class<?>> getAllInterfaces(Class<?> clazz) {
    Set<Class<?>> interfaces = new LinkedHashSet<>();
    Deque<Class<?>> pending = new ArrayDeque<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      pending.addAll(Arrays.asList(c.getInterfaces()));
    }
    while (!pending.isEmpty()) {
      Class<?> anInterface = pending.poll();
      if (anInterface != clazz && interfaces.add(anInterface)) {
        pending.addAll(Arrays.asList(anInterface.getInterfaces()));
      }
    }
    return interfaces;
  }

  private static boolean isIgnored(Accessor accessor) {
    JsonIgnore ignore = accessor.annotation(JsonIgnore.class);
    JsonAnyGetter anyGetter = accessor.annotation(JsonAnyGetter.class);
    return (Objects.nonNull(ignore) && ignore.value())
        || (Objects.nonNull(anyGetter) && anyGetter.enabled());
  }

  private void processMethod(
      Class<?> baseClazz, Accessor accessor, ClassSchemaCache classSchemaCache) {
    Method m = accessor.method();
    String name = getPropertyName(accessor);
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
          withFormat(
              accessor, generateSchema((ParameterizedType) type, baseClazz, classSchemaCache)));
      JsonProperty property = accessor.annotation(JsonProperty.class);
      boolean required = Objects.nonNull(property) && property.required();
      if (required && !isOmittedWhenEmpty(baseClazz, accessor)) {
        this.addRequiredItem(name);
      }
    } else {
      Class<?> returnType = m.getReturnType();
      if (LOGGER.isTraceEnabled()) {
        LOGGER.trace("Type: {}", returnType);
      }
      if (Objects.isNull(accessor.annotation(javax.annotation.Nullable.class))) {
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
        this.addProperties(
            name, withFormat(accessor, classSchemaCache.getSchema(returnType, baseClazz)));
      }
    }
  }

  // the schemas of basic types are shared, a format is set on a new schema
  private static Schema<?> withFormat(Accessor accessor, Schema<?> schema) {
    SchemaFormat format = accessor.annotation(SchemaFormat.class);
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
  private static boolean isOmittedWhenEmpty(Class<?> baseClazz, Accessor accessor) {
    JsonInclude include = accessor.annotation(JsonInclude.class);
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

  // an explicit name is used as is, otherwise the prefix of a getter is removed
  private static String getPropertyName(Accessor accessor) {
    JsonProperty property = accessor.annotation(JsonProperty.class);
    if (Objects.nonNull(property) && !property.value().isBlank()) {
      return property.value();
    }

    String name = accessor.method().getName();
    if (hasPrefix(name, "get")) {
      name = name.substring(3);
    } else if (hasPrefix(name, "is")) {
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

  // a method and all declarations of it in the type hierarchy, the most specific first
  private record Accessor(Method method, List<Method> declarations) {
    <A extends Annotation> A annotation(Class<A> type) {
      for (Method declaration : declarations) {
        A annotation = declaration.getAnnotation(type);
        if (Objects.nonNull(annotation)) {
          return annotation;
        }
      }
      return null;
    }
  }
}
