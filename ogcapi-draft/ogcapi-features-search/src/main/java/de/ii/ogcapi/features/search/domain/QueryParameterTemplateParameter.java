/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.base.Splitter;
import de.ii.ogcapi.features.search.app.SearchBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameterBase;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TypedQueryParameter;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style() // use defaults
public abstract class QueryParameterTemplateParameter extends OgcApiQueryParameterBase
    implements TypedQueryParameter<Object> {

  ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new GuavaModule())
          .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public abstract String getApiId();

  public abstract String getQueryId();

  public abstract Schema<?> getSchema();

  @Override
  public abstract SchemaValidator getSchemaValidator();

  @Override
  @Value.Default
  public String getId() {
    return getName() + "_" + getQueryId();
  }

  @Override
  public abstract String getName();

  @Override
  public abstract String getDescription();

  @Override
  @Value.Default
  public boolean getExplode() {
    return false;
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/search/{queryId}".equals(definitionPath);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return SearchConfiguration.class;
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData) {
    return Objects.equals(apiData.getId(), getApiId());
  }

  @Override
  public Schema<?> getSchema(OgcApiDataV2 apiData) {
    return getSchema();
  }

  @Override
  public Object parse(
      String value,
      Map<String, Object> typedValues,
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    if (Objects.isNull(value)) {
      // no default value
      return null;
    }

    return getValue(value, getSchema());
  }

  @SuppressWarnings("PMD.NullAssignment")
  private Object getValue(String value, Schema<?> schema) {
    StringBuilder valueAsString = new StringBuilder();
    if (schema instanceof ArraySchema && !value.trim().startsWith("[")) {
      Schema<?> itemsSchema = ((ArraySchema) schema).getItems();
      if (itemsSchema instanceof StringSchema) {
        valueAsString.append(
            String.format(
                "[\"%s\"]",
                Splitter.on(',')
                    .trimResults()
                    .splitToStream(value)
                    .map(s -> s.replace("\"", "\\\""))
                    .collect(Collectors.joining("\",\""))));
      } else {
        valueAsString.append(
            String.format(
                "[%s]",
                Splitter.on(',')
                    .trimResults()
                    .splitToStream(value)
                    .map(s -> s.replace("\"", "\\\""))
                    .collect(Collectors.joining(","))));
      }
    } else if (schema instanceof ObjectSchema && !value.trim().startsWith("{")) {
      Map<String, Schema> properties = schema.getProperties();
      valueAsString.append('{');
      String key = null;
      for (String s : Splitter.on(',').trimResults().split(value)) {
        if (Objects.isNull(key)) {
          key = s;
          valueAsString.append(String.format("\"%s\":", s));
        } else {
          if (properties.containsKey(key) && properties.get(key) instanceof StringSchema) {
            valueAsString.append(String.format("\"%s\"", s));
          } else {
            valueAsString.append(s);
          }
          key = null;
        }
      }
      valueAsString.append('}');

    } else {
      if (schema instanceof StringSchema) {
        valueAsString.append(String.format("\"%s\"", value.replace("\"", "\\\"")));
      } else {
        valueAsString.append(value);
      }
    }
    try {
      return MAPPER.readValue(valueAsString.toString(), Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          String.format(
              "The value '%s' provided for parameter '%s' could not be converted to a value for the parameter.",
              value, getName()),
          e);
    }
  }

  @Override
  @Value.Default
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return SearchBuildingBlock.MATURITY;
  }

  @Override
  @Value.Default
  public Optional<ExternalDocumentation> getSpecificationRef() {
    return SearchBuildingBlock.SPEC;
  }
}
