/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ogcapi.codelists.app.CodelistsBuildingBlock;
import de.ii.ogcapi.collections.app.CollectionsBuildingBlock;
import de.ii.ogcapi.collections.queryables.app.QueryablesBuildingBlock;
import de.ii.ogcapi.collections.schema.app.SchemaBuildingBlock;
import de.ii.ogcapi.common.domain.CommonBuildingBlock;
import de.ii.ogcapi.crs.app.CrsBuildingBlock;
import de.ii.ogcapi.crud.app.CrudBuildingBlock;
import de.ii.ogcapi.features.cityjson.app.CityJsonBuildingBlock;
import de.ii.ogcapi.features.core.app.FeaturesCoreBuildingBlock;
import de.ii.ogcapi.features.csv.app.FeaturesCsvBuildingBlock;
import de.ii.ogcapi.features.custom.extensions.app.FeaturesExtensionsBuildingBlock;
import de.ii.ogcapi.features.flatgeobuf.app.FeaturesFlatgeobufBuildingBlock;
import de.ii.ogcapi.features.geojson.app.GeoJsonBuildingBlock;
import de.ii.ogcapi.features.geojson.ld.app.GeoJsonLdBuildingBlock;
import de.ii.ogcapi.features.gltf.app.GltfBuildingBlock;
import de.ii.ogcapi.features.gml.app.GmlBuildingBlock;
import de.ii.ogcapi.features.html.app.FeaturesHtmlBuildingBlock;
import de.ii.ogcapi.features.jsonfg.app.JsonFgBuildingBlock;
import de.ii.ogcapi.features.resulttype.app.ResultTypeBuildingBlock;
import de.ii.ogcapi.features.search.app.SearchBuildingBlock;
import de.ii.ogcapi.filter.app.FilterBuildingBlock;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FoundationBuildingBlock;
import de.ii.ogcapi.geometry.simplification.app.GeometrySimplificationBuildingBlock;
import de.ii.ogcapi.html.app.HtmlBuildingBlock;
import de.ii.ogcapi.json.app.JsonBuildingBlock;
import de.ii.ogcapi.oas30.app.OpenApiBuildingBlock;
import de.ii.ogcapi.projections.app.ProjectionsBuildingBlock;
import de.ii.ogcapi.pubsub.app.PubSubBuildingBlock;
import de.ii.ogcapi.resources.app.ResourcesBuildingBlock;
import de.ii.ogcapi.routes.app.RoutingBuildingBlock;
import de.ii.ogcapi.sorting.app.SortingBuildingBlock;
import de.ii.ogcapi.styles.app.StylesBuildingBlock;
import de.ii.ogcapi.text.search.app.TextSearchBuildingBlock;
import de.ii.ogcapi.tilematrixsets.app.TileMatrixSetsBuildingBlock;
import de.ii.ogcapi.tiles.app.TilesBuildingBlock;
import de.ii.ogcapi.tiles3d.app.Tiles3dBuildingBlock;
import de.ii.ogcapi.xml.app.XmlBuildingBlock;
import de.ii.xtraplatform.base.domain.AppContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class OgcApiExtensionRegistry implements ExtensionRegistry {

  private final Set<ApiExtension> apiExtensions;

  OgcApiExtensionRegistry(AppContext appContext) {
    this.apiExtensions =
        ImmutableSet.<ApiExtension>builder()
            .add(new CityJsonBuildingBlock())
            .add(new CodelistsBuildingBlock())
            .add(new CollectionsBuildingBlock())
            .add(new CommonBuildingBlock())
            .add(new CrsBuildingBlock(null, null))
            .add(new CrudBuildingBlock(null))
            .add(new FeaturesCsvBuildingBlock())
            .add(new FeaturesCoreBuildingBlock(null, null, null))
            .add(new FeaturesExtensionsBuildingBlock())
            .add(new FeaturesHtmlBuildingBlock())
            .add(new FeaturesFlatgeobufBuildingBlock())
            .add(new FilterBuildingBlock())
            .add(new FoundationBuildingBlock())
            .add(new GeoJsonBuildingBlock())
            .add(new GeoJsonLdBuildingBlock())
            .add(new GeometrySimplificationBuildingBlock())
            .add(new GltfBuildingBlock())
            .add(new GmlBuildingBlock())
            .add(new HtmlBuildingBlock(appContext))
            .add(new JsonBuildingBlock())
            .add(new JsonFgBuildingBlock())
            .add(new OpenApiBuildingBlock())
            .add(new ProjectionsBuildingBlock())
            .add(new PubSubBuildingBlock(null, this, null, null))
            .add(new QueryablesBuildingBlock(null, null))
            .add(new ResourcesBuildingBlock())
            .add(new ResultTypeBuildingBlock())
            .add(new RoutingBuildingBlock())
            .add(new SchemaBuildingBlock())
            .add(new SearchBuildingBlock())
            .add(new SortingBuildingBlock(null, null))
            .add(new StylesBuildingBlock(this)) // TODO: StyleFormatExtensions
            .add(new TextSearchBuildingBlock())
            .add(new TileMatrixSetsBuildingBlock())
            .add(
                new TilesBuildingBlock(
                    this, null, null)) // TODO: TileFormatWithQuerySupportExtension,
            // TileSetFormatExtension
            .add(new Tiles3dBuildingBlock(null, null))
            .add(new XmlBuildingBlock())
            .build();
  }

  @Override
  public List<ApiExtension> getExtensions() {
    return ImmutableList.copyOf(apiExtensions);
  }

  @Override
  public <T extends ApiExtension> List<T> getExtensionsForType(Class<T> extensionType) {
    return apiExtensions.stream()
        .filter(
            extension -> extension != null && extensionType.isAssignableFrom(extension.getClass()))
        .map(extensionType::cast)
        .collect(Collectors.toList());
  }
}
