/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import de.ii.xtraplatform.entities.domain.maptobuilder.Buildable;
import de.ii.xtraplatform.entities.domain.maptobuilder.BuildableBuilder;
import de.ii.xtraplatform.features.domain.FeatureTypeConfiguration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableFeatureTypeConfigurationOgcApi.Builder.class)
public interface FeatureTypeConfigurationOgcApi
    extends FeatureTypeConfiguration,
        ExtendableConfiguration,
        Buildable<FeatureTypeConfigurationOgcApi> {

  static FeatureTypeConfigurationOgcApi replaceOrAddExtensions(
      FeatureTypeConfigurationOgcApi collectionOld, ExtensionConfiguration... extensions) {
    List<ExtensionConfiguration> extensionsNew =
        ExtensionConfiguration.replaceOrAddExtensions(collectionOld.getExtensions(), extensions);

    return new ImmutableFeatureTypeConfigurationOgcApi.Builder()
        .from(collectionOld)
        .extensions(extensionsNew)
        .build();
  }

  abstract class Builder implements BuildableBuilder<FeatureTypeConfigurationOgcApi> {

    // jackson should append to instead of replacing extensions
    @JsonIgnore
    public abstract Builder extensions(Iterable<? extends ExtensionConfiguration> elements);

    @JsonProperty("api")
    public abstract Builder addAllExtensions(Iterable<? extends ExtensionConfiguration> elements);
  }

  @Override
  default ImmutableFeatureTypeConfigurationOgcApi.Builder getBuilder() {
    return new ImmutableFeatureTypeConfigurationOgcApi.Builder().from(this);
  }

  /**
   * @langEn Enable the collection?
   * @langDe Die Collection aktivieren?
   * @default true
   */
  @Value.Default
  default boolean getEnabled() {
    return true;
  }

  /**
   * @langEn The *Feature* resource defines a unique URI for every feature, but this URI is only
   *     stable as long as the API URI stays the same. For use cases where external persistent
   *     feature URIs, which redirect to the current API URI, are used, this option allows to use
   *     such URIs as canonical URI of every feature. To enable this option, provide an URI template
   *     where `{{value}}` is replaced with the feature id.
   * @langDe Über die Feature-Ressource hat jedes Feature zwar eine feste URI, die für Links
   *     verwendet werden kann, allerdings ist die URI nur so lange stabil, wie die API stabil
   *     bleibt. Um von Veränderungen in der URI unabhängig zu sein, kann es sinnvoll oder gewünscht
   *     sein, API-unabhängige URIs für die Features zu definieren und von diesen URIs auf die
   *     jeweils gültige API-URI weiterzuleiten. Diese kanonische URI kann auch in ldproxy
   *     konfiguriert und bei den Features kodiert werden. Hierfür ist ein Muster der Feature-URI
   *     anzugeben, wobei `{{value}}` als Ersetzungspunkt für den lokalen Identifikator des Features
   *     in der API angegeben werden kann.
   * @default null
   */
  Optional<String> getPersistentUriTemplate();

  /**
   * @langEn Array of additional link objects, required keys are `href` (the URI), `label` and `rel`
   *     (the relation).
   * @langDe Erlaubt es, zusätzliche Links bei jeder Objektart zu ergänzen. Der Wert ist ein Array
   *     von Link-Objekten. Anzugeben sind jeweils mindestens die URI (`href`), der anzuzeigende
   *     Text (`label`) und die Link-Relation (`rel`).
   * @default []
   */
  List<Link> getAdditionalLinks();

  /**
   * @langEn [Building Blocks](#building-blocks) configuration.
   * @langDe [Bausteine](#bausteine) konfigurieren.
   * @default []
   */
  @JsonProperty("api")
  @Override
  List<ExtensionConfiguration> getExtensions();

  @JsonIgnore
  List<ExtensionConfiguration> getParentExtensions();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  @Override
  default List<ExtensionConfiguration> getMergedExtensions() {
    return getMergedExtensions(
        Lists.newArrayList(Iterables.concat(getParentExtensions(), getExtensions())));
  }

  @Value.Check
  default FeatureTypeConfigurationOgcApi mergeBuildingBlocks() {
    List<ExtensionConfiguration> distinctExtensions = getMergedExtensions(getExtensions());

    // remove duplicates, stable order
    if (!Objects.equals(
        ExtendableConfiguration.discriminators(getExtensions()),
        ExtendableConfiguration.discriminators(distinctExtensions))) {
      return new ImmutableFeatureTypeConfigurationOgcApi.Builder()
          .from(this)
          .extensions(distinctExtensions)
          .build();
    }

    return this;
  }
}
