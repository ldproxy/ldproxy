/*
 * Copyright 2023 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.xtraplatform.base.domain.ImmutableJacksonSubType;
import de.ii.xtraplatform.base.domain.JacksonSubTypeIds;
import java.util.List;
import javax.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.immutables.value.Value;

@Singleton
@AutoBind
public class JacksonSubTypeIdsCatchAll implements JacksonSubTypeIds {

  @Inject
  public JacksonSubTypeIdsCatchAll() {}

  @Override
  public List<JacksonSubType> getSubTypes() {
    return ImmutableList.of(
        ImmutableJacksonSubType.builder()
            .superType(ExtensionConfiguration.class)
            .subType(CatchAllConfiguration.class)
            .id(".+")
            .build());
  }

  @Value.Immutable
  @Value.Style(builder = "new")
  @JsonDeserialize(builder = ImmutableCatchAllConfiguration.Builder.class)
  public interface CatchAllConfiguration extends ExtensionConfiguration {

    @Nullable
    @Override
    Boolean getEnabled();

    @Override
    String getBuildingBlock();

    abstract class Builder extends ExtensionConfiguration.Builder {}

    @Override
    default Builder getBuilder() {
      return new ImmutableCatchAllConfiguration.Builder();
    }
  }
}
