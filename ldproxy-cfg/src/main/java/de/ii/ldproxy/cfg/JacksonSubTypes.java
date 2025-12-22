/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.additional.metadata.app.JacksonSubTypeIdsAdditionalMetadata;
import de.ii.ogcapi.codelists.app.JacksonSubTypeIdsCodelists;
import de.ii.ogcapi.collections.app.JacksonSubTypeIdsOgcApiCollections;
import de.ii.ogcapi.collections.queryables.app.JacksonSubTypeIdsQueryables;
import de.ii.ogcapi.collections.schema.app.JacksonSubTypeIdsSchema;
import de.ii.ogcapi.collections.schema.validation.app.JacksonSubTypeIdsSchemaValidation;
import de.ii.ogcapi.common.domain.JacksonSubTypeIdsOgcApiCommon;
import de.ii.ogcapi.crs.app.JacksonSubTypeIdsCrs;
import de.ii.ogcapi.crud.app.JacksonSubTypeIdsCrud;
import de.ii.ogcapi.features.cityjson.app.JacksonSubTypeIdsCityJson;
import de.ii.ogcapi.features.core.app.JacksonSubTypeIdsFeaturesCore;
import de.ii.ogcapi.features.csv.app.JacksonSubTypeIdsCsv;
import de.ii.ogcapi.features.custom.extensions.app.JacksonSubTypeIdsFeaturesExtensions;
import de.ii.ogcapi.features.flatgeobuf.app.JacksonSubTypeIdsFlatgeobuf;
import de.ii.ogcapi.features.geojson.app.JacksonSubTypeIdsGeoJson;
import de.ii.ogcapi.features.geojson.ld.app.JacksonSubTypeIdsGeoJsonLd;
import de.ii.ogcapi.features.gltf.app.JacksonSubTypeIdsGltf;
import de.ii.ogcapi.features.gml.app.JacksonSubTypeIdsGml;
import de.ii.ogcapi.features.html.app.JacksonSubTypeIdsFeaturesHtml;
import de.ii.ogcapi.features.jsonfg.app.JacksonSubTypeIdsJsonFg;
import de.ii.ogcapi.features.resulttype.app.JacksonSubTypeIdsResultType;
import de.ii.ogcapi.features.search.app.JacksonSubTypeIdsSearch;
import de.ii.ogcapi.filter.domain.JacksonSubTypeIdsFilter;
import de.ii.ogcapi.foundation.domain.JacksonSubTypeIdsFoundation;
import de.ii.ogcapi.geometry.simplification.app.JacksonSubTypeIdsGeometrySimplification;
import de.ii.ogcapi.html.app.JacksonSubTypeIdsHtml;
import de.ii.ogcapi.json.app.JacksonSubTypeIdsJson;
import de.ii.ogcapi.mcp.app.JacksonSubTypeIdsMcp;
import de.ii.ogcapi.oas30.app.JacksonSubTypeIdsOas30;
import de.ii.ogcapi.profile.codelist.app.JacksonSubTypeIdsProfileCodelist;
import de.ii.ogcapi.profile.rel.app.JacksonSubTypeIdsProfileRel;
import de.ii.ogcapi.profile.val.app.JacksonSubTypeIdsProfileVal;
import de.ii.ogcapi.projections.app.JacksonSubTypeIdsProjections;
import de.ii.ogcapi.pubsub.app.JacksonSubTypeIdsPubSub;
import de.ii.ogcapi.resources.app.JacksonSubTypeIdsResources;
import de.ii.ogcapi.routes.app.JacksonSubTypeIdsRouting;
import de.ii.ogcapi.sorting.app.JacksonSubTypeIdsSorting;
import de.ii.ogcapi.styles.app.JacksonSubTypeIdsStyles;
import de.ii.ogcapi.text.search.app.JacksonSubTypeIdsTextSearch;
import de.ii.ogcapi.tilematrixsets.app.JacksonSubTypeIdsTileMatrixSets;
import de.ii.ogcapi.tiles.app.JacksonSubTypeIdsTiles;
import de.ii.ogcapi.tiles3d.app.JacksonSubTypeIds3dTiles;
import de.ii.ogcapi.xml.app.JacksonSubTypeIdsXml;
import de.ii.xtraplatform.base.domain.JacksonSubTypeIds;
import de.ii.xtraplatform.feature.changes.sql.app.JacksonSubTypeIdsFeatureChanges;
import de.ii.xtraplatform.features.gml.app.JacksonSubTypeIdsWfs;
import de.ii.xtraplatform.features.graphql.app.JacksonSubTypeIdsGraphQl;
import de.ii.xtraplatform.features.sql.app.FeatureProviderRegisterSql;
import de.ii.xtraplatform.routes.sql.app.JacksonSubTypeIdsRoutes;
import de.ii.xtraplatform.schemas.ext.app.JacksonSubTypeIdsJsonSchema;
import java.util.Set;

