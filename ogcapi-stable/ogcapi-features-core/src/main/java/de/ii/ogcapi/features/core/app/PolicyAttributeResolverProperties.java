/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.EndpointFeaturesDefinition;
import de.ii.ogcapi.features.core.domain.PolicyAttributeKeys;
import de.ii.ogcapi.foundation.domain.ApiOperation;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ApiSecurity.PolicyAttribute;
import de.ii.ogcapi.foundation.domain.ImmutableRequestContext.Builder;
import de.ii.ogcapi.foundation.domain.PolicyAttributeResolver;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

@Singleton
@AutoBind
public class PolicyAttributeResolverProperties implements PolicyAttributeResolver {

  private static final TypeReference<List<Map<String, Object>>> TYPE_REF = new TypeReference<>() {};

  private final PolicyAttributeFeatureGetter featureGetter;
  private final PolicyAttributeFeaturesGetter featuresGetter;
  private final ObjectMapper objectMapper;

  @Inject
  PolicyAttributeResolverProperties(
      PolicyAttributeFeatureGetter featureGetter, PolicyAttributeFeaturesGetter featuresGetter) {
    this.featureGetter = featureGetter;
    this.featuresGetter = featuresGetter;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public Category getCategory() {
    return Category.RESOURCE;
  }

  @Override
  public boolean canResolve(Map<String, PolicyAttribute> attributes, ApiOperation apiOperation) {
    return attributes.values().stream().anyMatch(attribute -> attribute.getProperty().isPresent())
        && (isItem(apiOperation) /*|| isItems(apiOperation)*/);
  }

  @Override
  public Map<String, Set<Object>> resolve(
      Map<String, PolicyAttribute> attributes,
      ApiOperation apiOperation,
      ApiRequestContext requestContext,
      Optional<byte[]> body) {
    try {
      List<Map<String, Object>> features = getFeatures(apiOperation, requestContext);

      return resolve(attributes, features);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Set<Object>> resolve(
      Map<String, PolicyAttribute> attributes, List<Map<String, Object>> features) {
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

    features.forEach(
        feature ->
            feature.forEach(
                (key, prop) -> {
                  if (properties.containsKey(key)) {
                    resolved.get(properties.get(key)).add(prop);
                  }
                }));

    return resolved;
  }

  private List<Map<String, Object>> getFeatures(
      ApiOperation apiOperation, ApiRequestContext requestContext) throws IOException {
    Optional<Response> response = getResponse(apiOperation, requestContext);

    if (response.isPresent()) {
      return objectMapper.readValue(toBytes(response.get()), TYPE_REF);
    }

    return List.of();
  }

  private Optional<Response> getResponse(
      ApiOperation apiOperation, ApiRequestContext requestContext) {
    ApiRequestContext newRequestContext =
        new Builder()
            .from(requestContext)
            .mediaType(PolicyAttributeFeaturesFormat.MEDIA_TYPE)
            .build();

    if (isItem(apiOperation)) {
      return Optional.of(
          featureGetter.getItem(
              newRequestContext,
              newRequestContext.getCollectionId().get(),
              newRequestContext.getUriCustomizer().getLastPathSegment()));
    }

    /*if (isItems(apiOperation)) {
      return Optional.of(
          featuresGetter.getItems(
              newRequestContext,
              new MultivaluedHashMap<>(newRequestContext.getParameters()),
              newRequestContext.getCollectionId().get()));
    }*/

    return Optional.empty();
  }

  private Map<String, String> encode(Map<String, Set<Object>> resolved)
      throws JsonProcessingException {
    Map<String, String> encoded = new LinkedHashMap<>();

    for (Entry<String, Set<Object>> entry : resolved.entrySet()) {
      encoded.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
    }

    return encoded;
  }

  private byte[] toBytes(Response response) throws IOException {
    if (response.getEntity() instanceof StreamingOutput) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      ((StreamingOutput) response.getEntity()).write(byteArrayOutputStream);

      return byteArrayOutputStream.toByteArray();
    }

    return (byte[]) response.getEntity();
  }

  private boolean isItem(ApiOperation apiOperation) {
    return Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(), EndpointFeaturesDefinition.OP_ID_GET_ITEM)
        || Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(),
            EndpointFeaturesDefinition.OP_ID_REPLACE_ITEM)
        || Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(),
            EndpointFeaturesDefinition.OP_ID_UPDATE_ITEM)
        || Objects.equals(
            apiOperation.getOperationIdWithoutPrefix(),
            EndpointFeaturesDefinition.OP_ID_DELETE_ITEM);
  }

  private boolean isItems(ApiOperation apiOperation) {
    return Objects.equals(
        apiOperation.getOperationIdWithoutPrefix(), EndpointFeaturesDefinition.OP_ID_GET_ITEMS);
  }
}
