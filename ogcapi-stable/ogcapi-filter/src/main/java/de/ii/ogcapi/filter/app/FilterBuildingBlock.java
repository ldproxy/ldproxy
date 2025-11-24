/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.filter.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.filter.domain.ImmutableFilterConfiguration.Builder;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Filter
 * @langEn Filter features with CQL2 expressions.
 * @langDe Features mit CQL2-Ausdrücken filtern.
 * @scopeEn This building block provides query parameters to filter features using CQL2 (in Text or
 *     JSON encoding).
 *     <p>In addition to the standard functions specified in CQL2, the following custom functions
 *     are supported, too: <code>
 * - `UPPER(String): String` returns the string in upper case.
 * - `LOWER(String): String` returns the string in lower case.
 * - `NOW(): Timestamp` returns the current time.
 * - `DIAMETER2D(Geometry): Double` returns the diameter of a geometry with 2D coordinates.
 * - `DIAMETER3D(Geometry): Double` returns the diameter of a geometry with 3D coordinates.
 *     </code>
 * @scopeDe Dieser Baustein bietet Abfrageparameter zum Filtern von Features mit CQL2 (in Text- oder
 *     JSON-Kodierung).
 *     <p>Zusätzlich zu den in CQL2 spezifizierten Standardfunktionen werden auch die folgenden
 *     benutzerdefinierten Funktionen unterstützt: <code>
 * - `UPPER(String): String` gibt die Zeichenkette in Großbuchstaben zurück.
 * - `LOWER(String): String` gibt die Zeichenkette in Kleinbuchstaben zurück.
 * - `NOW(): Timestamp` gibt die aktuelle Zeit zurück.
 * - `DIAMETER2D(Geometry): Double` gibt den Durchmesser einer Geometrie mit 2D-Koordinaten zurück.
 * - `DIAMETER3D(Geometry): Double` gibt den Durchmesser einer Geometrie mit 3D-Koordinaten zurück.
 *     </code>
 * @conformanceEn This building block implements requirements of the conformance classes *Filter*
 *     and *Features Filter* from the standard [OGC API - Features - Part 3:
 *     Filtering](https://docs.ogc.org/is/19-079r2/19-079r2.html) as well as the conformance classes
 *     *Basic CQL2*, *Advanced Comparison Operators*, *Case-insensitive Comparisons*,
 *     *Accent-insensitive Comparisons*, *Basic Spatial Functions*, *Basic Spatial Functions with
 *     additional Spatial Literals*, *Spatial Functions*, *Temporal Functions*, *Array Functions*,
 *     *Property-Property Comparisons*, *CQL2 Text encoding*, and *CQL2 JSON encoding* from the
 *     standard [Common Query Language (CQL2)](https://docs.ogc.org/is/21-065r2/21-065r2.html).
 *     <p>The publication of queryables is controlled via [Feature Collections -
 *     Queryables](feature_collections_-_queryables.md) and is a prerequisite for clients to be able
 *     to determine the queryable feature properties.
 * @conformanceDe Dieser Baustein implementiert die Anforderungen der Konformitätsklassen *Filter*
 *     und *Features Filter* aus dem Standard [OGC API - Features - Part 3:
 *     Filtering](https://docs.ogc.org/is/19-079r2/19-079r2.html) sowie die Konformitätsklassen
 *     *Basic CQL2*, *Advanced Comparison Operators*, *Case-insensitive Comparisons*,
 *     *Accent-insensitive Comparisons*, *Basic Spatial Functions*, *Basic Spatial Functions with
 *     additional Spatial Literals*, *Spatial Functions*, *Temporal Functions*, *Array Functions*,
 *     *Property-Property Comparisons*, *CQL2 Text encoding*, und *CQL2 JSON encoding* aus dem
 *     Standard [Common Query Language (CQL2)](https://docs.ogc.org/is/21-065r2/21-065r2.html).
 *     <p>Die Veröffentlichung der Queryables wird über [Feature Collections -
 *     Queryables](feature_collections_-_queryables.md) gesteuert und ist Voraussetzung, damit
 *     Clients die abfragbaren Objekteigenschaften bestimmen können.
 * @limitationsEn Depending on the feature provider, some capabilities of CQL2 may not be supported.
 *     Specifically, in GeoPackage feature providers, queryables in a JSON column that are arrays
 *     are not supported. In PostgreSQL/PostGIS feature providers, the `A_OVERLAPS` operator is not
 *     supported for queryables in a JSON column.
 *     <p>The conformance classes *Functions* and *Arithmetic Expressions* are not supported.
 *     <p>The operator `IN` requires a property on the left side and literals on the right side.
 *     <p>Only `booleanLiteral` may be used from `booleanExpression` in a `scalarExpression`.
 *     <p>The Unicode characters "\x10000" to "\x10FFFF" are not supported.
 * @limitationsDe Je nach Feature-Provider werden einige Funktionen von CQL2 möglicherweise nicht
 *     unterstützt. Insbesondere werden in GeoPackage Feature-Providern Queryables in einer
 *     JSON-Spalte, die Arrays sind, nicht unterstützt. In PostgreSQL/PostGIS-Feature-Anbietern wird
 *     der Operator `A_OVERLAPS` für Queryables in einer JSON-Spalte nicht unterstützt.
 *     <p>Die Konformitätsklassen *Functions* und *Arithmetic Expressions* werden nicht unterstützt.
 *     <p>In einer `scalarExpression` darf aus `booleanExpression` nur `booleanLiteral` verwendet
 *     werden.
 *     <p>Der Operator `IN` erfordert eine Eigenschaft auf der linken Seite und Literale auf der
 *     rechten Seite.
 *     <p>Die Unicode-Zeichen "\x10000" bis "\x10FFFF" werden nicht unterstützt.
 * @ref:cfg {@link de.ii.ogcapi.filter.domain.FilterConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.filter.domain.ImmutableFilterConfiguration}
 * @ref:queryParameters {@link de.ii.ogcapi.filter.api.QueryParameterFilter}, {@link
 *     de.ii.ogcapi.filter.api.QueryParameterFilterCrs}, {@link
 *     de.ii.ogcapi.filter.api.QueryParameterFilterLang}
 */
@Singleton
@AutoBind
public class FilterBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.STABLE_OGC);
  public static final Optional<ExternalDocumentation> SPEC =
      Optional.of(
          ExternalDocumentation.of(
              "https://docs.ogc.org/is/19-079r2/19-079r2.html",
              "OGC API - Features - Part 3: Filtering"));

  @Inject
  public FilterBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new Builder().enabled(false).build();
  }
}
