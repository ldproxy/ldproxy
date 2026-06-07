/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.docs.JsonDynamicSubType;
import java.util.LinkedHashSet;
import java.util.List;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * @buildingBlock VERSIONED_FEATURES
 * @examplesAll <code>
 * ```yaml
 * - buildingBlock: VERSIONED_FEATURES
 *   enabled: true
 *   timeAxis: VALIDITY_TIME
 *   mutationTime: CLIENT
 *   retireWithModifications:
 *   - anl
 *   compositeIdPattern: "^(?<id>DE[A-Za-z0-9]{14})(?<start>\\d{8}T\\d{6}Z)$"
 * ```
 * </code>
 */
@Value.Immutable
@Value.Style(builder = "new")
@JsonDynamicSubType(superType = ExtensionConfiguration.class, id = "VERSIONED_FEATURES")
@JsonDeserialize(builder = ImmutableVersionedFeaturesConfiguration.Builder.class)
public interface VersionedFeaturesConfiguration extends ExtensionConfiguration {

  /**
   * Time axis along which version intervals are interpreted.
   *
   * <p>{@code VALIDITY_TIME} models the real-world validity of a feature; {@code TRANSACTION_TIME}
   * models when the version was recorded in the system.
   */
  enum TimeAxis {
    VALIDITY_TIME,
    TRANSACTION_TIME
  }

  /**
   * Source of the timestamp used when a mutation creates or retires a version.
   *
   * <p>{@code SERVER} uses the request timestamp captured by the server; {@code CLIENT} uses a
   * timestamp supplied by the client (in the request body or via an explicit header).
   */
  enum MutationTime {
    SERVER,
    CLIENT
  }

  /**
   * @langEn Time axis along which the version intervals of a collection are interpreted. {@code
   *     VALIDITY_TIME} (the validity of the feature in the real world) or {@code TRANSACTION_TIME}
   *     (when the version was recorded in the system). Must be set when the building block is
   *     enabled for a collection.
   * @langDe Zeitachse, entlang derer die Versionsintervalle einer Sammlung interpretiert werden.
   *     {@code VALIDITY_TIME} (die Gültigkeit des Features in der realen Welt) oder {@code
   *     TRANSACTION_TIME} (Zeitpunkt der Erfassung im System). Muss gesetzt sein, wenn der Baustein
   *     für eine Sammlung aktiviert ist.
   * @default null
   * @since v4.8
   */
  @Nullable
  TimeAxis getTimeAxis();

  /**
   * @langEn Source of the timestamp used when a mutation creates or retires a version. {@code
   *     SERVER} uses the request timestamp captured by the server; {@code CLIENT} uses a timestamp
   *     supplied by the client. Must be set when the collection accepts mutations.
   * @langDe Quelle des Zeitstempels, der beim Erzeugen oder Stilllegen einer Version verwendet
   *     wird. {@code SERVER} verwendet den serverseitig erfassten Anfragezeitstempel; {@code
   *     CLIENT} verwendet einen vom Client gelieferten Zeitstempel. Muss gesetzt sein, wenn die
   *     Sammlung Mutationen akzeptiert.
   * @default null
   * @since v4.8
   */
  @Nullable
  MutationTime getMutationTime();

  /**
   * @langEn Property paths (in dotted schema-id notation, e.g. {@code anl} or {@code lzi.beg}) that
   *     may be modified together with a non-null value for the {@code PRIMARY_INTERVAL_END} role in
   *     a single {@code Update} action. Properties not listed cause an {@code Update} that combines
   *     retirement with other modifications to be rejected with {@code 400 Bad Request}.
   * @langDe Eigenschaftspfade (in Punktschreibweise der Schema-IDs, z. B. {@code anl} oder {@code
   *     lzi.beg}), die in einer einzelnen {@code Update}-Aktion gemeinsam mit dem Setzen eines
   *     Werts für die Rolle {@code PRIMARY_INTERVAL_END} geändert werden dürfen. Nicht aufgelistete
   *     Eigenschaften führen dazu, dass ein {@code Update}, das Stilllegung mit weiteren Änderungen
   *     kombiniert, mit {@code 400 Bad Request} abgelehnt wird.
   * @default []
   * @since v4.8
   */
  List<String> getRetireWithModifications();

