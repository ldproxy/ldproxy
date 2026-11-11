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
import java.util.List;
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
        .build();
  }
}
