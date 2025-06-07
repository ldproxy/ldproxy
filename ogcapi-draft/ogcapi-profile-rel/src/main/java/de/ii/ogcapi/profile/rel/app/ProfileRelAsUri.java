/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.rel.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.profile.rel.domain.ProfileSetRel;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations.Builder;
import de.ii.xtraplatform.features.domain.transform.FeatureRefResolver;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileRelAsUri extends ProfileRel {

  private static final String URI_TEMPLATE =
      String.format(
          "{{%s | orElse:'{{apiUri}}/collections/%s/items/%s'}}",
          FeatureRefResolver.URI_TEMPLATE, FeatureRefResolver.SUB_TYPE, FeatureRefResolver.SUB_ID);

  @Inject
  ProfileRelAsUri(ExtensionRegistry extensionRegistry, ProfileSetRel profileSet) {
    super(extensionRegistry, profileSet);
  }

  @Override
  public String getId() {
    return "rel-as-uri";
  }

  @Override
  public void addPropertyTransformations(FeatureSchema schema, String mediaType, Builder builder) {
    schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isFeatureRef)
        .forEach(property -> reduceToUri(property, builder));
  }

  public static void reduceToUri(
      FeatureSchema property, ImmutableProfileTransformations.Builder builder) {
    builder.putTransformations(
        property.getFullPathAsString(),
        ImmutableList.of(
            new ImmutablePropertyTransformation.Builder()
                .objectRemoveSelect(FeatureRefResolver.ID)
                .objectReduceFormat(URI_TEMPLATE)
                .build()));
  }
}
