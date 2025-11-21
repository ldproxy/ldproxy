/*
 * Copyright 2024 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2.State;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistered;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.function.TriConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoMultiBind
public interface ApiExtensionHealth extends ApiExtension {

  Logger LOGGER = LoggerFactory.getLogger(ApiExtensionHealth.class);
  Map<String, Volatile2> VOLATILES = new ConcurrentHashMap<>();
  Map<String, String> STARTUP_STATE = new ConcurrentHashMap<>();

  default boolean isStarted(OgcApiDataV2 apiData) {
    String key = getClass() + apiData.getId();
    boolean started =
        STARTUP_STATE.containsKey(key)
            && Objects.equals(STARTUP_STATE.get(key), apiData.getStableHash());

    if (!started) {
      getComposedVolatile(apiData);
    }

    return started;
  }

  default void didStart(OgcApiDataV2 apiData) {
    String key = getClass() + apiData.getId();
    STARTUP_STATE.put(key, apiData.getStableHash());
  }

  default void whenAvailable(OgcApi api, Runnable init) {
    OgcApiDataV2 apiData = api.getData();
    getComposedVolatile(apiData)
        .onStateChange(
            (from, to) -> {
              if (to == State.AVAILABLE && !isStarted(apiData)) {
                init.run();
                didStart(apiData);
              }
            },
            true);
  }

  default Volatile2 getComposedVolatile(OgcApiDataV2 apiData) {
    String key = getClass() + apiData.getId();

    return VOLATILES.computeIfAbsent(
        key,
        ignore -> {
          Set<Volatile2> volatiles = getVolatiles(apiData);

          if (volatiles.isEmpty()) {
            return Volatile2.available(getGlobalComponent(this, apiData.getId()));
          }

          ApiHealthVolatile composed =
              new ApiHealthVolatile(getGlobalComponent(this, apiData.getId()), volatiles, Set.of());

          composed.start();

          return composed;
        });
  }

  Set<Volatile2> getVolatiles(OgcApiDataV2 apiData);

  default void register(OgcApi api, TriConsumer<String, Volatile2, String> addSubcomponent) {
    Volatile2 composed = getComposedVolatile(api.getData());

    addSubcomponent.accept(getComponent(this), composed, getCapability(this));
  }

  default void initWhenAvailable(OgcApi api) {
    whenAvailable(
        api,
        LogContext.withMdc(
            () -> {
              ValidationResult result = onStartup(api, MODE.NONE);

              if (!result.getErrors().isEmpty()) {
                Volatile2 composed = getComposedVolatile(api.getData());
                if (composed instanceof ApiHealthVolatile) {
                  ((ApiHealthVolatile) composed).setErrors(result.getErrors());
                  if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn(
                        "Errors during startup of {}, the module will be unavailable: {}",
                        this.getClass().getSimpleName(),
                        String.join("; ", result.getErrors()));
                  }
                }
              }
            }));
  }

  default String getComponentKey() {
    return getComponent(this);
  }

  static String getCapability(ApiExtension extension) {
    return ExtensionConfiguration.getBuildingBlockIdentifier(
            extension.getBuildingBlockConfigurationType())
        .toLowerCase(Locale.ROOT);
  }

  static String getComponent(ApiExtension extension) {
    return extension.getClass().getSimpleName();
  }

  static String getGlobalComponent(ApiExtension extension, String apiId) {
    return String.format("entities/services/%s/%s", apiId, getComponent(extension));
  }

  class ApiHealthVolatile extends AbstractVolatileComposed {

    private final Set<Volatile2> volatiles;
    private final Set<String> errors;

    public ApiHealthVolatile(String uniqueKey, Set<Volatile2> volatiles, Set<String> errors) {
      super(
          uniqueKey,
          volatiles.stream()
              .filter(v -> v instanceof VolatileRegistered)
              .map(v -> ((VolatileRegistered) v).getVolatileRegistry())
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format("No VolatileRegistered found for %s", uniqueKey))),
          false);
      this.volatiles = volatiles;
      this.errors = errors;
    }

    void start() {
      super.onVolatileStart();

      for (Volatile2 volatile2 : volatiles) {
        addSubcomponent(volatile2);
      }

      super.onVolatileStarted();

      if (!errors.isEmpty()) {
        setState(State.UNAVAILABLE);
        setMessage(String.join("|", errors));
      }
    }

    void setErrors(Set<String> errors) {
      if (!errors.isEmpty()) {
        setState(State.UNAVAILABLE);
        setMessage(String.join("|", errors));
      }
    }
  }
}
