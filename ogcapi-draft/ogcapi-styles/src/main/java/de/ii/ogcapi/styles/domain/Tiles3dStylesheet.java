/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.xtraplatform.docs.DocFile;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueBuilder;
import de.ii.xtraplatform.values.domain.ValueEncoding.FORMAT;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore.FormatAlias;
import java.util.Map;
import org.immutables.value.Value;

/**
 * @langEn # 3D Tiles Styles
 *     <p>3D Tiles stylesheets are used by the building block
 *     [Styles](../services/building-blocks/styles.md) and by the web maps of other building blocks
 *     that show 3D Tiles, e.g. [3D Tiles](../services/building-blocks/3d_tiles.md). They follow the
 *     chapter [Styling](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc45) of the OGC 3D Tiles
 *     Specification.
 * @langDe # 3D-Tiles-Styles
 *     <p>3D-Tiles-Stylesheets werden vom Baustein [Styles](../services/building-blocks/styles.md)
 *     und von den Webkarten anderer Bausteine genutzt, die 3D Tiles zeigen, z.B. [3D
 *     Tiles](../services/building-blocks/3d_tiles.md). Sie folgen dem Kapitel
 *     [Styling](https://docs.ogc.org/cs/22-025r4/22-025r4.html#toc45) der OGC 3D Tiles
 *     Specification.
 * @langEn ## Storage
 *     <p>3D Tiles stylesheets reside in the
 *     [Store](../application/20-configuration/10-store-new.md) as values with type `3dtiles-styles`
 *     and relative path `{apiId}/{styleId}.json`. YAML (`yml`) is supported as well, the extension
 *     `3dtiles` is still supported.
 * @langDe ## Speicherung
 *     <p>3D-Tiles-Stylesheets liegen im [Store](../application/20-configuration/10-store-new.md)
 *     als Values mit Typ `3dtiles-styles` und dem relativen Pfad `{apiId}/{styleId}.json`. YAML
 *     (`yml`) wird ebenfalls unterstützt, die Endung `3dtiles` wird weiterhin unterstützt.
 */
@DocFile(path = "auxiliaries", name = "3dtiles-styles.md")
@Value.Immutable
@Value.Style(jdkOnly = true, builder = "new", deepImmutablesDetection = true)
@FromValueStore(
    type = "3dtiles-styles",
    formatAliases = {@FormatAlias(extension = "3dtiles", format = FORMAT.JSON)})
@JsonDeserialize(builder = ImmutableTiles3dStylesheet.Builder.class)
public abstract class Tiles3dStylesheet implements StoredValue {

  public static final String SCHEMA_REF = "#/components/schemas/3dTilesStylesheet";

  // 'defines' is not yet supported

  // oneOf Tiles3dConditions, String, Boolean
  @Value.Default
  public Object getShow() {
    return true;
  }

  // oneOf Tiles3dConditions, String
  @Value.Default
  public Object getColor() {
    return "color('#FFFFFF')";
  }

  public abstract Map<String, Object> getMeta();

  public abstract Map<String, Object> getExtensions();

  public abstract Map<String, Object> getExtras();

  abstract static class Builder implements ValueBuilder<Tiles3dStylesheet> {}
}
