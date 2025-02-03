/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.pubsub.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock PUB_SUB
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: PUB_SUB
 *   enabled: true
 *   brokers:
 *     t19:
 *       host: t19.ldproxy.net
 *       port: 8883
 *   publisher: ${PUBLISHER:-t19.ldproxy.net}
 *   publications:
 *     items:
 *       broker: t19
 *       mqttQos: AT_MOST_ONCE
 *     '{wigos_station_identifier}/{observed_property}':
 *       parameters:
 *         wigos_station_identifier: wigos_station_identifier
 *         observed_property: name
 *       property: value
 *       broker: t19
 *       mqttQos: AT_MOST_ONCE
 *       retain: true
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PUB_SUB")
@JsonDeserialize(builder = ImmutablePubSubConfiguration.Builder.class)
public interface PubSubConfiguration extends ExtensionConfiguration {

  /**
   * @langEn A dictionary of MQTT brokers to connect to. The key is an id of the borker. The value
   *     is a broker object with the following properties: `host` (the hostname of the broker),
   *     `port` (the port to use, default is 1883), `ssl` (whether to use SSL, default is `true`, if
   *     the port is 8883, otherwise `false`), `username` and `password` (optional credentials, if
   *     required by the broker).
   * @langDe Ein Verzeichnis der MQTT-Broker, mit denen eine Verbindung hergestellt werden soll. Der
   *     Schlüssel ist eine ID des Brokers. Der Wert ist ein Broker-Objekt mit den folgenden
   *     Eigenschaften: `host` (der Hostname des Brokers), `port` (der zu verwendende Port, Standard
   *     ist 1883), `ssl` (ob SSL verwendet werden soll, Standard ist `true`, wenn der Port 8883
   *     ist, sonst `false`), `username` und `password` (optionale Anmeldedaten, falls vom Broker
   *     benötigt).
   * @default {}
   */
  Map<String, Broker> getBrokers();

  /**
   * @langEn The unique identifier to use when connecting to the broker. Since the value must be
   *     unique in the broker, the value should be set explicitly in the configuration.
   * @langDe Der eindeutige Bezeichner, der im Broker verwendet werden soll. Da der Wert im Broker
   *     eindeutig sein muss, sollte der Wert explizit in der Konfiguration festgelegt werden.
   * @default ldproxy
   */
  @Nullable
  String getPublisher();

  /**
   * @langEn See [Publications](#publications)
   * @langDe Siehe [Veröffentlichungen](#veroffentlichungen)
   * @default {}
   */
  Map<String, Publication> getPublications();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutablePubSubConfiguration.Builder();
  }

  static Set<String> getBrokersInUse(OgcApiDataV2 apiData) {
    return apiData.getCollections().values().stream()
        .map(
            collectionData ->
                collectionData
                    .getExtension(PubSubConfiguration.class)
                    .filter(ExtensionConfiguration::isEnabled)
                    .map(
                        cfg ->
                            cfg.getPublications().values().stream()
                                .map(Publication::getBroker)
                                .collect(Collectors.toUnmodifiableSet())))
        .flatMap(Optional::stream)
        .flatMap(Set::stream)
        .collect(Collectors.toUnmodifiableSet());
  }
}
