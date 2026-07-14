/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.codelists.domain.Codelist;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/**
 * The GML encoding options of one collection. A single response may mix features from several
 * collections (the <em>Search</em> building block), each with its own {@link GmlConfiguration};
 * {@link FeatureTransformationContextGml} keeps one bundle per collection and resolves the active
 * one from the type of the feature currently being encoded, so a property is always encoded with
 * the configuration of the collection it belongs to — not the arbitrary first collection of the
 * response.
 *
 * <p>The bundle holds the collection's {@link GmlConfiguration} directly; the context reads most
 * options straight off it. Only the few values that cannot be read from the configuration as-is are
 * pre-computed here at encoder-build time:
 *
 * <ul>
 *   <li>{@link #getXmlAttributes()}, {@link #getCodelistProperties()}, {@link #getValueWrap()},
 *       {@link #getObjectTypeSuffixedProperties()} — their path keys are alias-rewritten (id →
 *       alias) when {@code useAlias} is on, to match the alias-form paths {@code
 *       GmlWriterProperties} sees at runtime; the {@code valueWrap} chain entries are additionally
 *       parsed into {@link ValueWrapElement}s;
 *   <li>{@link #getCodelists()} — the codelist ids are resolved to {@code Codelist} instances via
 *       the codelist store;
 *   <li>{@link #getAppendTemporalSuffixToGmlId()} — the configured flag folded with whether the
 *       request is a datetime-interval request.
 * </ul>
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public interface CollectionEncodingGml {

  @Value.Default
  default GmlConfiguration getConfig() {
    return new ImmutableGmlConfiguration.Builder().enabled(false).build();
  }

  @Value.Default
  default List<String> getXmlAttributes() {
    return ImmutableList.of();
  }

  @Value.Default
  default Map<String, String> getCodelistProperties() {
    return ImmutableMap.of();
  }

  @Value.Default
  default Map<String, List<ValueWrapElement>> getValueWrap() {
    return ImmutableMap.of();
  }

  @Value.Default
  default Map<String, PositionVariants> getPositionVariants() {
    return ImmutableMap.of();
  }

  @Value.Default
  default List<String> getObjectTypeSuffixedProperties() {
    return ImmutableList.of();
  }

  @Value.Default
  default Map<String, Codelist> getCodelists() {
    return ImmutableMap.of();
  }

  @Value.Default
  default boolean getAppendTemporalSuffixToGmlId() {
    return false;
  }
}
