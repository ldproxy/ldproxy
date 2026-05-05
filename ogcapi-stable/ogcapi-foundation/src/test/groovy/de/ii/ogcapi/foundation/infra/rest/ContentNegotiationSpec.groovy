/*
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.infra.rest

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import de.ii.ogcapi.foundation.domain.ApiMediaType
import spock.lang.Shared
import spock.lang.Specification

import jakarta.ws.rs.core.MediaType

class ContentNegotiationSpec extends Specification {

    @Shared MediaType wildcard
    @Shared MediaType image
    @Shared MediaType png
    @Shared MediaType application
    @Shared MediaType json
    @Shared MediaType jsonfg
    @Shared MediaType jsonfgc
    @Shared MediaType geojson
    @Shared MediaType text
    @Shared MediaType html
    @Shared MediaType xml

    def setupSpec() {
        wildcard = new MediaType("*", "*")
        text = new MediaType("text", "*")
        html = new MediaType("text", "html")
        image = new MediaType("image", "*")
        png = new MediaType("image", "png")
        application = new MediaType("application", "*")
        xml = new MediaType("application", "xml")
        json = new MediaType("application", "json")
        geojson = new MediaType("application", "geo+json")
        jsonfg = new MediaType("application", "vnd.ogc.fg+json")
        jsonfgc = new MediaType("application", "vnd.ogc.fg+json", ImmutableMap.of("compatibility", "geojson"))
    }

    def "Negotiate with wildcard"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(wildcard)
        List<MediaType> provided = ImmutableList.of(html, json)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && (mediaType == html || mediaType == json)
    }

    def "Negotiate with wildcard / null"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(wildcard)
        List<MediaType> provided = ImmutableList.of()
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType == null
    }

    def "Negotiate with subtype wildcard"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(application)
        List<MediaType> provided = ImmutableList.of(html, json, png)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && mediaType == json
    }

    def "Negotiate with subtype wildcards"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(application, image)
        List<MediaType> provided = ImmutableList.of(html, json, png)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && (mediaType == json || mediaType == png)
    }

    def "Negotiate with subtype wildcard / null "() {
        given:
        List<MediaType> acceptable = ImmutableList.of(application)
        List<MediaType> provided = ImmutableList.of(html)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType == null
    }

    def "Negotiate with subtype"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(json, xml)
        List<MediaType> provided = ImmutableList.of(html, json, png)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && mediaType == json
    }

    def "Negotiate with subtypes"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(json, xml, png)
        List<MediaType> provided = ImmutableList.of(html, json, png)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && (mediaType == json || mediaType == png)
    }

    def "Negotiate with subtypes / null"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(json, xml, png)
        List<MediaType> provided = ImmutableList.of(html)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType == null
    }

    def "Negotiate with + subtype"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(xml, json)
        List<MediaType> provided = ImmutableList.of(html, geojson, jsonfg, jsonfgc)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && (mediaType == jsonfg || mediaType == geojson || mediaType == jsonfgc)
    }

    def "Negotiate with + subtype / null"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(xml, json)
        List<MediaType> provided = ImmutableList.of(html, geojson, jsonfg, jsonfgc)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && (mediaType == jsonfg || mediaType == geojson || mediaType == jsonfgc)
    }

    def "Negotiate with (no) parameters"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(jsonfg)
        List<MediaType> provided = ImmutableList.of(geojson, jsonfg, jsonfgc)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && mediaType == jsonfg
    }
    def "Negotiate with parameters"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(jsonfgc)
        List<MediaType> provided = ImmutableList.of(geojson, jsonfg, jsonfgc)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && mediaType == jsonfgc
    }
    def "Negotiate with parameters, fallback"() {
        given:
        List<MediaType> acceptable = ImmutableList.of(jsonfgc)
        List<MediaType> provided = ImmutableList.of(geojson, jsonfg)
        when:
        MediaType mediaType = ApiMediaType.negotiateMediaType(acceptable, provided)
        then:
        mediaType != null && mediaType == jsonfg
    }
}