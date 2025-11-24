/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.DEFAULT_PAGE_SIZE;
import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.MAX_PAGE_SIZE;
import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.MINIMUM_PAGE_SIZE;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.WithChangeListeners;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.CollectionExtent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.foundation.domain.TemporalExtent;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.crs.domain.BoundingBox;
import de.ii.xtraplatform.crs.domain.CrsTransformationException;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.OgcCrs;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.DatasetChangeListener;
import de.ii.xtraplatform.features.domain.FeatureChangeListener;
import de.ii.xtraplatform.features.domain.FeatureExtents;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQueries;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaArray;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaBoolean;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaOneOf;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaString;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title Features
 * @langEn The core capabilities to publish feature data (vector data).
 * @langDe Die Kernfunktionen zur Bereitstellung von Features (Vektordaten).
 * @scopeEn *Features* specifies the most important capabilities for sharing feature data.
 *     <p>The scope is restricted to fetching features where geometries are represented in the
 *     coordinate reference system WGS 84 with axis order longitude/latitude. Features can be
 *     selected using basic filtering criteria.Additional capabilities that address more advanced
 *     needs are provided in additional building blocks.
 *     <p>The formats (encodings) that the API supports are enabled through additional building
 *     blocks, too. By default, GeoJSON and HTML are enabled.
 *     <p>Response paging is supported. That is, if more features are available that the page size,
 *     a link to the next page with the next features is returned.
 *     <p>For spatial filtering a rectangular area (`bbox`) can be specified. Only features that
 *     have a primary geometry that intersects the bounding box are selected. The bounding box is
 *     provided as four numbers:
 *     <p><code>
 * - Lower left corner, coordinate axis 1
 * - Lower left corner, coordinate axis 2
 * - Upper right corner, coordinate axis 1
 * - Upper right corner, coordinate axis 2
 *     </code>
 *     <p>If the primary geometry is never `NULL`, the constraint `required: true` should be set for
 *     the property in the provider schema. This speeds up queries with the `bbox` parameter,
 *     especially with larger data sets.
 *     <p>The coordinate reference system of the values is WGS 84 longitude/latitude unless a
 *     different coordinate reference system is specified in the parameter `bbox-crs` (see building
 *     block [CRS](crs.md)).
 *     <p>For WGS 84 longitude/latitude the values are in most cases the sequence of minimum
 *     longitude, minimum latitude, maximum longitude and maximum latitude. However, in cases where
 *     the box spans the antimeridian the first value (west-most box edge) is larger than the third
 *     value (east-most box edge).
 *     <p>For temporal filtering an instant or interval (`datetime`) can be specified. The value is
 *     either a local date, a date-time value in UTC or an interval. date and date-time expressions
 *     adhere to RFC 3339. Intervals are two instants, separated by a slash (`/`). To indicate a
 *     half-bounded interval end a double-dot (`..`) can be used.
 *     <p>If the primary instant is never `NULL`, the constraint `required: true` should be set for
 *     the property in the provider schema. This speeds up queries with the `datetime` parameter,
 *     especially with larger data sets.
 *     <p>Additional attributes can be filtered based on their values, if they are configured as
 *     queryables.
 *     <p>All filter predicates must be met to select a feature.
 * @scopeDe *Features* spezifiziert die wichtigsten Fähigkeiten zur Bereitstellung von Features.
 *     <p>Der Umfang beschränkt sich auf das Abrufen von Features, deren Geometrien im
 *     Koordinatenreferenzsystem WGS 84 mit der Achsenreihenfolge Längengrad/Breitengrad dargestellt
 *     werden. Die Selektion von Features kann anhand grundlegender Filterkriterien erfolgen.
 *     Zusätzliche Fähigkeiten, die weitergehende Anforderungen erfüllen, werden in zusätzlichen
 *     Bausteinen bereitgestellt.
 *     <p>Die Formate (Kodierungen), die die API unterstützt, werden durch zusätzliche Bausteine
 *     aktiviert. Standardmäßig sind GeoJSON und HTML kodiert.
 *     <p>Die Antworten werden seitenweise zurückgeliefert. Das heißt, wenn mehr Features als die
 *     Seitengröße verfügbar sind, wird ein Link zur nächsten Seite mit den nächsten Features
 *     zurückgegeben.
 *     <p>Für die räumliche Filterung kann ein rechteckiger Bereich (`bbox`) angegeben werden. Es
 *     werden nur Features ausgewählt, deren primäre Geometrie die Begrenzungsgeometrie schneidet.
 *     Die Begrenzungsgeometrie wird als vier Zahlen angegeben:
 *     <p><code>
 * - Linke untere Ecke, Koordinatenachse 1
 * - Linke untere Ecke, Koordinatenachse 2
 * - Rechte obere Ecke, Koordinatenachse 1
 * - Rechte obere Ecke, Koordinatenachse 2
 *     </code>
 *     <p>Sofern die primäre Geometrie nie `NULL` ist, sollte im Provider Schema für die Eigenschaft
 *     der Constraint `required: true` gesetzt werden. Dies beschleunigt Abfragen mit dem
 *     `bbox`-Parameter, besonders bei größeren Datensätzen.
 *     <p>Das Koordinatenreferenzsystem der Werte ist WGS 84 Längen-/Breitengrad, es sei denn, im
 *     Parameter `bbox-crs` (siehe den [CRS-Baustein](crs.md)) wird ein anderes
 *     Koordinatenreferenzsystem angegeben.
 *     <p>Für Angaben als Längen-/Breitengrad sind die Werte in den meisten Fällen die Folge von
 *     minimaler Länge, minimaler Breite, maximale Länge und maximale Breite. In den Fällen, in
 *     denen die Geometrie über den Antimeridian verläuft, ist der erste Wert (westlichster Rand)
 *     jedoch größer als der dritte Wert (östlichster Rand).
 *     <p>Für die zeitliche Filterung kann ein Zeitpunkt oder ein Intervall (`datetime`) angegeben
 *     werden. Der Wert ist entweder ein lokales Datum, ein Zeitstempel in UTC oder ein Intervall.
 *     Datums- und Zeitstempel-Ausdrücke entsprechen RFC 3339. Intervalle sind zwei Zeitpunkte, die
 *     durch einen Schrägstrich (`/`) getrennt sind. Zur Angabe eines unbegrenzten Intervallendes
 *     kann ein Doppelpunkt (`..`) verwendet werden.
 *     <p>Sofern die primären Zeitangaben (der Zeitpunkt oder die Intervallenden) nie `NULL` sind,
 *     sollte im Provider Schema für die Eigenschaft der Constraint `required: true` gesetzt werden.
 *     Dies beschleunigt Abfragen mit dem `datetime`-Parameter, besonders bei größeren Datensätzen.
 *     <p>Zusätzliche Attribute können auf der Grundlage ihrer Werte gefiltert werden, wenn sie als
 *     abfragbar konfiguriert sind (Queryables).
 *     <p>Alle Filterprädikate müssen erfüllt sein, um ein Feature zu selektieren.
 * @conformanceEn *Features* implements all requirements of conformance class *Core* of [OGC API -
 *     Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_core) for the
 *     two operations resources.
 * @conformanceDe *Features* implementiert alle Vorgaben der Konformitätsklasse "Core" von [OGC API
 *     - Features - Part 1: Core 1.0](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_core) für
 *     die zwei Operationen.
 * @ref:cfg {@link de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.features.core.domain.ImmutableFeaturesCoreConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.features.core.app.EndpointFeatures}, {@link
 *     de.ii.ogcapi.features.core.app.EndpointFeature}
 * @ref:pathParameters {@link de.ii.ogcapi.features.core.domain.PathParameterCollectionIdFeatures},
 *     {@link de.ii.ogcapi.features.core.domain.PathParameterFeatureIdFeatures}
 * @ref:queryParameters {@link de.ii.ogcapi.features.core.app.QueryParameterBbox}, {@link
 *     de.ii.ogcapi.features.core.app.QueryParameterDatetime}, {@link
 *     de.ii.ogcapi.features.core.app.QueryParameterLimitFeatures}, {@link
 *     de.ii.ogcapi.features.core.app.QueryParameterOffsetFeatures}, {@link
 *     de.ii.ogcapi.features.core.app.QueryParameterFFeatures}, {@link
 *     de.ii.ogcapi.features.core.app.QueryParameterProfileFeatures},
 */
