/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.domain;

import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.web.domain.MustacheRenderer;
import java.util.Objects;
import org.immutables.value.Value;

/**
 * @author zahnen
 */
@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public interface FeatureTransformationContextHtml extends FeatureTransformationContext {

  ModifiableFeatureCollectionView collectionView();

  MustacheRenderer mustacheRenderer();

  @Value.Derived
  default boolean isSchemaOrgEnabled() {
    return Objects.equals(htmlConfiguration().getSchemaOrgEnabled(), true) && !isQueryExpression();
  }

  @Value.Derived
  default FeaturesHtmlConfiguration featuresHtmlConfiguration() {
    return getConfiguration(FeaturesHtmlConfiguration.class);
  }

  @Value.Derived
  default HtmlConfiguration htmlConfiguration() {
    return getConfiguration(HtmlConfiguration.class);
  }
}
