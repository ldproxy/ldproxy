/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock PROCESSES_ARAZZO
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: PROCESSES_ARAZZO
 *   enabled: true
 *   maximumSteps: 50
 *   publishedWorkflows:
 *   - permit-check
 *   allowedExternalUris:
 *   - https://api.example.com/v2/
 *   externalApiHeaders:
 *     https://api.example.com/v2/:
 *       x-api-key: '${example.apikey}'
 *   externalApiServers:
 *     https://api.example.com/v2/rules/openapi.json: https://api.example.com/v2/rules
 *   serverInputs:
 *     apiKey: '${example.apikey}'
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "PROCESSES_ARAZZO")
@JsonDeserialize(builder = ImmutableProcessesArazzoConfiguration.Builder.class)
public interface ProcessesArazzoConfiguration extends ExtensionConfiguration {

  /**
   * @langEn The maximum number of steps that one execution of a workflow runs, including the steps
   *     of nested workflows and the steps that run again after `goto` or `retry`. An execution that
   *     needs more steps fails.
   * @langDe Die maximale Anzahl an Schritten, die eine Ausführung eines Workflows durchführt,
   *     einschließlich der Schritte verschachtelter Workflows und der Schritte, die nach `goto`
   *     oder `retry` erneut ausgeführt werden. Eine Ausführung, die mehr Schritte benötigt, schlägt
   *     fehl.
   * @default 100
   * @since v5.0
   */
  @Nullable
  Integer getMaximumSteps();

  /**
   * @langEn The maximum size in bytes of the response of a step. A step with a larger response
   *     fails, the request of the step needs a smaller result, e.g. with a smaller `limit`.
   * @langDe Die maximale Größe der Antwort eines Schritts in Bytes. Ein Schritt mit einer größeren
   *     Antwort schlägt fehl, die Anfrage des Schritts benötigt ein kleineres Ergebnis, z.B. mit
   *     einem kleineren `limit`.
   * @default 10000000
   * @since v5.0
   */
  @Nullable
  Integer getMaximumResponseSize();

  /**
   * @langEn The URIs of external APIs that the steps of a workflow may call over HTTP. The OpenAPI
   *     document of a source description, the URL of each request of a step and the target of each
   *     redirect are checked. A URI is accepted, if it has the scheme, host and port of an entry
   *     and its path starts with the path of the entry, e.g. `https://api.example.com/v1/` accepts
   *     `https://api.example.com/v1/collections`. The entry `*` accepts every HTTP(S) URI; use it
   *     only for tests or in a closed environment, since a client can then direct requests of the
   *     server to any host the server can reach, including services that are only reachable from
   *     the server. Without an entry, only the APIs of this server can be called. Steps may call
   *     the GET and POST operations of an external API; the credentials of the caller are not sent,
   *     a request has only the headers of the parameters of its step.
   * @langDe Die URIs externer APIs, die die Schritte eines Workflows über HTTP aufrufen dürfen.
   *     Geprüft werden das OpenAPI-Dokument einer Quellbeschreibung, die URL jeder Anfrage eines
   *     Schritts und das Ziel jeder Weiterleitung. Eine URI wird akzeptiert, wenn sie Schema, Host
   *     und Port eines Eintrags hat und ihr Pfad mit dem Pfad des Eintrags beginnt, z.B. akzeptiert
   *     `https://api.example.com/v1/` die URI `https://api.example.com/v1/collections`. Der Eintrag
   *     `*` akzeptiert jede HTTP(S)-URI; er sollte nur für Tests oder in einer abgeschlossenen
   *     Umgebung verwendet werden, da ein Client dann Anfragen des Servers an jeden Host richten
   *     kann, den der Server erreicht, auch an Dienste, die nur vom Server aus erreichbar sind.
   *     Ohne Eintrag können nur die APIs dieses Servers aufgerufen werden. Schritte dürfen die GET-
   *     und POST-Operationen einer externen API aufrufen; die Anmeldedaten des Aufrufers werden
   *     nicht gesendet, eine Anfrage hat nur die Header aus den Parametern ihres Schritts.
   * @default []
   * @since v5.0
   */
  List<String> getAllowedExternalUris();

