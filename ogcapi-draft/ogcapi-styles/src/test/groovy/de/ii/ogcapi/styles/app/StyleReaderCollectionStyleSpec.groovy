/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import de.ii.ogcapi.foundation.domain.ExtensionRegistry
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.foundation.infra.json.ClassSchemaCacheImpl
import de.ii.ogcapi.html.app.StyleReaderImpl
import de.ii.ogcapi.html.domain.MapClient
import de.ii.ogcapi.html.domain.StyleReader
import de.ii.ogcapi.styles.domain.ImmutableStylesConfiguration
import de.ii.ogcapi.styles.domain.MbStyleStylesheet
import de.ii.ogcapi.styles.domain.StylesheetContent
import de.ii.xtraplatform.values.domain.KeyValueStore
import de.ii.xtraplatform.values.domain.StoredValue
import de.ii.xtraplatform.values.domain.ValueStore
import java.nio.charset.StandardCharsets
import spock.lang.Specification

/**
 * The map on a collection page uses a collection style only if it exists or can be derived from
 * the API style, and the API style only if it has a layer for the collection.
 */
class StyleReaderCollectionStyleSpec extends Specification {

    static final String API = "api"
    static final String SERVICE_URL = "https://example.com/api"

    static final String VECTOR_STYLE = '''{
      "version": 8,
      "sources": {
        "api": { "type": "vector", "tiles": ["https://example.com/api/tiles/WebMercatorQuad/{z}/{y}/{x}?f=mvt"] },
        "basemap": { "type": "raster", "tiles": ["https://example.com/basemap/{z}/{y}/{x}.png"] }
      },
      "layers": [
        { "id": "background", "type": "raster", "source": "basemap" },
        { "id": "styled_fill", "type": "fill", "source": "api", "source-layer": "styled" }
      ]
    }'''

    static final String GEOJSON_STYLE = '''{
      "version": 8,
      "sources": {
        "data": { "type": "geojson", "data": "https://example.com/api/collections/styled/items" }
      },
      "layers": [
        { "id": "data_fill", "type": "fill", "source": "data" }
      ]
    }'''

    def 'with derived collection styles, a collection page uses the derived style if the API style has a layer for the collection'() {

        given:

        StyleReader reader = reader(VECTOR_STYLE)
        OgcApiDataV2 apiData = apiData(true)

        when:

        String styled = styleUrl(reader, "styled", apiData)
        String unstyled = styleUrl(reader, "unstyled", apiData)

        then:

        styled == "https://example.com/api/collections/styled/styles/default?f=mbs"
        unstyled == null
        reader.exists(API, Optional.of("unstyled"), "default", StyleReader.StyleFormat.MBS, apiData) == false
    }

    def 'without derived collection styles, a collection page uses the API style only if it has a layer for the collection'() {

        given:

        StyleReader reader = reader(VECTOR_STYLE)
        OgcApiDataV2 apiData = apiData(false)

        when:

        String styled = styleUrl(reader, "styled", apiData)
        String unstyled = styleUrl(reader, "unstyled", apiData)

        then:

        styled == "https://example.com/api/styles/default?f=mbs"
        unstyled == null
    }

    def 'an API style without source layers is used for every collection'() {

        given:

        StyleReader reader = reader(GEOJSON_STYLE)
        OgcApiDataV2 apiData = apiData(false)

        when:

        String unstyled = styleUrl(reader, "unstyled", apiData)

        then:

        unstyled == "https://example.com/api/styles/default?f=mbs"
    }

    def 'the API landing page uses the API style'() {

        given:

        StyleReader reader = reader(VECTOR_STYLE)
        OgcApiDataV2 apiData = apiData(true)

        when:

        String url = reader.getStyleUrl(Optional.of("DEFAULT"), Optional.empty(), API, SERVICE_URL, MapClient.Type.MAP_LIBRE, "default", apiData)

        then:

        url == "https://example.com/api/styles/default?f=mbs"
    }

    def 'deriving a collection style accepts layers of the API source without a source layer'() {

        given:

        String style = '''{
          "version": 8,
          "sources": {
            "api": { "type": "vector", "tiles": ["https://example.com/api/tiles/WebMercatorQuad/{z}/{y}/{x}?f=mvt"] }
          },
          "layers": [
            { "id": "no_source_layer", "type": "fill", "source": "api" },
            { "id": "styled_fill", "type": "fill", "source": "api", "source-layer": "styled" }
          ]
        }'''
        MbStyleStylesheet stylesheet = StyleFormatMbStyle.parse(style.getBytes(StandardCharsets.UTF_8), false)
        StylesheetContent content = new StylesheetContent(style.getBytes(StandardCharsets.UTF_8), "api/default", true, stylesheet)
        StyleFormatMbStyle format = new StyleFormatMbStyle(Stub(ExtensionRegistry), new ClassSchemaCacheImpl())

        when:

        Optional<StylesheetContent> styled = format.deriveCollectionStyle(content, apiData(true), SERVICE_URL, "styled", "default")
        Optional<StylesheetContent> unstyled = format.deriveCollectionStyle(content, apiData(true), SERVICE_URL, "unstyled", "default")

        then:

        styled.get().getMbStyle().get().getLayers()*.getId() == ["no_source_layer", "styled_fill"]
        unstyled.isEmpty()
    }

    static String styleUrl(StyleReader reader, String collectionId, OgcApiDataV2 apiData) {
        return reader.getStyleUrl(Optional.of("DEFAULT"), Optional.of(collectionId), API, SERVICE_URL, MapClient.Type.MAP_LIBRE, "default", apiData)
    }

    static OgcApiDataV2 apiData(boolean deriveCollectionStyles) {
        return new ImmutableOgcApiDataV2.Builder()
                .id(API)
                .addExtensions(new ImmutableStylesConfiguration.Builder()
                        .enabled(true)
                        .deriveCollectionStyles(deriveCollectionStyles)
                        .build())
                .putCollections("styled", new ImmutableFeatureTypeConfigurationOgcApi.Builder().id("styled").label("styled").build())
                .putCollections("unstyled", new ImmutableFeatureTypeConfigurationOgcApi.Builder().id("unstyled").label("unstyled").build())
                .build()
    }

    StyleReader reader(String style) {
        List<String> apiStylePath = [StyleReader.StyleFormat.MBS.valueType(), API]
        StoredValue stylesheet = StyleFormatMbStyle.parse(style.getBytes(StandardCharsets.UTF_8), false)

        // the decorators of StyleReaderImpl call the store with the value type prepended to the path
        TestValueStore store = Stub(TestValueStore) {
            has(_ as String, _ as String[]) >> { String id, String[] path -> id == "default" && path.toList() == apiStylePath }
            get(_ as String, _ as String[]) >> { String id, String[] path -> id == "default" && path.toList() == apiStylePath ? stylesheet : null }
        }

        return new StyleReaderImpl(store)
    }

    interface TestValueStore extends ValueStore, KeyValueStore<StoredValue> {}
}
