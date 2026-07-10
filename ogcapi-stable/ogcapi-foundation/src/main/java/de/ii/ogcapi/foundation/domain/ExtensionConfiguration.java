/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.google.common.base.CaseFormat;
import de.ii.xtraplatform.base.domain.JacksonProvider;
import de.ii.xtraplatform.entities.domain.Mergeable;
import de.ii.xtraplatform.entities.domain.maptobuilder.Buildable;
import de.ii.xtraplatform.entities.domain.maptobuilder.BuildableBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CUSTOM,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "buildingBlock",
    visible = true)
@JsonTypeIdResolver(JacksonProvider.DynamicTypeIdResolver.class)
@JsonPropertyOrder({"buildingBlock", "enabled"})
public interface ExtensionConfiguration
    extends Buildable<ExtensionConfiguration>, Mergeable<ExtensionConfiguration> {

  abstract class Builder implements BuildableBuilder<ExtensionConfiguration> {

    public abstract Builder defaultValues(ExtensionConfiguration defaultValues);
  }

  static String getBuildingBlockIdentifier(Class<? extends ExtensionConfiguration> clazz) {
    // derived purely from the class name; memoized because it is queried per feature on the
    // extension-enablement hot path (ApiExtension.isExtensionEnabled)
    return BuildingBlockIds.CACHE.computeIfAbsent(
        clazz,
        c ->
            CaseFormat.UPPER_CAMEL.to(
                CaseFormat.UPPER_UNDERSCORE,
                c.getSimpleName().replace("Immutable", "").replace("Configuration", "")));
  }

  final class BuildingBlockIds {
    private BuildingBlockIds() {}

    private static final Map<Class<?>, String> CACHE = new ConcurrentHashMap<>();
  }

  /**
   * @langEn Always `{@buildingBlock}`.
   * @langDe Immer `{@buildingBlock}`.
   */
  @Value.Derived
  default String getBuildingBlock() {
    return getBuildingBlockIdentifier(this.getClass());
  }

  /**
   * @langEn Enable the building block?
   * @langDe Soll der Baustein aktiviert werden?
   * @default false
   */
  @Nullable
  Boolean getEnabled();

  @JsonIgnore
  @Value.Derived
  @Value.Auxiliary
  default boolean isEnabled() {
    return Objects.equals(getEnabled(), true);
  }

  @JsonIgnore
  @Value.Auxiliary
  Optional<ExtensionConfiguration> getDefaultValues();

  @Override
  default ExtensionConfiguration mergeInto(ExtensionConfiguration source) {
    return source.getBuilder().from(source).from(this).build();
  }

  static List<ExtensionConfiguration> replaceOrAddExtensions(
      List<ExtensionConfiguration> extensions, ExtensionConfiguration... add) {
    Map<String, ExtensionConfiguration> extensionsAdd =
        Arrays.stream(add)
            .filter(Objects::nonNull)
            .collect(
                Collectors.toMap(
                    ExtensionConfiguration::getBuildingBlock,
                    Function.identity(),
                    (a, b) -> b,
                    LinkedHashMap::new));

    List<ExtensionConfiguration> extensionsNew =
        extensions.stream()
            .map(
                ext -> {
                  if (extensionsAdd.containsKey(ext.getBuildingBlock())) {
                    return extensionsAdd.remove(ext.getBuildingBlock());
                  }
                  return ext;
                })
            .collect(Collectors.toCollection(ArrayList::new));

    extensionsNew.addAll(extensionsAdd.values());

    return extensionsNew;
  }
}
