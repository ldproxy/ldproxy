/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.geojson.ld.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.geojson.ld.domain.ImmutableGeoJsonLdConfiguration;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title Features - GeoJSON-LD
 * @langEn Encode features as GeoJSON-LD.
 * @langDe Kodierung von Features als GeoJSON-LD.
 * @scopeEn The building block *Features - GeoJSON-LD* adds the following information to the GeoJSON
 *     output:
 *     <p><code>
 * - A JSON-LD context to be referenced from the GeoJSON outputs of the Features and Feature resources. The context can be external or provided via the API. The context must contain at least the following entries:
 * - `"@version": 1.1`
 * - `"geojson": "https://purl.org/geojson/vocab#"`
 * - `"FeatureCollection": "geojson:FeatureCollection"`
 * - `"features": { "@id": "geojson:features", "@container":"@set" }`
 * - `"feature": "geojson:Feature"`
 * - `"type": "geojson:type"`
 * - `"properties":"@nest"`
 * - In addition to the "type" property, which is fixed to "Feature" in GeoJSON, "@type" is added as another property with the values specified in the configuration.
 * - In addition to the "id" property, "@id" is added as another property based on the value from "id" and the URI template specified in the configuration. Dabei wird `{{serviceUrl}}` durch die Landing-Page-URI der API, `{{collectionId}}` durch die Collection-ID und `{{featureId}}` durch den Wert von "id" ersetzt.
 * </code>
 * @scopeDe Der Baustein *Features - GeoJSON-LD* ergänzt die GeoJSON-Ausgabe um die folgenden
 *     Angaben:
 *     <p><code>
 * - Einen JSON-LD-Context, auf den aus den GeoJSON-Ausgaben der Ressourcen Features und
 *     Feature verwiesen wird. Der Context kann extern liegen oder über die API bereitgestellt
 *     werden. Der Context muss mindestens die folgenden Einträge enthalten:
 *   - `"@version": 1.1`
 *   - `"geojson": "https://purl.org/geojson/vocab#"`
 *   - `"FeatureCollection": "geojson:FeatureCollection"`
 *   - `"features": { "@id": "geojson:features", "@container": "@set" }`
 *   - `"Feature": "geojson:Feature"`
 *   - `"type": "geojson:type"`
 *   - `"properties": "@nest"`
 * - Zusätzlich zur Eigenschaft "type", die in GeoJSON fest mit "Feature" belegt ist, wird "@type" als weitere
 *     Eigenschaft mit den in der Konfiguration angegeben Werten ergänzt.
 * - Zusätzlich zur Eigenschaft "id", wird "@id" als weitere Eigenschaft auf Basis des Wertes aus "id" und dem in
 *     der Konfiguration angegeben URI-Template ergänzt. Dabei wird `{{serviceUrl}}` durch die
 *     Landing-Page-URI der API, `{{collectionId}}` durch die Collection-ID und `{{featureId}}`
 *     durch den Wert von "id" ersetzt.
 *     </code>
 * @cfgFilesEn If the context file is provided by the API, the context must be located in the
 *     resource store under the relative path `json-ld-contexts/{apiId}/{collectionId}.jsonld`, or
 *     `json-ld-contexts/{apiId}/{contextFileName}`, if the `contextFileName` configuration option
 *     is used.
 * @cfgFilesDe Wenn der Context über die API bereitgestellt wird, muss der Context im Verzeichnis
 *     des Ressourcen-Stores unter dem relativen Pfad
 *     `json-ld-contexts/{apiId}/{collectionId}.jsonld` liegen, bzw. unter
 *     `json-ld-contexts/{apiId}/{contextFileName}`, wenn die Konfigurationsoption `contextFileName`
 *     verwendet wird.
 * @ref:cfg {@link de.ii.ogcapi.features.geojson.ld.domain.GeoJsonLdConfiguration}
 * @ref:cfgProperties {@link
 *     de.ii.ogcapi.features.geojson.ld.domain.ImmutableGeoJsonLdConfiguration}
 * @ref:pathParameters {@link
 *     de.ii.ogcapi.features.geojson.ld.app.PathParameterCollectionIdGeoJsonLd}
 */
@Singleton
@AutoBind
public class GeoJsonLdBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();
  public static final String STORE_RESOURCE_TYPE = "json-ld-contexts";

  @Inject
  public GeoJsonLdBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableGeoJsonLdConfiguration.Builder().enabled(false).build();
  }
}
