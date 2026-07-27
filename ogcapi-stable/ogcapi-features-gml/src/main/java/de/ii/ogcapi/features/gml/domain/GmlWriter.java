/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.features.core.domain.FeatureWriter;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import java.util.List;
import java.util.Objects;

@AutoMultiBind
public interface GmlWriter extends FeatureWriter<EncodingAwareContextGml> {
  GmlWriter create();

  /**
   * Whether an object property is mapped by an {@code xmlPaths} chain. The chain then contributes
   * both the property element and the object element, and its innermost element holds the object's
   * properties. Feature references and the {@code Link} and {@code Measure} object types have an
   * encoding of their own and are never chained, and neither is an object whose element name is
   * derived from a property value — the chain states that name, so there is nothing to derive.
   */
  default boolean isXmlPathObject(EncodingAwareContextGml context, FeatureSchema schema) {
    String objectType = schema.getObjectType().orElse("");
    if (schema.isFeatureRef()
        || "Link".equals(objectType)
        || "Measure".equals(objectType)
        || context.encoding().getVariableObjectElementNames().containsKey(objectType)) {
      return false;
    }
    List<XmlPathElement> chain = context.encoding().getXmlPaths().get(schema.getFullPathAsString());
    return Objects.nonNull(chain) && !chain.isEmpty();
  }
}
