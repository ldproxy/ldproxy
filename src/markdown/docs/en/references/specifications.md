# Specifications and technologies

This is an overview of supported specifications and technologies. For constraints and limitations see the documentation of the ldproxy building blocks.

## Web API Specifications

The following published standards and drafts of future standards for geospatial Web APIs or HTTP APIs are implemented in ldproxy:

* [OGC API - Features - Part 1: Core, Version 1.0.1](https://docs.ogc.org/is/17-069r4/17-069r4.html)
* [OGC API - Features - Part 2: Coordinate Reference Systems by Reference 1.0.1](https://docs.ogc.org/is/18-058r1/18-058r1.html)
* [OGC API - Features - Part 3: Filtering, 1.0.0](https://docs.ogc.org/is/19-079r2/19-079r2.html)
* [OGC API - Features - Part 4: Create, Replace, Update and Delete, draft](https://docs.ogc.org/DRAFTS/20-002r1.html)
* [OGC API - Features - Part 5: Schemas, draft](https://docs.ogc.org/DRAFTS/23-058r1.html)
* [OGC API - Tiles - Part 1: Core, Version 1.0.0](https://docs.ogc.org/is/20-057/20-057.html)
* [OGC API - Styles, draft](https://docs.ogc.org/DRAFTS/20-009.html)
* [OGC API - Routes - Part 1: Core, draft](https://docs.ogc.org/DRAFTS/21-000.html)
* [OGC API - 3D GeoVolumes, draft](https://github.com/opengeospatial/ogcapi-3d-geovolumes)
* [Common Query Language (CQL2), 1.0.0](https://docs.ogc.org/is/21-065r2/21-065r2.html)

The functionality implemented in lproxy will in general differ from the current draft specifications since the drafts are changing.

Additionally, several ldproxy extensions are supported for which no official drafts are available at this time.

ldproxy is certified as an [OGC reference implementation for "OGC API - Features - Part 1: Core" and "OGC API - Features - Part 2: Coordinate Reference Systems by Reference"](http://www.ogc.org/resource/products/details/?pid=1705).

<img src='https://cite.opengeospatial.org/teamengine/site/certification-logo.gif' alt='Certified OGC Compliant Logo' height='74' style='padding:0;margin:0;border:0;'/>

## Formats

ldproxy supports the following formats that are supported by existing tools and libraries:

* General resources
  * JSON
  * HTML
  * XML (only for resources specified in OGC API Features)
* API definitions
  * [OpenAPI 3.0.3](http://spec.openapis.org/oas/v3.0.3) as JSON, YAML and HTML
* Features
  * [GeoJSON](https://datatracker.ietf.org/doc/html/rfc7946)
  * [JSON-FG 0.2.2](https://docs.ogc.org/DRAFTS/21-045.html)
  * HTML
  * [GML 3.2.2](https://portal.ogc.org/files/?artifact_id=74183&version=2)
  * [FlatGeobuf](https://flatgeobuf.org/)
  * CSV
  * [CityJSON 1.0](https://www.cityjson.org/specs/1.0.3/) and [CityJSON 1.1](https://www.cityjson.org/specs/1.1.3/)
  * [glTF 2.0](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html)
    * [KHR_mesh_quantization](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_mesh_quantization)
    * [EXT_mesh_features](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_mesh_features)
    * [EXT_structural_metadata](https://github.com/CesiumGS/glTF/tree/3d-tiles-next/extensions/2.0/Vendor/EXT_structural_metadata)
    * [CESIUM_primitive_outline](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Vendor/CESIUM_primitive_outline)
* 2D Tiles
  * [Mapbox Vector Tiles 2.1](https://github.com/mapbox/vector-tile-spec/tree/master/2.1)
  * PNG (not for Feature Tile Providers)
  * JPEG (not for Feature Tile Providers)
  * WebP (not for Feature Tile Providers)
  * TIFF (not for Feature Tile Providers)
* 3D Tiles
  * [3D Tiles 1.1](https://docs.ogc.org/cs/22-025r4/22-025r4.html)
* Tile Set metadata
  * [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0](https://docs.ogc.org/is/17-083r4/17-083r4.html)
  * [TileJSON 3.0.0](https://github.com/mapbox/tilejson-spec/tree/master/3.0.0)
* Tile Matrix Sets
  * [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0](https://docs.ogc.org/is/17-083r4/17-083r4.html)
* Style encodings (styles in most formats can be shared via OGC API Styles, but are not processed by ldproxy)
  * [MapLibre Style Spec](https://maplibre.org/maplibre-style-spec/) (styles can be used in HTML representations where MapLibre GL JS is the map client)
  * [3D Tiles Styling](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc45) (styles can be used in HTML representations where CesiumJS is the map client)
  * OGC SLD 1.0 
  * OGC SLD 1.1
  * QGIS QML
  * ArcGIS Desktop (lyr)
  * ArcGIS Pro (lyrx)
* Route Encodings 
  * [OGC Route Exchange Model, draft](https://docs.ogc.org/DRAFTS/21-001.html)

HTML can be annotated with [schema.org](https://schema.org/) markup ([schema:Place](https://schema.org/Place), [schema:Dataset](https://schema.org/Dataset) and [schema:DataCatalog](https://schema.org/DataCatalog)).

## Data Providers

### Features

All feature data in ldproxy is provided by feature providers, where each API has one provider. Two types of providers are supported:

* PostgreSQL databases (version 9.6 or later) with PostGIS (version 2.4 or later)
  * All tables of a provider must be in one database and one schema. Each table, except intermediate tables, must have a unique identifier column (primary key or with unique index). It is recommended to use integers for the identifiers for performance reasons.
* GeoPackage
* OGC Web Feature Services (WFS)
  * The WFS should support the query parameters `COUNT` and `STARTINDEX` in the GetFeature operation.
  
### 2D Tiles

All tiles are provided by a tile provider.

Three types of tile providers are supported:

* Features (provided by the same API)
* [MBTiles](https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md)
* HTTP (access via a xyz-URI-Template)

### 3D Tiles

All tiles are generated from a PostgreSQL feature provider with CityGML building features (LoD 1, LoD 2).
