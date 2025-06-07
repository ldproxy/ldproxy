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
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileRelAsKey extends ProfileRel {

  private static final String KEY_TEMPLATE =
      String.format(
          "{{%s | orElse:'%s::%s'}}",
          FeatureRefResolver.KEY_TEMPLATE, FeatureRefResolver.SUB_TYPE, FeatureRefResolver.SUB_ID);

  @Inject
  ProfileRelAsKey(ExtensionRegistry extensionRegistry, ProfileSetRel profileSet) {
    super(extensionRegistry, profileSet);
  }

  @Override
  public String getId() {
    return "rel-as-key";
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public void addPropertyTransformations(FeatureSchema schema, String mediaType, Builder builder) {
    schema.getAllNestedProperties().stream()
        .filter(SchemaBase::isFeatureRef)
        .forEach(property -> reduceToKey(property, builder));
  }

  public static void reduceToKey(
      FeatureSchema property, ImmutableProfileTransformations.Builder builder) {
    builder.putTransformations(
        property.getFullPathAsString(),
        ImmutableList.of(
            property
                        .getRefType()
                        .filter(
                            refType ->
                                !Objects.equals(refType, FeatureRefResolver.REF_TYPE_DYNAMIC))
                        .isPresent()
                    && property.getRefKeyTemplate().isEmpty()
                ? new ImmutablePropertyTransformation.Builder()
                    .objectRemoveSelect(FeatureRefResolver.ID)
                    .objectReduceSelect(FeatureRefResolver.ID)
                    .build()
                : new ImmutablePropertyTransformation.Builder()
                    .objectRemoveSelect(FeatureRefResolver.ID)
                    .objectReduceFormat(KEY_TEMPLATE)
                    .build()));
  }
}