  /**
   * @langEn Headers that the server adds to the requests to external APIs, by the URI of the API as
   *     in `allowedExternalUris`, e.g. an API key: the requests for the OpenAPI documents of source
   *     descriptions and the requests of steps. A header that a step sets itself is not replaced. A
   *     redirect to another host does not keep these headers. The headers are only sent for stored
   *     workflows, not for Arazzo descriptions in the inputs of a process. Use a variable for a
   *     secret, e.g. `${dso.apikey}`, so that it is not part of the configuration.
   * @langDe Header, die der Server den Anfragen an externe APIs hinzufügt, nach der URI der API wie
   *     in `allowedExternalUris`, z.B. ein API-Schlüssel: den Anfragen nach den OpenAPI-Dokumenten
   *     der Quellbeschreibungen und den Anfragen der Schritte. Ein Header, den ein Schritt selbst
   *     setzt, wird nicht ersetzt. Eine Weiterleitung zu einem anderen Host behält diese Header
   *     nicht. Die Header werden nur für gespeicherte Workflows gesendet, nicht für
   *     Arazzo-Beschreibungen in den Eingaben eines Prozesses. Für ein Geheimnis sollte eine
   *     Variable verwendet werden, z.B. `${dso.apikey}`, damit es nicht Teil der Konfiguration ist.
   * @default {}
   * @since v5.0
   */
  Map<String, Map<String, String>> getExternalApiHeaders();

  /**
   * @langEn The URL of the server of an external API, by the URL of its OpenAPI document. It
   *     replaces the servers in the document, e.g. for a document without servers, where the server
   *     would otherwise be the root of the host of the document.
   * @langDe Die URL des Servers einer externen API, nach der URL ihres OpenAPI-Dokuments. Sie
   *     ersetzt die Server im Dokument, z.B. für ein Dokument ohne Server, bei dem der Server sonst
   *     die Wurzel des Hosts des Dokuments wäre.
   * @default {}
   * @since v5.0
   */
  Map<String, String> getExternalApiServers();

  /**
   * @langEn Inputs of stored workflows whose values the server sets, by the name of the input, e.g.
   *     an API key of the server for an external API. Each workflow that has an input with this
   *     name receives the value, also a workflow that another one calls. The inputs are not part of
   *     the description of a process and a client cannot set them. Use a variable for a secret,
   *     e.g. `${dso.apikey}`.
   * @langDe Eingaben gespeicherter Workflows, deren Werte der Server setzt, nach dem Namen der
   *     Eingabe, z.B. ein API-Schlüssel des Servers für eine externe API. Jeder Workflow mit einer
   *     Eingabe dieses Namens erhält den Wert, auch ein Workflow, den ein anderer aufruft. Die
   *     Eingaben sind nicht Teil der Beschreibung eines Prozesses und ein Client kann sie nicht
   *     setzen. Für ein Geheimnis sollte eine Variable verwendet werden, z.B. `${dso.apikey}`.
   * @default {}
   * @since v5.0
   */
  Map<String, String> getServerInputs();

  /**
   * @langEn The ids of the workflows of the stored Arazzo descriptions that are published as
   *     processes. Without an entry, every workflow is a process. Workflows that are not published
   *     can still be called by other workflows.
   * @langDe Die Ids der Workflows der gespeicherten Arazzo-Beschreibungen, die als Prozesse
   *     veröffentlicht werden. Ohne Eintrag ist jeder Workflow ein Prozess. Workflows, die nicht
   *     veröffentlicht werden, können weiterhin von anderen Workflows aufgerufen werden.
   * @default []
   * @since v5.0
   */
  List<String> getPublishedWorkflows();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableProcessesArazzoConfiguration.Builder();
  }

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ProcessesArazzoConfiguration src = (ProcessesArazzoConfiguration) source;
    return new ImmutableProcessesArazzoConfiguration.Builder()
        .from(source)
        .from(this)
        .allowedExternalUris(
            Stream.concat(src.getAllowedExternalUris().stream(), getAllowedExternalUris().stream())
                .distinct()
                .toList())
        .externalApiHeaders(merge(src.getExternalApiHeaders(), getExternalApiHeaders()))
        .externalApiServers(merge(src.getExternalApiServers(), getExternalApiServers()))
        .serverInputs(merge(src.getServerInputs(), getServerInputs()))
        .publishedWorkflows(
            Stream.concat(src.getPublishedWorkflows().stream(), getPublishedWorkflows().stream())
                .distinct()
                .toList())
        .build();
  }

  private static <T> Map<String, T> merge(Map<String, T> source, Map<String, T> target) {
    Map<String, T> merged = new LinkedHashMap<>(source);
    merged.putAll(target);
    return merged;
  }
}
