/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.crud.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.EndpointFeaturesDefinition;
import de.ii.ogcapi.features.core.domain.PolicyAttributeKeys;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ApiSecurity.PolicyAttribute;
import de.ii.ogcapi.foundation.domain.PolicyAttributeResolver;
import de.ii.xtraplatform.base.domain.LogContext;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class PolicyAttributeResolverPayloadProperties implements PolicyAttributeResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PolicyAttributeResolverPayloadProperties.class);

  private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {};
  private final ObjectMapper objectMapper;

  @Inject
  PolicyAttributeResolverPayloadProperties() {
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public Category getCategory() {
    return Category.ACTION;
  }

  @Override
  public boolean canResolve(Map<String, PolicyAttribute> attributes, ApiOperation apiOperation) {
    return attributes.values().stream().anyMatch(attribute -> attribute.getProperty().isPresent())
        && (hasPayload(apiOperation));
  }

  @Override
  public Map<String, Set<Object>> resolve(
      Map<String, PolicyAttribute> attributes,
      ApiOperation apiOperation,
      ApiRequestContext requestContext,
      Optional<byte[]> body) {
    try {
      Map<String, Object> feature = getFeature(body);

      return resolve(attributes, feature);
    } catch (IOException e) {
      LogContext.errorAsWarn(
          LOGGER, e, "Could not resolve properties, payload not a valid JSON object");
    }
    return Map.of();
  }

  private Map<String, Set<Object>> resolve(
      Map<String, PolicyAttribute> attributes, Map<String, Object> feature) {
    Map<String, String> properties = new LinkedHashMap<>();
    Map<String, Set<Object>> resolved = new LinkedHashMap<>();

    attributes.forEach(
        (key, attribute) -> {
          if (attribute.getProperty().isPresent()) {
            String fullKey = PolicyAttributeKeys.getFullKey(key);
            properties.put(attribute.getProperty().get(), fullKey);
            resolved.put(fullKey, new HashSet<>());
          }
        });

    if (properties.containsKey(PolicyAttributeKeys.KEY_ID)
        && feature.containsKey(PolicyAttributeKeys.KEY_ID)) {
      resolved
          .get(properties.get(PolicyAttributeKeys.KEY_ID))
          .add(feature.get(PolicyAttributeKeys.KEY_ID));
    }
    if (properties.containsKey(PolicyAttributeKeys.KEY_GEOMETRY)
        && feature.containsKey(PolicyAttributeKeys.KEY_GEOMETRY)) {
      try {
        resolved
            .get(properties.get(PolicyAttributeKeys.KEY_GEOMETRY))
            .add(objectMapper.writeValueAsString(feature.get(PolicyAttributeKeys.KEY_GEOMETRY)));
      } catch (JsonProcessingException e) {
        LogContext.errorAsWarn(
            LOGGER, e, "Could not resolve payload geometry, not a valid JSON object");
      }
    }
    if (feature.containsKey(PolicyAttributeKeys.KEY_PROPERTIES)
        && feature.get(PolicyAttributeKeys.KEY_PROPERTIES) instanceof Map) {
      resolve(
          (Map<String, Object>) feature.get(PolicyAttributeKeys.KEY_PROPERTIES),
          properties,
          resolved,
          "");
    }

    return resolved;
  }

  private void resolve(
      Map<String, Object> feature,
      Map<String, String> properties,
      Map<String, Set<Object>> resolved,
      String parentKey) {
    for (Entry<String, Object> entry : feature.entrySet()) {
      String key = parentKey + entry.getKey();
      Object prop = entry.getValue();

      if (properties.containsKey(key)) {
        resolved.get(properties.get(key)).add(prop);
      } else if (prop instanceof Map) {
        resolve((Map<String, Object>) prop, properties, resolved, key + ".");
      }
    }
  }

  private Map<String, Object> getFeature(Optional<byte[]> body) throws IOException {

    if (body.isPresent()) {
      return objectMapper.readValue(body.get(), TYPE_REF);
    }

    return Map.of();
  }

  private boolean hasPayload(ApiOperation apiOperation) {
    return Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(),
            EndpointFeaturesDefinition.OP_ID_CREATE_ITEM)
        || Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(),
            EndpointFeaturesDefinition.OP_ID_REPLACE_ITEM)
        || Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(),
            EndpointFeaturesDefinition.OP_ID_UPDATE_ITEM);
  }
}
