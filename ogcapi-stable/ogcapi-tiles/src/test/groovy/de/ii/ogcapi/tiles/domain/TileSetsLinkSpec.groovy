/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import de.ii.ogcapi.foundation.domain.ImmutableLink
import de.ii.ogcapi.tiles.domain.TileSet.DataType
import spock.lang.Specification

class TileSetsLinkSpec extends Specification {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new Jdk8Module())

    static TileSetsLink link(String href, Map scope = null) {
        Map json = [rel: "describedby", title: href, href: "https://example.com/" + href]
        if (scope != null) {
            json.scope = scope
        }
        return MAPPER.readValue(MAPPER.writeValueAsString(json), TileSetsLink)
    }

    static final TilesConfiguration CONFIG = new ImmutableTilesConfiguration.Builder()
            .addAdditionalLinks(
                    link("all"),
                    link("dataset", [levels: ["DATASET"]]),
                    link("collections", [levels: ["COLLECTIONS"]]),
                    link("vector", [tileTypes: ["VECTOR"]]),
                    link("map", [tileTypes: ["MAP"]]),
                    link("styled", [tileTypes: ["STYLED_MAP"]]),
                    link("topo", [styles: ["topo"]]),
                    link("dataset-topo", [levels: ["DATASET"], tileTypes: ["STYLED_MAP"], styles: ["topo"]]))
            .build()

    def "a configured link is read with its scope"() {
        when:
        TileSetsLink link = link("x", [levels: ["DATASET"], tileTypes: ["STYLED_MAP"], styles: ["topo", "night"]])

        then:
        link.getHref() == "https://example.com/x"
        link.getRel() == "describedby"
        link.getScope().get().getLevels() == [TileSetsLinkScope.Level.DATASET]
        link.getScope().get().getTileTypes() == [TileSetsLinkScope.TileType.STYLED_MAP]
        link.getScope().get().getStyles() == ["topo", "night"]
    }

    def "the published link has no scope"() {
        when:
        def published = link("x", [levels: ["DATASET"]]).toLink()

        then:
        published == new ImmutableLink.Builder().rel("describedby").title("x").href("https://example.com/x").build()
        !MAPPER.writeValueAsString(published).contains("scope")
    }

    def "the links of a tileset list: #list"() {
        when:
        List<String> titles = CONFIG.additionalLinksFor(Optional.ofNullable(collectionId), dataType, Optional.ofNullable(styleId))*.title

        then:
        titles == expected

        where:
        list                                | collectionId | dataType        | styleId || expected
        "/tiles"                            | null         | DataType.vector | null    || ["all", "dataset", "vector"]
        "/map/tiles"                        | null         | DataType.map    | null    || ["all", "dataset", "map"]
        "/styles/topo/map/tiles"            | null         | DataType.map    | "topo"  || ["all", "dataset", "styled", "topo", "dataset-topo"]
        "/styles/night/map/tiles"           | null         | DataType.map    | "night" || ["all", "dataset", "styled"]
        "/collections/c/tiles"              | "c"          | DataType.vector | null    || ["all", "collections", "vector"]
        "/collections/c/styles/topo/map/tiles" | "c"       | DataType.map    | "topo"  || ["all", "collections", "styled", "topo"]
    }
}