public interface JacksonSubTypes {

  static Set<JacksonSubTypeIds> ids() {
    return ImmutableSet.<JacksonSubTypeIds>builder()
        .add(new JacksonSubTypeIds3dTiles())
        .add(new JacksonSubTypeIdsAdditionalMetadata())
        .add(new JacksonSubTypeIdsCityJson())
        .add(new JacksonSubTypeIdsCodelists())
        .add(new JacksonSubTypeIdsCrs())
        .add(new JacksonSubTypeIdsCrud())
        .add(new JacksonSubTypeIdsCsv())
        .add(new JacksonSubTypeIdsFeaturesCore())
        .add(new JacksonSubTypeIdsFeaturesExtensions())
        .add(new JacksonSubTypeIdsFeaturesHtml())
        .add(new JacksonSubTypeIdsFilter())
        .add(new JacksonSubTypeIdsFlatgeobuf())
        .add(new JacksonSubTypeIdsFoundation())
        .add(new JacksonSubTypeIdsGeoJson())
        .add(new JacksonSubTypeIdsGeoJsonLd())
        .add(new JacksonSubTypeIdsGeometrySimplification())
        .add(new JacksonSubTypeIdsGltf())
        .add(new JacksonSubTypeIdsGml())
        .add(new JacksonSubTypeIdsHtml())
        .add(new JacksonSubTypeIdsJson())
        .add(new JacksonSubTypeIdsJsonFg())
        .add(new JacksonSubTypeIdsMcp())
        .add(new JacksonSubTypeIdsOas30())
        .add(new JacksonSubTypeIdsOgcApiCollections())
        .add(new JacksonSubTypeIdsOgcApiCommon())
        .add(new JacksonSubTypeIdsProfileCodelist())
        .add(new JacksonSubTypeIdsProfileRel())
        .add(new JacksonSubTypeIdsProfileVal())
        .add(new JacksonSubTypeIdsProjections())
        .add(new JacksonSubTypeIdsPubSub())
        .add(new JacksonSubTypeIdsQueryables())
        .add(new JacksonSubTypeIdsResources())
        .add(new JacksonSubTypeIdsResultType())
        .add(new JacksonSubTypeIdsRouting())
        .add(new JacksonSubTypeIdsSchema())
        .add(new JacksonSubTypeIdsSearch())
        .add(new JacksonSubTypeIdsSorting())
        .add(new JacksonSubTypeIdsStyles())
        .add(new JacksonSubTypeIdsTextSearch())
        .add(new JacksonSubTypeIdsTileMatrixSets())
        .add(new JacksonSubTypeIdsTiles())
        .add(new JacksonSubTypeIdsXml())
        .add(new FeatureProviderRegisterSql())
        .add(new JacksonSubTypeIdsGraphQl())
        .add(new JacksonSubTypeIdsWfs())
        .add(new JacksonSubTypeIdsFeatureChanges())
        .add(new JacksonSubTypeIdsJsonSchema())
        .add(new JacksonSubTypeIdsRoutes())
        .add(new JacksonSubTypeIdsSchemaValidation())
        .add(new JacksonSubTypeIdsCatchAll())
        .build();
  }
}
