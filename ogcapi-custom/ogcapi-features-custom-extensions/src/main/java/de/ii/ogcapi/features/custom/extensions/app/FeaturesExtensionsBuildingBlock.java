/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.custom.extensions.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.custom.extensions.domain.ImmutableFeaturesExtensionsConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - Custom Extensions
 * @langEn Filter by geometry intersection.
 * @langDe Filterung durch Geometrie-Überschneidung.
 * @scopeEn The building block adds support for the following query parameter:
 *     <p><code>
 * - `intersects`: if the parameter is specified, the features are
 *     additionally selected by the geometry specified as value and only features whose primary
 *     geometry intersects with the specified geometry are returned. The geometry can be either a
 *     WKT geometry or a URL for a GeoJSON object with a geometry. In case of a FeatureCollection
 *     the first geometry is used.
 *     </code>
 * @scopeDe Der Baustein ergänzt die Unterstützung für den folgenden Query-Parameter:
 *     <p><code>
 * - `intersects`: Ist der Parameter angegeben, werden die Features
 *     zusätzlich nach der als Wert angegeben Geometrie selektiert und es werden nur Features
 *     zurückgeliefert, deren primäre Geometrie sich mit der angegebenen Geometrie schneidet. Als
 *     Geometrie kann entweder eine WKT-Geometrie angegeben werden oder eine URL für ein
 *     GeoJSON-Objekt mit einer Geometrie. Im Fall einer FeatureCollection wird die erste Geometrie
 *     verwendet.
 *     </code>
 * @ref:cfg {@link de.ii.ogcapi.features.custom.extensions.domain.FeaturesExtensionsConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.features.custom.extensions.domain.ImmutableFeaturesExtensionsConfiguration}
 * @ref:pathParameters {@link de.ii.ogcapi.features.core.domain.PathParameterCollectionIdFeatures}
 * @ref:queryParameters {@link de.ii.ogcapi.features.custom.extensions.app.QueryParameterIntersects}
 */
@Singleton
@AutoBind
public class FeaturesExtensionsBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public FeaturesExtensionsBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).intersectsParameter(false).build();
  }
}
