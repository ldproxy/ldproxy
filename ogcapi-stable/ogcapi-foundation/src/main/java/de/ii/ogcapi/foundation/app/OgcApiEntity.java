/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.google.common.collect.ImmutableList;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ChangingItemCount;
import de.ii.ogcapi.foundation.domain.ChangingLastModified;
import de.ii.ogcapi.foundation.domain.ChangingSpatialExtent;
import de.ii.ogcapi.foundation.domain.ChangingTemporalExtent;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.TemporalExtent;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.cache.domain.Cache;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsTransformationException;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.ChangingValue;
import de.ii.xtraplatform.entities.domain.Entity;
import de.ii.xtraplatform.entities.domain.Entity.SubType;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.services.domain.AbstractService;
import de.ii.xtraplatform.services.domain.Service;
import de.ii.xtraplatform.services.domain.ServicesContext;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity(
    type = Service.TYPE,
    subTypes = {@SubType(key = Service.SERVICE_TYPE_KEY, value = OgcApiDataV2.SERVICE_TYPE)},
    data = OgcApiDataV2.class)
public class OgcApiEntity extends AbstractService<OgcApiDataV2> implements OgcApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(OgcApiEntity.class);

  private final CrsTransformerFactory crsTransformerFactory;
  private final ExtensionRegistry extensionRegistry;
  private final ServicesContext servicesContext;
  private final boolean asyncStartup;

  private final Cache cache;

  @AssistedInject
  public OgcApiEntity(
      CrsTransformerFactory crsTransformerFactory,
      ExtensionRegistry extensionRegistry,
      ServicesContext servicesContext,
      AppContext appContext,
      VolatileRegistry volatileRegistry,
      Cache cache,
      @Assisted OgcApiDataV2 data) {
    super(data, volatileRegistry);
    this.crsTransformerFactory = crsTransformerFactory;
    this.extensionRegistry = extensionRegistry;
    this.servicesContext = servicesContext;
    this.asyncStartup = appContext.getConfiguration().getModules().isStartupAsync();
    this.cache = cache.withPrefix(getEntityType(), getId());
  }

  @Override
  protected boolean onStartup() throws InterruptedException {
    onVolatileStart();

    // validate the API, the behaviour depends on the validation option for the API:
    // NONE: no validation
    // LAX: invalid the configuration and try to remove invalid options, but try to start the
    // service with the valid options
    // STRICT: no validation during hydration, validation will be done in onStartup() and startup
    // will fail in case of any error
    boolean isSuccess = true;
    OgcApiDataV2 apiData = getData();
    MODE apiValidation = apiData.getApiValidation();

    if (isAsyncStartup() && apiValidation != MODE.NONE) {
      LOGGER.warn("API validation is skipped for startup mode ASYNC.");
      apiValidation = MODE.NONE;
    }

    if (apiValidation != MODE.NONE && LOGGER.isInfoEnabled()) {
      LOGGER.info("Validating service '{}'.", apiData.getId());
    }

    List<ApiExtension> extensions =
        extensionRegistry.getExtensions().stream()
            .sorted(Comparator.comparingInt(ApiExtension::getStartupPriority))
            .collect(Collectors.toList());

    for (ApiExtension extension : extensions) {
      if (extension.isEnabledForApi(apiData)) {
        if (extension instanceof ApiBuildingBlock) {
          addCapability(ApiExtensionHealth.getCapability(extension));
        }
        if (extension instanceof ApiExtensionHealth) {
          ((ApiExtensionHealth) extension).register(this, this::addSubcomponent);
        }

        if (isAsyncStartup()) {
          if (extension instanceof ApiExtensionHealth) {
            ((ApiExtensionHealth) extension).initWhenAvailable(this);
          } else {
            extension.onStartup(this, apiValidation);
          }
        } else {
          ValidationResult result = extension.onStartup(this, apiValidation);
          isSuccess = isSuccess && result.isSuccess();

          result.getErrors().forEach(LOGGER::error);
          result
              .getStrictErrors()
              .forEach(result.getMode() == MODE.STRICT ? LOGGER::error : LOGGER::warn);
          result.getWarnings().forEach(LOGGER::warn);
        }
      }
      // TODO
      checkForStartupCancel();
    }

    if (!isSuccess && LOGGER.isErrorEnabled()) {
      LOGGER.error(
          "Service with id '{}' could not be started. See previous log messages for reasons.",
          apiData.getId());
    }

    return isSuccess;
  }

  @Override
  protected State reconcileStateNoComponents(String capability) {
    return State.AVAILABLE;
  }

  @Override
  public boolean isAvailable(ApiExtension extension, boolean checkBuildingBlock) {
    if (checkBuildingBlock) {
      String capability = ApiExtensionHealth.getCapability(extension);
      return hasCapability(capability) && isAvailable(capability);
    }

    if (extension instanceof ApiExtensionHealth) {
      String componentKey = ((ApiExtensionHealth) extension).getComponentKey();
      return hasComponent(componentKey) && getComponent(componentKey).isAvailable();
    }

    return true;
  }

  @Override
  protected void onShutdown() {
    OgcApiDataV2 apiData = getData();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Shutting down service '{}'.", apiData.getId());
    }

    List<ApiExtension> extensions =
        extensionRegistry.getExtensions().stream()
            .sorted(Comparator.comparingInt(ApiExtension::getStartupPriority).reversed())
            .collect(Collectors.toList());

    for (ApiExtension extension : extensions) {
      if (extension.isEnabledForApi(apiData)) {
        extension.onShutdown(this);
      }
    }

    super.onShutdown();
  }

  @Override
  public <T extends FormatExtension> Optional<T> getOutputFormat(
      Class<T> extensionType, ApiMediaType mediaType, Optional<String> collectionId) {
    List<T> candidates =
        extensionRegistry.getExtensionsForType(extensionType).stream()
            .filter(
                outputFormatExtension ->
                    collectionId
                        .map(s -> outputFormatExtension.isEnabledForApi(getData(), s))
                        .orElseGet(() -> outputFormatExtension.isEnabledForApi(getData())))
            .collect(Collectors.toUnmodifiableList());
    MediaType selected =
        ApiMediaType.negotiateMediaType(
            ImmutableList.of(mediaType.type()),
            candidates.stream()
                .map(f -> f.getMediaType().type())
                .collect(Collectors.toUnmodifiableList()));
    return candidates.stream().filter(f -> f.getMediaType().type().equals(selected)).findFirst();
  }

  @Override
  public <T extends FormatExtension> List<T> getAllOutputFormats(
      Class<T> extensionType, ApiMediaType mediaType, Optional<T> excludeFormat) {
    return extensionRegistry.getExtensionsForType(extensionType).stream()
        .filter(
            outputFormatExtension ->
                !Objects.equals(outputFormatExtension, excludeFormat.orElse(null)))
        .filter(
            outputFormatExtension ->
                mediaType.type().isCompatible(outputFormatExtension.getMediaType().type()))
        .filter(outputFormatExtension -> outputFormatExtension.isEnabledForApi(getData()))
        .collect(Collectors.toList());
  }

  @Override
  public Optional<BoundingBox> getSpatialExtent() {
    return getChangingData().get(ChangingSpatialExtent.class).map(ChangingValue::getValue);
  }

  @Override
  public Optional<BoundingBox> getSpatialExtent(EpsgCrs targetCrs) {
    Optional<BoundingBox> spatialExtent = getSpatialExtent();

    if (spatialExtent.isPresent()) {
      return transformSpatialExtent(spatialExtent.get(), targetCrs);
    }

    return Optional.empty();
  }

  @Override
  public Optional<BoundingBox> getSpatialExtent(String collectionId) {
    return getChangingData()
        .get(ChangingSpatialExtent.class, collectionId)
        .map(ChangingValue::getValue);
  }

  @Override
  public Optional<BoundingBox> getSpatialExtent(String collectionId, EpsgCrs targetCrs) {
    Optional<BoundingBox> spatialExtent = getSpatialExtent(collectionId);

    if (spatialExtent.isPresent()) {
      return transformSpatialExtent(spatialExtent.get(), targetCrs);
    }

    return Optional.empty();
  }

  @Override
  public void setSpatialExtent(BoundingBox bbox) {
    getChangingData().put(ChangingSpatialExtent.class, "UNKNOWN", ChangingSpatialExtent.of(bbox));
  }

  @Override
  public void setSpatialExtent(String collectionId, BoundingBox bbox) {
    getChangingData()
        .put(ChangingSpatialExtent.class, collectionId, ChangingSpatialExtent.of(bbox));
  }

  @Override
  public boolean updateSpatialExtent(String collectionId, BoundingBox bbox) {
    return getChangingData()
        .update(ChangingSpatialExtent.class, collectionId, ChangingSpatialExtent.of(bbox));
  }

  @Override
  public Optional<TemporalExtent> getTemporalExtent() {
    return getChangingData().get(ChangingTemporalExtent.class).map(ChangingValue::getValue);
  }

  @Override
  public Optional<TemporalExtent> getTemporalExtent(String collectionId) {
    return getChangingData()
        .get(ChangingTemporalExtent.class, collectionId)
        .map(ChangingValue::getValue);
  }

  @Override
  public void setTemporalExtent(String collectionId, TemporalExtent temporalExtent) {
    getChangingData()
        .put(ChangingTemporalExtent.class, collectionId, ChangingTemporalExtent.of(temporalExtent));
  }

  @Override
  public boolean updateTemporalExtent(String collectionId, TemporalExtent temporalExtent) {
    return getChangingData()
        .update(
            ChangingTemporalExtent.class, collectionId, ChangingTemporalExtent.of(temporalExtent));
  }

  @Override
  public Optional<Instant> getLastModified() {
    return getChangingData().get(ChangingLastModified.class).map(ChangingValue::getValue);
  }

  @Override
  public Optional<Instant> getLastModified(String collectionId) {
    return getChangingData()
        .get(ChangingLastModified.class, collectionId)
        .map(ChangingValue::getValue);
  }

  @Override
  public boolean updateLastModified(String collectionId, Instant lastModified) {
    return getChangingData()
        .update(ChangingLastModified.class, collectionId, ChangingLastModified.of(lastModified));
  }

  @Override
  public Optional<Long> getItemCount() {
    return getChangingData().get(ChangingItemCount.class).map(ChangingValue::getValue);
  }

  @Override
  public Optional<Long> getItemCount(String collectionId) {
    return getChangingData()
        .get(ChangingItemCount.class, collectionId)
        .map(ChangingValue::getValue);
  }

  @Override
  public void setItemCount(String collectionId, Long itemCount) {
    getChangingData().put(ChangingItemCount.class, collectionId, ChangingItemCount.of(itemCount));
  }

  @Override
  public boolean updateItemCount(String collectionId, Long itemCount) {
    return getChangingData()
        .update(ChangingItemCount.class, collectionId, ChangingItemCount.of(itemCount));
  }

  @Override
  public URI getUri() {
    return servicesContext.getUri().resolve(String.join("/", getData().getSubPath()));
  }

  @Override
  public boolean isAsyncStartup() {
    return asyncStartup;
  }

  @Override
  public Cache getCache() {
    return cache;
  }

  private Optional<BoundingBox> transformSpatialExtent(
      BoundingBox spatialExtent, EpsgCrs targetCrs) {
    if (Objects.nonNull(spatialExtent)) {
      Optional<CrsTransformer> crsTransformer =
          spatialExtent.is3d()
              ? crsTransformerFactory.getTransformer(OgcCrs.CRS84h, targetCrs)
              : crsTransformerFactory.getTransformer(OgcCrs.CRS84, targetCrs);

      if (crsTransformer.isPresent()) {
        try {
          return Optional.ofNullable(crsTransformer.get().transformBoundingBox(spatialExtent));
        } catch (CrsTransformationException e) {
          LOGGER.error(String.format("Error converting bounding box to CRS %s.", targetCrs));
        }
      }
    }

    return Optional.ofNullable(spatialExtent);
  }
}
