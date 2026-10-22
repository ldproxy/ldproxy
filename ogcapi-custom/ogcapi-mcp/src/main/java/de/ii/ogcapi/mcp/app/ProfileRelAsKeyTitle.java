/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileGeneric;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations.Builder;
import de.ii.xtraplatform.features.domain.profile.ProfileTransformations;
import de.ii.xtraplatform.features.domain.transform.FeatureRefResolver;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Objects;

/**
 * A reference as an object with the key of the referenced feature, as in the profile "rel-as-key",
 * and its title, but without its URI. The default of the MCP tools, where the URIs of the
 * references were a large part of a result, but a client still needs the key and the title. The
 * profile is not specified by OGC.
 */
@Singleton
@AutoBind
public class ProfileRelAsKeyTitle extends ProfileGeneric {

  public static final String ID = "rel-as-key-title";

  // the profile set of the profiles "rel-as-link", "rel-as-uri" and "rel-as-key"
  public static final String PROFILE_SET = "rel";

  @Inject
  ProfileRelAsKeyTitle(ExtensionRegistry extensionRegistry) {
    super(extensionRegistry);
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getProfileSet() {
    return PROFILE_SET;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return McpConfiguration.class;
  }

  @Override
  public void addPropertyTransformations(FeatureSchema schema, String mediaType, Builder builder) {
    schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isFeatureRef)
        .forEach(property -> addTransformation(property, mediaType, builder));
  }

  private static void addTransformation(FeatureSchema property, String mediaType, Builder builder) {
    // HTML only shows links
    if (MediaType.TEXT_HTML.equals(mediaType)) {
      ProfileTransformations.reduceToLink(property, builder);
      return;
    }

    builder.putTransformations(
        property.getFullPathAsString(),
        ImmutableList.of(
            new ImmutablePropertyTransformation.Builder()
                .objectRemoveSelect(FeatureRefResolver.ID)
                .objectMapFormat(
                    ImmutableMap.of("id", key(property), "title", FeatureRefResolver.SUB_TITLE))
                .build()));
  }

  // the same key as in the profile "rel-as-key"
  static String key(FeatureSchema property) {
    return property
                .getRefType()
                .filter(refType -> !Objects.equals(refType, FeatureRefResolver.REF_TYPE_DYNAMIC))
                .isPresent()
            && property.getRefKeyTemplate().isEmpty()
        ? FeatureRefResolver.SUB_ID
        : ProfileTransformations.KEY_TEMPLATE;
  }
}
