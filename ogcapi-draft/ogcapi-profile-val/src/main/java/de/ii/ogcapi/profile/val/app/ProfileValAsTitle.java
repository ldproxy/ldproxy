/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.val.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.profile.val.domain.ProfileSetVal;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaConstraints;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations;
import de.ii.xtraplatform.features.domain.profile.ImmutableProfileTransformations.Builder;
import de.ii.xtraplatform.features.domain.transform.ImmutablePropertyTransformation;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@AutoBind
public class ProfileValAsTitle extends ProfileVal {

  @Inject
  ProfileValAsTitle(ExtensionRegistry extensionRegistry, ProfileSetVal profileSet) {
    super(extensionRegistry, profileSet);
  }

  @Override
  public String getId() {
    return "val-as-title";
  }

  @Override
  public boolean isDefaultForHumanReadable() {
    return true;
  }

  @Override
  public void addPropertyTransformations(FeatureSchema schema, String mediaType, Builder builder) {
    schema.getAllNestedProperties().stream()
        .filter(p -> p.getConstraints().map(c -> c.getCodelist().isPresent()).orElse(false))
        .forEach(property -> mapToTitle(property, builder));
  }

  public static void mapToTitle(
      FeatureSchema property, ImmutableProfileTransformations.Builder builder) {
    property
        .getConstraints()
        .flatMap(SchemaConstraints::getCodelist)
        .ifPresent(
            codelist -> {
              builder.putTransformations(
                  property.getFullPathAsString(),
                  ImmutableList.of(
                      new ImmutablePropertyTransformation.Builder().codelist(codelist).build()));
            });
  }
}
