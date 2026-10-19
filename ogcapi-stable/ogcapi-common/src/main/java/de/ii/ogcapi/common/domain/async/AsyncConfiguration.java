/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.common.domain.async;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import javax.annotation.Nullable;
import org.immutables.value.Value;

public interface AsyncConfiguration {

  /**
   * @langEn Enables asynchronous execution. A request with `Prefer: respond-async` is accepted with
   *     status 202 and executed as a background job; progress and outcome are tracked by the job.
   *     When disabled, requests with `respond-async` are executed synchronously.
   * @langDe Aktiviert die asynchrone Ausführung. Eine Anfrage mit `Prefer: respond-async` wird mit
   *     Status 202 angenommen und als Hintergrund-Job ausgeführt; Fortschritt und Ergebnis werden
   *     im Job festgehalten. Ist die Option inaktiv, werden Anfragen mit `respond-async` synchron
   *     ausgeführt.
   * @default false
   * @since v4.9
   */
  @Nullable
  Boolean getAsync();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isAsync() {
    return Objects.equals(getAsync(), true);
  }

  /**
   * @langEn Only relevant when `async` is enabled. Upper bound in seconds for the `wait`
   *     preference: a request with `Prefer: respond-async, wait=n` is executed as a background job,
   *     but the response is deferred for up to `min(n, maxWait)` seconds — if the job finishes in
   *     time, the regular synchronous response is returned and the stored result is deleted,
   *     otherwise the request is answered with status 202 and the job continues.
   * @langDe Nur relevant, wenn `async` aktiviert ist. Obergrenze in Sekunden für die
   *     `wait`-Präferenz: Eine Anfrage mit `Prefer: respond-async, wait=n` wird als Hintergrund-Job
   *     ausgeführt, die Antwort aber bis zu `min(n, maxWait)` Sekunden zurückgehalten — ist der Job
   *     rechtzeitig fertig, wird die reguläre synchrone Antwort zurückgegeben und das gespeicherte
   *     Ergebnis gelöscht, andernfalls wird die Anfrage mit Status 202 beantwortet und der Job
   *     läuft weiter.
   * @default 60
   * @since v4.9
   */
  @Nullable
  Integer getMaxWait();
}