@Singleton
@AutoBind
public class FeaturesCoreBuildingBlock
    implements ApiBuildingBlock, WithChangeListeners, ApiExtensionHealth {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/17-069r4/17-069r4.html",
              "OGC API - Features - Part 1: Core"));

  private static final Logger LOGGER = LoggerFactory.getLogger(FeaturesCoreBuildingBlock.class);

  private final FeaturesCoreProviders providers;
  private final CrsTransformerFactory crsTransformerFactory;
  private final ClassSchemaCache classSchemaCache;

  @Inject
  public FeaturesCoreBuildingBlock(
      FeaturesCoreProviders providers,
      CrsTransformerFactory crsTransformerFactory,
      ClassSchemaCache classSchemaCache) {
    this.providers = providers;
    this.crsTransformerFactory = crsTransformerFactory;
    this.classSchemaCache = classSchemaCache;
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableFeaturesCoreConfiguration.Builder()
        .enabled(true)
        .itemType(FeaturesCoreConfiguration.ItemType.feature)
        .defaultCrs(FeaturesCoreConfiguration.DefaultCrs.CRS84)
        .defaultProfiles(Map.of("rel", "rel-as-key", "val", "val-as-code"))
        .minimumPageSize(MINIMUM_PAGE_SIZE)
        .defaultPageSize(DEFAULT_PAGE_SIZE)
        .maximumPageSize(MAX_PAGE_SIZE)
        .validateCoordinatesInQueries(false)
        .build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    OgcApiDataV2 apiData = api.getData();

    apiData
        .getCollections()
        .keySet()
        .forEach(
            collectionId ->
                initMetadata(api, collectionId, Instant.now().truncatedTo(ChronoUnit.SECONDS)));

    providers
        .getFeatureProvider(apiData)
        .ifPresent(provider -> updateChangeListeners(provider.changes(), api));

    // register schemas that cannot be derived automatically
    // TODO Setting a schema here has no effect since onStartup is executed *after* the
    //      API definitions have been compiled.
    Schema<?> stringSchema = classSchemaCache.getSchema(JsonSchemaString.class);
    Schema<?> numberSchema = classSchemaCache.getSchema(JsonSchemaNumber.class);
    Schema<?> integerSchema = classSchemaCache.getSchema(JsonSchemaInteger.class);
    Schema<?> booleanSchema = classSchemaCache.getSchema(JsonSchemaBoolean.class);
    Schema<?> objectSchema = classSchemaCache.getSchema(JsonSchemaObject.class);
    Schema<?> arraySchema = classSchemaCache.getSchema(JsonSchemaArray.class);
    Schema<?> refSchema = classSchemaCache.getSchema(JsonSchemaRef.class);
    Schema<?> refExternalSchema = classSchemaCache.getSchema(JsonSchemaRef.class);
    Schema<?> oneOfSchema = classSchemaCache.getSchema(JsonSchemaOneOf.class);
    classSchemaCache.registerSchema(
        JsonSchema.class,
        new ComposedSchema()
            .addOneOfItem(stringSchema)
            .addOneOfItem(numberSchema)
            .addOneOfItem(integerSchema)
            .addOneOfItem(booleanSchema)
            .addOneOfItem(objectSchema)
            .addOneOfItem(arraySchema)
            .addOneOfItem(refSchema)
            .addOneOfItem(refExternalSchema)
            .addOneOfItem(oneOfSchema),
        ImmutableList.of(
            JsonSchemaString.class,
            JsonSchemaNumber.class,
            JsonSchemaInteger.class,
            JsonSchemaBoolean.class,
            JsonSchemaObject.class,
            JsonSchemaArray.class,
            JsonSchemaRef.class,
            JsonSchemaOneOf.class));

    return ValidationResult.of();
  }

  @Override
  public void onShutdown(OgcApi api) {
    providers
        .getFeatureProvider(api.getData())
        .ifPresent(provider -> removeChangeListeners(provider.changes(), api));

    ApiBuildingBlock.super.onShutdown(api);
  }

  // initialize dynamic collection metadata
  private void initMetadata(OgcApi api, String collectionId, Instant lastModified) {
    OgcApiDataV2 apiData = api.getData();
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);

    final Optional<CollectionExtent> optionalExtent = apiData.getExtent(collectionId);

    optionalExtent
        .flatMap(extent -> extent.isSpatialComputed() ? Optional.empty() : extent.getSpatial())
        .or(() -> computeBbox(apiData, collectionId))
        .ifPresent(bbox -> api.setSpatialExtent(collectionId, bbox));

    optionalExtent
        .flatMap(extent -> extent.isTemporalComputed() ? Optional.empty() : extent.getTemporal())
        .or(() -> computeInterval(apiData, collectionId))
        .ifPresent(interval -> api.setTemporalExtent(collectionId, interval));

    final Optional<FeatureQueries> featureQueries =
        providers.getFeatureProvider(apiData, collectionData, FeatureProvider::queries);
    if (featureQueries.isPresent()) {
      final String featureTypeId =
          collectionData
              .getExtension(FeaturesCoreConfiguration.class)
              .map(cfg -> cfg.getFeatureType().orElse(collectionId))
              .orElse(collectionId);
      final long count = featureQueries.get().getFeatureCount(featureTypeId);
      api.setItemCount(collectionId, count);
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Number of items in collection '{}': {}", collectionId, count);
      }

      api.updateLastModified(collectionId, lastModified);
    }
  }

  @Override
  public DatasetChangeListener onDatasetChange(OgcApi api) {
    return change -> {
      for (String featureType : change.getFeatureTypes()) {
        String collectionId = FeaturesCoreConfiguration.getCollectionId(api.getData(), featureType);

        initMetadata(api, collectionId, change.getModified());
      }
    };
  }

  @Override
  public FeatureChangeListener onFeatureChange(OgcApi api) {
    return change -> {
      String collectionId =
          FeaturesCoreConfiguration.getCollectionId(api.getData(), change.getFeatureType());
      switch (change.getAction()) {
        case CREATE:
          api.updateItemCount(collectionId, (long) change.getFeatureIds().size());
        case UPDATE:
          change
              .getNewBoundingBox()
              .flatMap(this::transformToCrs84)
              .ifPresent(bbox -> api.updateSpatialExtent(collectionId, bbox));
          change
              .getNewInterval()
              .ifPresent(
                  interval -> api.updateTemporalExtent(collectionId, TemporalExtent.of(interval)));
          break;
        case DELETE:
          api.updateItemCount(collectionId, (long) -change.getFeatureIds().size());
          break;
      }
      api.updateLastModified(collectionId, change.getModified());
    };
  }

  private Optional<BoundingBox> transformToCrs84(BoundingBox boundingBox) {
    if (!boundingBox.getEpsgCrs().equals(OgcCrs.CRS84)) {
      Optional<CrsTransformer> transformer =
          crsTransformerFactory.getTransformer(boundingBox.getEpsgCrs(), OgcCrs.CRS84);
      if (transformer.isPresent()) {
        try {
          return Optional.ofNullable(transformer.get().transformBoundingBox(boundingBox));
        } catch (CrsTransformationException e) {
          LOGGER.error(
              "Error while transforming the spatial extent of a feature to CRS84: {}",
              e.getMessage());
        }
      }
      return Optional.empty();
    }
    return Optional.ofNullable(boundingBox);
  }

  private Optional<BoundingBox> computeBbox(OgcApiDataV2 apiData, String collectionId) {

    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    Optional<FeatureExtents> featureExtents =
        providers.getFeatureProvider(apiData, collectionData, FeatureProvider::extents);

    if (featureExtents.isPresent()) {
      String featureType =
          collectionData
              .getExtension(FeaturesCoreConfiguration.class)
              .flatMap(FeaturesCoreConfiguration::getFeatureType)
              .orElse(collectionId);
      Optional<BoundingBox> spatialExtent = featureExtents.get().getSpatialExtent(featureType);
      if (spatialExtent.isPresent()) {

        BoundingBox boundingBox = spatialExtent.get();
        if (!boundingBox.getEpsgCrs().equals(OgcCrs.CRS84)
            && !boundingBox.getEpsgCrs().equals(OgcCrs.CRS84h)) {
          Optional<CrsTransformer> transformer =
              boundingBox.is3d()
                  ? crsTransformerFactory.getTransformer(boundingBox.getEpsgCrs(), OgcCrs.CRS84h)
                  : crsTransformerFactory.getTransformer(boundingBox.getEpsgCrs(), OgcCrs.CRS84);
          if (transformer.isPresent()) {
            try {
              boundingBox = transformer.get().transformBoundingBox(boundingBox);
            } catch (CrsTransformationException e) {
              LOGGER.error(
                  "Error while computing spatial extent of collection '{}' while transforming the CRS of the bounding box: {}",
                  collectionId,
                  e.getMessage());
              return Optional.empty();
            }
          }
        }

        return Optional.of(boundingBox);
      }
    }

    return Optional.empty();
  }

  private Optional<TemporalExtent> computeInterval(OgcApiDataV2 apiData, String collectionId) {
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);
    Optional<FeatureExtents> featureExtents =
        providers.getFeatureProvider(apiData, collectionData, FeatureProvider::extents);

    if (featureExtents.isPresent()) {
      String featureType =
          collectionData
              .getExtension(FeaturesCoreConfiguration.class)
              .flatMap(FeaturesCoreConfiguration::getFeatureType)
              .orElse(collectionId);

      return featureExtents.get().getTemporalExtent(featureType).map(TemporalExtent::of);
    }
    return Optional.empty();
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(crsTransformerFactory, providers.getFeatureProviderOrThrow(apiData));
  }
}
