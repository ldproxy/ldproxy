/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.collections.domain.ImmutableCollectionsConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Feature Collections
 * @langEn Feature Collections.
 * @langDe Feature Collections.
 * @scopeEn The building block *Feature Collections* has to be enabled for every API with a feature
 *     provider. It provides the resources *Feature Collections* and *Feature Collection*.
 *     Currently, feature collections are the only supported type of collection.
 *     <p>::: info Additional links for a specific *Collection* can be defined in the configuration
 *     of the collection. :::
 *     <p>
 * @scopeDe Der Baustein *Feature Collections* ist für jede API mit einem Feature-Provider zu
 *     aktivieren. Es stellt die Ressourcen *Collections* und *Collection* bereit. Derzeit sind
 *     Feature Collections die einzige unterstützte Art von Collections.
 *     <p>::: info Zusätzliche Links zu einer bestimmten Feature Collection einzelnen können bei der
 *     Konfiguration der Collection angegeben werden. :::
 *     <p>
 * @conformanceEn *Feature Collections* implements all requirements of conformance class *Core* of
 *     [OGC API - Features - Part 1: Core
 *     1.0.1](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_core) for the two mentioned
 *     resources.
 * @conformanceDe "Feature Collections" implementiert alle Vorgaben der Konformitätsklasse "Core"
 *     von [OGC API - Features - Part 1: Core
 *     1.0.1](https://docs.ogc.org/is/17-069r4/17-069r4.html#rc_core) für die zwei genannten
 *     Ressourcen.
 * @ref:cfg {@link de.ii.ogcapi.collections.domain.CollectionsConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.collections.domain.ImmutableCollectionsConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.collections.infra.EndpointCollections}, {@link
 *     de.ii.ogcapi.collections.infra.EndpointCollection}
 * @ref:pathParameters {@link de.ii.ogcapi.collections.app.PathParameterCollectionIdCollections}
 * @ref:queryParameters {@link de.ii.ogcapi.collections.domain.QueryParameterFCollections}, {@link
 *     de.ii.ogcapi.collections.domain.QueryParameterFCollection}
 */
@Singleton
@AutoBind
public class CollectionsBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/17-069r4/17-069r4.html",
              "OGC API - Features - Part 1: Core"));

  @Inject
  public CollectionsBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableCollectionsConfiguration.Builder().enabled(true).build();
  }
}
