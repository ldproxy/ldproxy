# Spezifikationen und Technologien

Dies ist ein Überblick über unterstützte Spezifikationen und Technologien. Für Einschränkungen und Begrenzungen siehe die Dokumentation der ldproxy-Bausteine.

## Web-API-Spezifikationen

Unterstützt werden derzeit die folgenden OGC-Standards bzw. aktuellen Entwürfen von zukünftigen OGC-Standards:

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

Die in ldproxy implementierte Funktionalität weicht bei Entwürfen aufgrund der dynamischen Entwicklung i.d.R. von der in den aktuellen Entwürfen beschriebenen Spezifikation ab.

Zusätzlich werden verschiedene Erweiterungen, für die noch keine offiziellen Entwürfe vorliegen, unterstützt.

ldproxy ist als [OGC-Referenzimplementierung für "OGC API - Features - Part 1: Core" und "OGC API - Features - Part 2: Coordinate Reference Systems by Reference" zertifiziert](http://www.ogc.org/resource/products/details/?pid=1705).

<img src='https://cite.opengeospatial.org/teamengine/site/certification-logo.gif' alt='Certified OGC Compliant Logo' height='74' style='padding:0;margin:0;border:0;'/>

## Formate

ldproxy unterstützt die folgenden Formate, die von verschiedenen Tools und Bibliotheken unterstützt werden:

* Allgemeine Ressourcen
  * JSON
  * HTML
  * XML (nur für Ressourcen, die in OGC API Features spezifiziert sind)
* API-Definitionen
  * [OpenAPI 3.0](http://spec.openapis.org/oas/v3.0.3) als JSON, YAML und HTML
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
  * PNG (nicht für Feature-Tile-Providers)
  * JPEG (nicht für Feature-Tile-Providers)
  * WebP (nicht für Feature-Tile-Providers)
  * TIFF (nicht für Feature-Tile-Providers)
* 3D Tiles
  * [3D Tiles 1.1](https://docs.ogc.org/cs/22-025r4/22-025r4.html)
* Tileset-Metadaten
  * [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0, JSON Encoding](https://docs.ogc.org/is/17-083r4/17-083r4.html)
  * [TileJSON 3.0.0](https://github.com/mapbox/tilejson-spec/tree/master/3.0.0)
* Kachelschemas
  * [OGC Two Dimensional Tile Matrix Set and Tile Set Metadata 2.0, JSON Encoding](https://docs.ogc.org/is/17-083r4/17-083r4.html)
* Style-Formate (die meisten Formaten können über OGC API Styles bereitgestellt werden, sie werden aber nicht von ldproxy verarbeitet)
  * [MapLibre Style Spec](https://maplibre.org/maplibre-style-spec/) (Styles können in HTML-Darstellungen verwendet werden, bei denen MapLibre GL JS der Kartenclient ist)
  * [3D Tiles Styling](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc45) (Styles können in HTML-Darstellungen verwendet werden, bei denen CesiumJS der Kartenclient ist)
  * OGC SLD 1.0 
  * OGC SLD 1.1
  * QGIS QML
  * ArcGIS Desktop (lyr)
  * ArcGIS Pro (lyrx)
* Routen-Formate
  * [OGC Route Exchange Model, draft](https://docs.ogc.org/DRAFTS/21-001.html)

HTML kann mit [schema.org](https://schema.org/)-Markup ([schema:Place](https://schema.org/Place), [schema:Dataset](https://schema.org/Dataset) und [schema:DataCatalog](https://schema.org/DataCatalog)) angereichert werden.

## Datenquellen

### Features

Alle Feature-Daten in ldproxy werden über Feature-Provider bereitgestellt, wobei jede API maximal einen Provider hat.

Es werden drei Arten von Feature-Providern unterstützt:

* PostgreSQL-Datenbanken ab Version 9.6 mit PostGIS ab Version 2.4
  * Alle Tabellen eines Providers müssen in einer Datenbank und in einem Schema liegen. Jede Tabelle, außer Zwischentabellen, muss eine eindeutige Identifikator-Spalte besitzen (Primary Key bzw. mit Unique Index). Es wird empfohlen, Integer für die Identifikatoren zu verwenden.
* GeoPackage
* OGC Web Feature Services (WFS)
  * Damit ein WFS angebunden werden kann, sollte er beim Datenzugriff über die GetFeature-Operation Paging über die Query-Parameter `COUNT` und `STARTINDEX` unterstützen.

### Tiles

Alle Tiles in ldproxy werden über Tile-Provider bereitgestellt.

Es werden drei Arten von Tile-Providern unterstützt:

* Features, die von derselben API bereitgestellt werden
* [MBTiles](https://github.com/mapbox/mbtiles-spec/blob/master/1.3/spec.md)
* HTTP (Zugriff über xyz-URI-Templates)

### 3D Tiles

Alle Kacheln werden aus einem PostgreSQL-Feature-Provider mit CityGML-Gebäudefeatures (LoD 1, LoD 2) erzeugt.
