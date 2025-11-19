/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.pubsub.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.exceptions.ConnectionClosedException;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.Query;
import de.ii.ogcapi.features.core.domain.FeaturesCoreQueriesHandler.QueryInputFeature;
import de.ii.ogcapi.features.core.domain.ImmutableQueryInputFeature;
import de.ii.ogcapi.features.core.domain.WithChangeListeners;
import de.ii.ogcapi.features.geojson.domain.GeoJsonConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtensionHealth;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.HttpMethods;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableStaticRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.OgcApiQueryParameter;
import de.ii.ogcapi.foundation.domain.ParameterExtension;
import de.ii.ogcapi.foundation.domain.QueryParameterSet;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.base.domain.LogContext.MARKER;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import de.ii.xtraplatform.cql.domain.In;
import de.ii.xtraplatform.cql.domain.ScalarLiteral;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import de.ii.xtraplatform.features.domain.DatasetChangeListener;
import de.ii.xtraplatform.features.domain.FeatureChange.Action;
import de.ii.xtraplatform.features.domain.FeatureChangeListener;
import de.ii.xtraplatform.features.domain.FeatureProvider;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @title PubSub
 * @langEn Publish feature changes via a MQTT broker
 * @langDe Veröffentlichen von Objektänderungen über einen MQTT-Broker
 * @scopeEn This building block publishes messages about feature changes via MQTT brokers.
 *     <p>The building block specifies one or more brokers (option: `brokers`), the unique publisher
 *     identifier in the brokers (option: `publisher`), and one or more types of publications
 *     (option: `publications`).
 *     <p>### Publications
 *     <p>The topic identifiers follow the pattern
 *     `ogcapi/{publisherId}/{apiId}/collections/{collectionId}/{subPath}`, where `publisherId` is
 *     the value of the configuration option `publisher`, `apiId` is the identifier of the API and
 *     `collectionId` is the identifier of the collection of the new, changed or deleted feature.
 *     `subPath` depends on the type of publication. Two types of publications are supported.
 *     <p>All publication types support the following configuration options: <code>
 *  - `broker`: the identifier of the broker to send publication messages to;
 *  - `mqttQos`: the MQTT QoS value for the messages, `AT_MOST_ONCE` (default), `AT_LEAST_ONCE`, or `EXACTLY_ONCE`.
 *  - `retain`: flag whether the broker should retain the message (default: `false);
 *  - `timeout`: the timeout in seconds (default: 60).
 *  </code>
 *     <p>#### Publication type: Single topic for all feature changes in a collection
 *     <p>For these publications `subPath` is `items`. The message is a GeoJSON feature with three
 *     additional properties: <code>
 *  - `$id`: a UUID for the publication;
 *  - `$pubtime`: the timestamp when the publication was created;
 *  - `$operation`: One of `create`, `update`, or `delete`.
 *  </code>
 *     <p>In case of `create` or `update`, the feature includes the id, the geometry and the feature
 *     properties. For `delete`, only the id is included.
 *     <p>See the `items` publication in the [example](#examples).
 *     <p>#### Publication type: One or more topics for changes of a feature property
 *     <p>For these publications `subPath` must not be `items`. The subPath can include multiple
 *     path elements and a path element can be a parameter in curly brackets.
 *     <p>The `parameters` configuration option in the publication maps these parameters to feature
 *     properties. The values of the properties in the instance are used to construct the topic.
 *     This allows to publish, for example, measurements by station, if one of the parameters is a
 *     station identifier.
 *     <p>The `property` configuration option identifies the property whose value is sent in the
 *     publication message. This could be, for example, the value of a measurement.
 *     <p>See the `{wigos_station_identifier}/{observed_property}` publication in the
 *     [example](#examples).
 * @scopeDe Dieser Baustein veröffentlicht Nachrichten über Feature-Änderungen über MQTT-Broker.
 *     <p>Der Baustein spezifiziert einen oder mehrere Broker (Option: `brokers`), die eindeutige
 *     Publisher-Kennung in den Brokern (Option: `publisher`) und eine oder mehrere Arten von
 *     Publikationen (Option: `publications`).
 *     <p>### Veröffentlichungen
 *     <p>Die Themenbezeichner folgen dem Muster
 *     `ogcapi/{publisherId}/{apiId}/collections/{collectionId}/{subPath}`, wobei `publisherId` der
 *     Wert der Konfigurationsoption `publisher` ist, `apiId` der Bezeichner der API und
 *     `collectionId` der Bezeichner der Collection des neuen, geänderten oder gelöschten Features
 *     ist. `subPath` hängt von der Art der Veröffentlichung ab. Es werden zwei Typen von
 *     Veröffentlichungen unterstützt.
 *     <p>Alle Veröffentlichungstypen unterstützen die folgenden Konfigurationsoptionen: <code>
 * - `broker`: der Identifikator des Brokers, an den die Publikationsnachrichten gesendet werden;
 * - `mqttQos`: der MQTT QoS-Wert für die Nachrichten, `AT_MOST_ONCE` (Standard), `AT_LEAST_ONCE`, oder `EXACTLY_ONCE`;
 * - `retain`: Schalter, ob der Broker die Nachricht aufbewahren soll (Voreinstellung: `false);
 * - `timeout`: der Timeout in Sekunden (Standardwert: 60).
 * </code>
 *     <p>#### Veröffentlichungstyp: Einzelnes Thema für alle Feature-Änderungen in einer Collection
 *     <p>Für diese Veröffentlichungen ist `items` der Wert von`subPath`. Die Nachricht ist ein
 *     GeoJSON Feature mit drei zusätzlichen Eigenschaften: <code>
 * - `$id`: eine UUID für die Veröffentlichung;
 * - `$pubtime`: der Zeitstempel, wann die Publikation erstellt wurde;
 * - `$operation`: Einer der Werte `create`, `update`, oder `delete`.
 * </code>
 *     <p>Im Falle von `create` oder `update` enthält das Feature die id, die Geometrie und die
 *     Feature-Eigenschaften. Bei `delete` ist nur die ID enthalten.
 *     <p>Siehe die Veröffentlichung `items` im [Beispiel](#beispiele).
 *     <p>#### Veröffentlichungstyp: Ein oder mehrere Themen für Änderungen an einer
 *     Feature-Eigenschaft.
 *     <p>Für diese Veröffentlichungen darf `subPath` nicht `items` sein. Der `subPath` kann mehrere
 *     Pfadelemente enthalten und ein Pfadelement kann ein Parameter in geschweiften Klammern sein.
 *     <p>Die Konfigurationsoption `parameters` in der Veröffentlichung bildet diese Parameter auf
 *     Feature-Eigenschaften ab. Die Werte der Eigenschaften in der Instanz werden verwendet, um das
 *     Thema zu konstruieren. Dies ermöglicht z.B. die Veröffentlichung von Messungen nach
 *     Stationen, wenn einer der Parameter eine Stationskennung ist.
 *     <p>Die Konfigurationsoption `property` identifiziert die Eigenschaft, deren Wert in der
 *     Veröffentlichungsnachricht gesendet wird. Dies kann z.B. der Wert einer Messung sein.
 *     <p>Siehe die Veröffentlichung `{wigos_station_identifier}/{observed_property}` im
 *     [Beispiel](#beispiele).
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 * @limitationsEn This building block is an initial version that was developed during OGC Testbed
 *     19. Additional development and testing is required to ensure the building block supports a
 *     sufficient range of use cases.
 *     <p>Currently only MQTT 3.1.1 is supported.
 * @limitationsDe Bei diesem Baustein handelt es sich um eine erste Version, die im Rahmen von OGC
 *     Testbed 19 entwickelt wurde. Weitere Entwicklungen und Tests sind erforderlich, um
 *     sicherzustellen, dass der Baustein eine ausreichende Anzahl von Anwendungsfällen unterstützt.
 *     <p>Derzeit wird nur MQTT 3.1.1 unterstützt.
 * @conformanceEn OGC is starting to work on [a standard that enables publish/subscribe
 *     functionality for resources supported by OGC API
 *     Standards](https://github.com/opengeospatial/pubsub). The work is in its early stages.
 *     <p>The event-driven API is described using [AsyncAPI 2.6](https://www.asyncapi.com/) and
 *     complements the OpenAPI definition (API requests initiated by clients).
 * @conformanceDe OGC beginnt mit der Arbeit an [einem Standard, der eine
 *     Publish/Subscribe-Funktionalität für Ressourcen ermöglicht, die von OGC API
 *     Standards](https://github.com/opengeospatial/pubsub) unterstützt werden. Die Arbeiten
 *     befinden sich in einem frühen Stadium.
 *     <p>Die ereignisgesteuerte API wird mit [AsyncAPI 2.6](https://www.asyncapi.com/) beschrieben
 *     und ergänzt die OpenAPI-Definition (von Nutzern initiierte API-Anfragen).
 * @ref:cfg {@link de.ii.ogcapi.pubsub.app.PubSubConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.pubsub.app.ImmutablePubSubConfiguration}
 */
@Singleton
@AutoBind
public class PubSubBuildingBlock
    implements ApiBuildingBlock, ApiExtensionHealth, WithChangeListeners {

  private static final Logger LOGGER = LoggerFactory.getLogger(PubSubBuildingBlock.class);

  private final AppContext appContext;
  private final ExtensionRegistry extensionRegistry;
  private final FeaturesCoreProviders providers;
  private final FeaturesCoreQueriesHandler queriesHandler;
  private final Map<String, Map<String, List<PublicationContext>>> pubContextMap;
  private final Map<String, String> publisherMap;
  private List<? extends FormatExtension> formats;
  private final ObjectMapper mapper;

  @Inject
  public PubSubBuildingBlock(
      AppContext appContext,
      ExtensionRegistry extensionRegistry,
      FeaturesCoreProviders providers,
      FeaturesCoreQueriesHandler queriesHandler) {
    this.appContext = appContext;
    this.extensionRegistry = extensionRegistry;
    this.providers = providers;
    this.queriesHandler = queriesHandler;
    this.pubContextMap = new HashMap<>();
    this.publisherMap = new HashMap<>();
    this.mapper = new ObjectMapper();
  }

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutablePubSubConfiguration.Builder().enabled(false).publisher("ldproxy").build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {

    providers
        .getFeatureProvider(api.getData())
        .ifPresent(provider -> updateChangeListeners(provider.changes(), api));

    String publisher =
        api.getData()
            .getExtension(PubSubConfiguration.class)
            .flatMap(cfg -> Optional.ofNullable(cfg.getPublisher()))
            .orElse("ldproxy");
    publisherMap.put(api.getId(), publisher);

    Set<String> brokersInUse = PubSubConfiguration.getBrokersInUse(api.getData());

    Map<String, Pair<Mqtt3AsyncClient, CompletableFuture<Mqtt3ConnAck>>> clientMap =
        api
            .getData()
            .getExtension(PubSubConfiguration.class)
            .map(PubSubConfiguration::getBrokers)
            .orElse(ImmutableMap.of())
            .entrySet()
            .stream()
            .filter(entry -> brokersInUse.contains(entry.getKey()))
            .map(
                entry -> {
                  Mqtt3AsyncClient client = getClient(publisher, entry.getKey(), entry.getValue());
                  return new SimpleImmutableEntry<>(
                      entry.getKey(),
                      Pair.of(client, client.connectWith().cleanSession(true).send()));
                })
            .collect(
                Collectors.toUnmodifiableMap(
                    SimpleImmutableEntry::getKey, SimpleImmutableEntry::getValue));

    ImmutableMap.Builder<String, List<PublicationContext>> collectionBuilder =
        ImmutableMap.builder();
    api.getData()
        .getCollections()
        .forEach(
            (collectionId, collectionData) ->
                collectionBuilder.put(
                    collectionId,
                    api
                        .getData()
                        .getExtension(PubSubConfiguration.class, collectionId)
                        .map(PubSubConfiguration::getPublications)
                        .orElse(ImmutableMap.of())
                        .entrySet()
                        .stream()
                        .map(
                            entry -> {
                              String pubId = entry.getKey();
                              Publication pub = entry.getValue();

                              return ImmutablePublicationContext.builder()
                                  .client(clientMap.get(pub.getBroker()).getLeft())
                                  .connAck(clientMap.get(pub.getBroker()).getRight())
                                  .qos(pub.getMqttQos())
                                  .subPath(pubId)
                                  .parameters(pub.getParameters())
                                  .property(pub.getProperty())
                                  .timeout(pub.getTimeout())
                                  .retain(pub.getRetain())
                                  .build();
                            })
                        .collect(Collectors.toUnmodifiableList())));

    pubContextMap.put(api.getId(), collectionBuilder.build());

    return ValidationResult.of();
  }

  @Override
  public void onShutdown(OgcApi api) {
    providers
        .getFeatureProvider(api.getData())
        .ifPresent(provider -> removeChangeListeners(provider.changes(), api));

    ApiBuildingBlock.super.onShutdown(api);
  }

  private static Mqtt3AsyncClient getClient(String publisher, String brokerId, Broker broker) {
    Mqtt3ClientBuilder clientBuilder =
        MqttClient.builder()
            .useMqttVersion3()
            .identifier(publisher)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener(
                context -> {
                  if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("PubSub: Connected to broker '{}'", brokerId);
                  }
                })
            .addDisconnectedListener(
                context -> {
                  if (LOGGER.isDebugEnabled()) {
                    if (context instanceof ConnectionClosedException) {
                      LOGGER.debug(
                          "PubSub: Disconnected from broker '{}': {}",
                          brokerId,
                          ((ConnectionClosedException) context).getMessage());
                    } else {
                      LOGGER.debug("PubSub: Disconnected from broker '{}'", brokerId);
                    }
                  }
                })
            .serverHost(broker.getHost())
            .serverPort(broker.getPort());
    if (broker.getSsl()) {
      //noinspection ResultOfMethodCallIgnored
      clientBuilder.sslWithDefaultConfig();
    }
    broker
        .getUsername()
        .ifPresent(
            username ->
                clientBuilder
                    .simpleAuth()
                    .username(username)
                    .password(broker.getPassword().orElse("").getBytes(StandardCharsets.UTF_8))
                    .applySimpleAuth());
    return clientBuilder.buildAsync();
  }

  @Override
  public boolean isEnabledForApi(OgcApiDataV2 apiData, String collectionId) {
    return ApiBuildingBlock.super.isEnabledForApi(apiData, collectionId)
        && apiData
            .getExtension(GeoJsonConfiguration.class, collectionId)
            .map(ExtensionConfiguration::isEnabled)
            .orElse(true);
  }

  @Override
  public DatasetChangeListener onDatasetChange(OgcApi api) {
    return change -> {};
  }

  @Override
  public FeatureChangeListener onFeatureChange(OgcApi api) {
    return change -> {
      String collectionId =
          FeaturesCoreConfiguration.getCollectionId(api.getData(), change.getFeatureType());
      if (isEnabledForApi(api.getData(), collectionId)) {
        pubContextMap
            .get(api.getId())
            .get(collectionId)
            .forEach(
                context ->
                    change
                        .getFeatureIds()
                        .forEach(
                            featureId -> {
                              if (change.getAction() != Action.DELETE
                                  || context.getProperty().isEmpty()) {
                                context
                                    .getConnAck()
                                    .thenCompose(
                                        connAck -> {
                                          ObjectNode geojson;
                                          ObjectNode properties;
                                          switch (change.getAction()) {
                                            case CREATE:
                                            case UPDATE:
                                              geojson =
                                                  getCurrentFeature(
                                                      api,
                                                      providers.getFeatureProviderOrThrow(
                                                          api.getData()),
                                                      collectionId,
                                                      featureId);
                                              if (Objects.isNull(geojson)) {
                                                geojson = initFeature(featureId);
                                              } else if (Objects.isNull(
                                                  geojson.get("properties"))) {
                                                geojson.putObject("properties");
                                              }
                                              break;
                                            default:
                                            case DELETE:
                                              geojson = initFeature(featureId);
                                              break;
                                          }
                                          // add PubSub values
                                          properties = (ObjectNode) geojson.get("properties");
                                          properties.put("$id", UUID.randomUUID().toString());
                                          properties.put(
                                              "$pubtime",
                                              Instant.now()
                                                  .truncatedTo(ChronoUnit.MILLIS)
                                                  .toString());
                                          properties.put(
                                              "$operation",
                                              change.getAction().toString().toLowerCase());
                                          try {
                                            String topic =
                                                String.format(
                                                    "ogcapi/%s/%s/collections/%s/%s",
                                                    publisherMap.get(api.getId()),
                                                    api.getId(),
                                                    collectionId,
                                                    replaceParameters(
                                                        context.getSubPath(),
                                                        context.getParameters(),
                                                        properties));

                                            return context
                                                .getClient()
                                                .publishWith()
                                                .topic(topic)
                                                .qos(context.getQos())
                                                .payload(
                                                    mapper.writeValueAsBytes(
                                                        context
                                                            .getProperty()
                                                            .map(properties::get)
                                                            .orElse(geojson)))
                                                .retain(context.getRetain())
                                                .send();
                                          } catch (JsonProcessingException e) {
                                            throw new IllegalStateException(e);
                                          }
                                        })
                                    .orTimeout(context.getTimeout(), TimeUnit.SECONDS)
                                    .thenAccept(
                                        publish -> {
                                          if (LOGGER.isTraceEnabled()) {
                                            LOGGER.trace(
                                                "PubSub action '{}', collection '{}', feature '{}': Message sent.",
                                                change.getAction(),
                                                collectionId,
                                                featureId);
                                          }
                                        })
                                    .whenComplete(
                                        (ignore, e) -> {
                                          if (e != null) {
                                            if (LOGGER.isWarnEnabled()) {
                                              LOGGER.warn(
                                                  "PubSub action '{}', collection '{}', feature '{}': Error during message publication. Reason: {}",
                                                  change.getAction(),
                                                  collectionId,
                                                  featureId,
                                                  e.getMessage());
                                            }
                                            if (LOGGER.isDebugEnabled(MARKER.STACKTRACE)) {
                                              LOGGER.debug("Stacktrace: ", e);
                                            }
                                          }
                                        });
                              }
                            }));
      }
    };
  }

  public static String replaceParameters(
      String input, Map<String, String> parameters, ObjectNode properties) {
    Pattern pattern = Pattern.compile("\\{(\\w+)}");
    Matcher matcher = pattern.matcher(input);
    StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      String property = parameters.get(matcher.group(1));
      if (Objects.nonNull(property)) {
        JsonNode value = properties.get(property);
        if (Objects.nonNull(value)) {
          matcher.appendReplacement(result, value.asText());
        } else {
          matcher.appendReplacement(result, "null");
        }
      } else {
        matcher.appendReplacement(result, "unknown");
        if (LOGGER.isErrorEnabled()) {
          // TODO move to validation on startup
          LOGGER.error(
              "PubSub: Invalid configuration, unknown feature property {}", matcher.group(1));
        }
      }
    }

    matcher.appendTail(result);
    return result.toString();
  }

  private ObjectNode initFeature(String featureId) {
    ObjectNode geojson;
    geojson = mapper.createObjectNode();
    geojson.putObject("properties");
    geojson.putNull("geometry");
    geojson.put("type", "Feature");
    geojson.put("id", featureId);
    return geojson;
  }

  private ObjectNode getCurrentFeature(
      OgcApi api, FeatureProvider provider, String collectionId, String featureId) {
    try {
      if (formats == null) {
        formats = extensionRegistry.getExtensionsForType(FeatureFormatExtension.class);
      }

      URI uri =
          new URICustomizer(api.getUri())
              .ensureLastPathSegments("collections", collectionId, "items", featureId)
              .build();
      List<OgcApiQueryParameter> parameterDefinitions =
          extensionRegistry.getExtensionsForType(OgcApiQueryParameter.class).stream()
              .filter(
                  param ->
                      param.isApplicable(
                          api.getData(),
                          "/collections/{collectionId}/items/{featureId}",
                          collectionId,
                          HttpMethods.GET))
              .sorted(Comparator.comparing(ParameterExtension::getName))
              .collect(ImmutableList.toImmutableList());
      ApiRequestContext requestContextGeoJson =
          new ImmutableStaticRequestContext.Builder()
              .webContext(appContext)
              .api(api)
              .requestUri(uri)
              .mediaType(
                  new ImmutableApiMediaType.Builder()
                      .type(new MediaType("application", "geo+json"))
                      .label("GeoJSON")
                      .parameter("json")
                      .build())
              .alternateMediaTypes(
                  formats.stream()
                      .filter(f -> f.isEnabledForApi(api.getData(), collectionId))
                      .filter(f -> Objects.nonNull(f.getContent()))
                      .map(FormatExtension::getMediaType)
                      .filter(
                          mediaType -> !"geo+json".equalsIgnoreCase(mediaType.type().getSubtype()))
                      .collect(Collectors.toUnmodifiableSet()))
              .queryParameterSet(
                  QueryParameterSet.of(
                          parameterDefinitions, ImmutableMap.of("profile", "rel-as-key"))
                      .evaluate(api, api.getData().getCollectionData(collectionId)))
              .build();

      @SuppressWarnings("OptionalGetWithoutIsPresent")
      FeaturesCoreConfiguration coreConfiguration =
          api.getData().getExtension(FeaturesCoreConfiguration.class, collectionId).get();
      FeatureQuery query =
          ImmutableFeatureQuery.builder()
              .type(coreConfiguration.getFeatureType().orElse(collectionId))
              .filter(In.of(ScalarLiteral.of(featureId)))
              .returnsSingleFeature(true)
              .crs(coreConfiguration.getDefaultEpsgCrs())
              .build();

      QueryInputFeature queryInput =
          new ImmutableQueryInputFeature.Builder()
              .collectionId(collectionId)
              .featureId(featureId)
              .query(query)
              .featureProvider(provider)
              .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
              .build();

      try (Response response =
          queriesHandler.handle(Query.FEATURE, queryInput, requestContextGeoJson)) {

        if (response.getStatus() == 200) {
          if (response.getEntity() instanceof byte[]) {
            return (ObjectNode) mapper.readTree((byte[]) response.getEntity());
          } else {
            if (LOGGER.isWarnEnabled()) {
              LOGGER.warn(
                  "PubSub: Could not retrieve feature in collection '{}' with id '{}'. Reason: feature payload must be of type 'byte[]', found: {}",
                  collectionId,
                  featureId,
                  response.getEntity().getClass().getSimpleName());
            }
          }
        } else {
          if (LOGGER.isWarnEnabled()) {
            LOGGER.warn(
                "PubSub: Could not retrieve feature in collection '{}' with id '{}'. Reason: feature query failed, status: {}",
                collectionId,
                featureId,
                response.getStatus());
          }
        }
      }

    } catch (URISyntaxException | IOException e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "PubSub: Could not retrieve feature in collection '{}' with id '{}'. Reason: feature query failed with an exception: {}",
            collectionId,
            featureId,
            e.getMessage());
      }
      if (LOGGER.isDebugEnabled(MARKER.STACKTRACE)) {
        LOGGER.debug(LogContext.MARKER.STACKTRACE, "Stacktrace: ", e);
      }
    }
    return null;
  }

  @Override
  public Set<Volatile2> getVolatiles(OgcApiDataV2 apiData) {
    return Set.of(queriesHandler, providers.getFeatureProviderOrThrow(apiData));
  }
}
