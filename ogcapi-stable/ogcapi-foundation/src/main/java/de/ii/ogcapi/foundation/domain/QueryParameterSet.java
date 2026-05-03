/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.immutables.value.Value;

@Value.Immutable
public interface QueryParameterSet {

  static QueryParameterSet of() {
    return new ImmutableQueryParameterSet.Builder()
        .definitions(ImmutableList.of())
        .values(new MultivaluedHashMap<>())
        .build();
  }

  static QueryParameterSet of(List<OgcApiQueryParameter> definitions, Map<String, String> values) {
    return new ImmutableQueryParameterSet.Builder()
        .definitions(definitions)
        .values(new MultivaluedHashMap<>(values))
        .build();
  }

  static QueryParameterSet of(
      List<OgcApiQueryParameter> definitions, MultivaluedMap<String, String> values) {
    return new ImmutableQueryParameterSet.Builder().definitions(definitions).values(values).build();
  }

  Set<OgcApiQueryParameter> getDefinitions();

  MultivaluedMap<String, String> getValues();

  Map<String, Object> getTypedValues();

  default <U> Optional<U> getValue(TypedQueryParameter<U> parameter) {
    //noinspection unchecked
    return Optional.ofNullable((U) getTypedValues().get(parameter.getName()));
  }

  default QueryParameterSet evaluate(
      OgcApi api, Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData) {
    MultivaluedMap<String, String> values = new MultivaluedHashMap<>(getValues());
    Map<String, Object> typedValues = new LinkedHashMap<>();

    int numberOfParsingPasses =
        getDefinitions().stream()
            .filter(parameter -> parameter instanceof TypedQueryParameter<?>)
            .map(parameter -> ((TypedQueryParameter<?>) parameter).getPriority())
            .max(Comparator.naturalOrder())
            .orElse(1);
    for (int pass = 1; pass <= numberOfParsingPasses; pass++) {
      for (OgcApiQueryParameter parameter : getDefinitions()) {
        if (parameter instanceof TypedQueryParameter<?>
            && ((TypedQueryParameter<?>) parameter).getPriority() == pass) {
          Object parsedValue =
              ((TypedQueryParameter<?>) parameter)
                  .parse(values.get(parameter.getName()), typedValues, api, optionalCollectionData);
          if (parsedValue != null) {
            typedValues.put(parameter.getName(), parsedValue);
          }
        }
      }
    }

    return new ImmutableQueryParameterSet.Builder()
        .definitions(getDefinitions())
        .values(values)
        .typedValues(typedValues)
        .build();
  }

  default QueryParameterSet merge(
      OgcApi api,
      Optional<FeatureTypeConfigurationOgcApi> optionalCollectionData,
      Map<String, String> additionalValues) {
    MultivaluedMap<String, String> values = new MultivaluedHashMap<>(getValues());
    Map<String, Object> typedValues = new LinkedHashMap<>(getTypedValues());
    additionalValues.forEach(
        (parameterName, value) ->
            getDefinitions().stream()
                .filter(p -> Objects.equals(p.getId(), parameterName))
                .findFirst()
                .ifPresentOrElse(
                    parameter -> {
                      if (parameter instanceof TypedQueryParameter<?>) {
                        Object currentValue = typedValues.get(parameterName);
                        Object obligationValue =
                            ((TypedQueryParameter<?>) parameter)
                                .parse(value, typedValues, api, optionalCollectionData);
                        if (Objects.nonNull(obligationValue)) {
                          Object mergedValue =
                              ((TypedQueryParameter<?>) parameter)
                                  .mergeValues(currentValue, obligationValue);
                          if (Objects.nonNull(mergedValue)) {
                            typedValues.put(parameterName, mergedValue);
                          } else if (Objects.nonNull(currentValue)) {
                            typedValues.remove(parameterName);
                          }
                        }
                      } else {
                        // untyped parameter, just add the value
                        values.add(parameterName, value);
                      }
                    },
                    // unknown parameter, throw an error
                    () -> {
                      throw new IllegalStateException(
                          String.format(
                              "Adding value for an unknown query parameter '%s'.", parameterName));
                    }));

    return new ImmutableQueryParameterSet.Builder()
        .definitions(getDefinitions())
        .values(values)
        .typedValues(typedValues)
        .build();
  }

  default <U> void forEach(Class<U> clazz, Consumer<U> consumer) {
    for (OgcApiQueryParameter parameter : getDefinitions()) {
      if (clazz.isInstance(parameter)) {
        consumer.accept(clazz.cast(parameter));
      }
    }
  }
}
