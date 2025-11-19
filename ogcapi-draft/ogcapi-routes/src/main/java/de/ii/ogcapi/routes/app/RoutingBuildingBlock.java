/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.routes.app;

import static de.ii.ogcapi.features.core.domain.FeaturesCoreConfiguration.DefaultCrs.CRS84;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.routes.domain.ImmutableHtmlForm;
import de.ii.ogcapi.routes.domain.ImmutableRoutingConfiguration;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Routing
 * @langEn Calculate and manage routes.
 * @langDe Routen berechnen und verwalten.
 * @scopeEn This building block computes routes using a [routing
 *     provider](../../providers/feature/extensions/routing.html); that is, am PostgreSQL feature
 *     provider with the extensions PostGIS and pgRouting.
 *     <p>An option to store and manage routes that have been computed can be enabled, too.
 * @scopeDe Dieser Baustein berechnet Routen mit Hilfe eines
 *     [Routing-Providers](../../providers/feature/extensions/routing.html), d.h. eines
 *     PostgreSQL-SQL-Feature-Providers mit den Erweiterungen PostGIS und pgRouting.
 *     <p>Eine Option zur Speicherung und Verwaltung der berechneten Routen wird ebenfalls
 *     unterstützt.
 * @conformanceEn *Routing* implements the conformance classes "Core", "Manage Routes", "Modes",
 *     "Intermediate Waypoints", "Height Restrictions", "Weight Restrictions", and "Obstacles" of
 *     the [draft OGC API - Routes - Part 1: Core](https://docs.ogc.org/DRAFTS/21-000.html) and the
 *     conformance class "Route Exchange Model" of the [draft OGC Route Exchange
 *     Model](https://docs.ogc.org/DRAFTS/21-001.html).
 * @conformanceDe Der Baustein implementiert die Konformitätsklassen "Core", "Manage Routes",
 *     "Modes", "Intermediate Waypoints", "Height Restrictions", "Weight Restrictions" und
 *     "Obstacles" des [Entwurfs von OGC API - Routes - Part 1:
 *     Core](https://docs.ogc.org/DRAFTS/21-000.html) sowie die Konformitätsklasse "Route Exchange
 *     Model" des [Entwurfs OGC Route Exchange Model](https://docs.ogc.org/DRAFTS/21-001.html).
 * @cfgFilesEn The building block does not require or support any additional configuration files.
 *     <p>If the `manageRoutes` option is enabled, the routes and their definition will be stored in
 *     the value store in the directories `routes/results/{apiId}/` and
 *     `routes/definitions/{apiId}/`.
 * @cfgFilesDe Der Baustein benötigt bzw. unterstützt keine zusätzlichen Konfigurationsdateien.
 *     <p>Wenn die `manageRoutes`-Option aktiviert ist, werden die Dateien zu den Routen im
 *     Value-Store in den Verzeichnissen `routes/results/{apiId}/` und `routes/definitions/{apiId}/`
 *     abgelegt.
 * @ref:cfg {@link de.ii.ogcapi.routes.domain.RoutingConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.routes.domain.ImmutableRoutingConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.routes.infra.EndpointRouteDefinition}, {@link
 *     de.ii.ogcapi.routes.infra.EndpointRouteDelete}, {@link
 *     de.ii.ogcapi.routes.infra.EndpointRouteGet}, {@link
 *     de.ii.ogcapi.routes.infra.EndpointRoutesGet}, {@link
 *     de.ii.ogcapi.routes.infra.EndpointRoutesPost}
 * @ref:queryParameters {@link de.ii.ogcapi.routes.domain.QueryParameterFRoutes}, {@link
 *     de.ii.ogcapi.routes.domain.QueryParameterFRoute}, {@link
 *     de.ii.ogcapi.routes.domain.QueryParameterFRouteDefinition}, {@link
 *     de.ii.ogcapi.routes.app.QueryParameterCrsRoutes}
 * @ref:pathParameters {@link de.ii.ogcapi.routes.domain.PathParameterRouteId}
 */
@Singleton
@AutoBind
public class RoutingBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/DRAFTS/21-000.html",
              "OGC API - Routes - Part 1: Core (DRAFT)"));
  public static String CORE = "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/core";
  public static String MODE = "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/mode";
  public static String INTERMEDIATE_WAYPOINTS =
      "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/intermediate-waypoints";
  public static String HEIGHT =
      "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/height";
  public static String WEIGHT =
      "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/weight";
  public static String OBSTACLES =
      "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/obstacles";
  public static String MANAGE_ROUTES =
      "http://www.opengis.net/spec/ogcapi-routes-1/1.0.0-draft.1/conf/manage-routes";

  @Inject
  public RoutingBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableRoutingConfiguration.Builder()
        .enabled(false)
        .manageRoutes(false)
        .intermediateWaypoints(false)
        .weightRestrictions(false)
        .heightRestrictions(false)
        .obstacles(false)
        .defaultPreference("fastest")
        .defaultMode("driving")
        .additionalFlags(ImmutableMap.of())
        .defaultCrs(CRS84)
        .html(ImmutableHtmlForm.builder().enabled(true).build())
        .build();
  }
}
