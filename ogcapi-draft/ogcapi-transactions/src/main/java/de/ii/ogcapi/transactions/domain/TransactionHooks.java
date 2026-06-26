/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.HeaderPrefer;
import java.util.List;
import java.util.stream.Stream;
import org.immutables.value.Value;

/**
 * Ordered SQL statements run at a point in a write transaction, selected by the request's `Prefer:
 * handling` preference. The effective list for a request is the `always` statements followed by the
 * `strict` or `lenient` statements, depending on the handling.
 */
@Value.Immutable
@JsonDeserialize(builder = ImmutableTransactionHooks.Builder.class)
public interface TransactionHooks {

  /**
   * @langEn Statements run regardless of the request's `Prefer: handling` preference, before any
   *     handling-specific statements.
   * @langDe Anweisungen, die unabhängig von der `Prefer: handling`-Präferenz der Anfrage ausgeführt
   *     werden, vor den handling-spezifischen Anweisungen.
   * @default []
   * @since v4.8
   */
  List<String> getAlways();

  /**
   * @langEn Statements run in addition to `always` when the request is processed with `Prefer:
   *     handling=strict`.
   * @langDe Anweisungen, die zusätzlich zu `always` ausgeführt werden, wenn die Anfrage mit
   *     `Prefer: handling=strict` verarbeitet wird.
   * @default []
   * @since v4.8
   */
  List<String> getStrict();

  /**
   * @langEn Statements run in addition to `always` when the request is processed with `Prefer:
   *     handling=lenient` (the default handling).
   * @langDe Anweisungen, die zusätzlich zu `always` ausgeführt werden, wenn die Anfrage mit
   *     `Prefer: handling=lenient` verarbeitet wird (das Standard-Handling).
   * @default []
   * @since v4.8
   */
  List<String> getLenient();

  /**
   * The statements to run for the given handling: {@link #getAlways()} followed by the handling-
   * specific list. Not a configuration option.
   */
  default List<String> effective(HeaderPrefer.Handling handling) {
    return Stream.concat(
            getAlways().stream(),
            (handling == HeaderPrefer.Handling.STRICT ? getStrict() : getLenient()).stream())
        .collect(ImmutableList.toImmutableList());
  }
}