  /**
   * @langEn Regex pattern that splits a composite feature id (used in {@code <fes:ResourceId
   *     rid="…"/>} filters and {@code gml:id} values) into a canonical id and an expected {@code
   *     PRIMARY_INTERVAL_START} suffix. The pattern must declare two named groups: {@code id} for
   *     the canonical id (stored verbatim in the database) and {@code start} for the packed
   *     timestamp suffix (parsed via {@code compositeIdTimestampFormat}). On {@code Replace},
   *     {@code Update} and {@code Delete}, the parsed suffix is used as an
   *     If-Unmodified-Since-style predicate: the open version's start must equal it, else the
   *     action fails with 412 Precondition Failed. On {@code Insert} the suffix is simply stripped
   *     so the canonical id is what lands in the database (this lets clients reuse the same logical
   *     feature id more than once in one transaction by attaching a unique suffix to each {@code
   *     gml:id} — XML IDs must be unique).
   * @langDe Regulärer Ausdruck, der einen zusammengesetzten Feature-Identifikator (verwendet in
   *     {@code <fes:ResourceId rid="…"/>}-Filtern und {@code gml:id}-Werten) in einen kanonischen
   *     Identifikator und einen erwarteten Suffix für {@code PRIMARY_INTERVAL_START} aufteilt. Das
   *     Muster muss zwei benannte Gruppen enthalten: {@code id} für den kanonischen Identifikator
   *     (unverändert in der Datenbank gespeichert) und {@code start} für das gepackte
   *     Zeitstempel-Suffix (geparst über {@code compositeIdTimestampFormat}). Bei {@code Replace},
   *     {@code Update} und {@code Delete} wird das geparste Suffix als If-Unmodified-Since-artige
   *     Vorbedingung interpretiert: der Start der offenen Version muss damit übereinstimmen, sonst
   *     scheitert die Aktion mit 412 Precondition Failed. Bei {@code Insert} wird das Suffix
   *     entfernt, sodass die kanonische ID gespeichert wird.
   * @default null
   * @since v4.8
   */
  @Nullable
  String getCompositeIdPattern();

  /**
   * @langEn {@code java.time.format.DateTimeFormatter}-style pattern used to parse the suffix
   *     captured by {@code compositeIdPattern}. Defaults to {@code yyyyMMdd'T'HHmmss'Z'} — the
   *     compact ISO-8601 basic-format with explicit {@code T}/{@code Z} markers, e.g. {@code
   *     20240215T121156Z}. Ignored when {@code compositeIdPattern} is unset.
   * @langDe {@code java.time.format.DateTimeFormatter}-Muster zum Parsen des durch {@code
   *     compositeIdPattern} erfassten Suffixes. Standard: {@code yyyyMMdd'T'HHmmss'Z'} — das
   *     kompakte ISO-8601-Basisformat mit expliziten {@code T}-/{@code Z}-Markern, z. B. {@code
   *     20240215T121156Z}. Wird ignoriert, wenn {@code compositeIdPattern} nicht gesetzt ist.
   * @default "yyyyMMdd'T'HHmmss'Z'"
   * @since v4.8
   */
  @Nullable
  String getCompositeIdTimestampFormat();

  abstract class Builder extends ExtensionConfiguration.Builder {}

  @Override
  default Builder getBuilder() {
    return new ImmutableVersionedFeaturesConfiguration.Builder();
  }

  // List-fields concat+distinct on merge so a collection-level config inherits and extends the
  // API-level config rather than overwriting it (matches feedback_config_list_merge_semantics).
  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    ImmutableVersionedFeaturesConfiguration src = (ImmutableVersionedFeaturesConfiguration) source;
    LinkedHashSet<String> retire = new LinkedHashSet<>(src.getRetireWithModifications());
    retire.addAll(getRetireWithModifications());
    return new ImmutableVersionedFeaturesConfiguration.Builder()
        .from(source)
        .from(this)
        .retireWithModifications(ImmutableList.copyOf(retire))
        .build();
  }
}
