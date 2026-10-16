/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import de.ii.ogcapi.foundation.domain.ApiRequestContext
import de.ii.ogcapi.foundation.domain.I18n
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableFoundationConfiguration
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.xtraplatform.features.domain.Tuple
import de.ii.xtraplatform.web.domain.URICustomizer
import jakarta.ws.rs.core.Response
import spock.lang.Specification

import java.time.Instant

class VersionedFeatureResponseHeadersSpec extends Specification {

    static final String FEATURE_URI = 'http://localhost/api/collections/c/items/1'

    I18n i18n = Stub(I18n)
    ApiRequestContext requestContext = Stub(ApiRequestContext)
    VersionedFeatureResponseHeaders subject = new VersionedFeatureResponseHeaders(i18n)

    def setup() {
        i18n.getLinkTitle(_, _) >> Optional.of('title')
        requestContext.getUriCustomizer() >> new URICustomizer(FEATURE_URI + '?f=json')
        requestContext.getLanguage() >> Optional.empty()
    }

    def 'not enabled for a collection without versioned features'() {
        expect:
        !subject.isEnabledForApi(api([]), 'c')
    }

    def 'not enabled for a collection with versioned features disabled'() {
        expect:
        !subject.isEnabledForApi(api([new ImmutableVersionedFeaturesConfiguration.Builder().enabled(false).build()]), 'c')
    }

    def 'enabled for a collection with versioned features'() {
        expect:
        subject.isEnabledForApi(api([new ImmutableVersionedFeaturesConfiguration.Builder().enabled(true).build()]), 'c')
    }

    def 'no headers without a temporal extent'() {
        given:
        Response.ResponseBuilder response = Mock(Response.ResponseBuilder)

        when:
        subject.customize(response, null, 'c', '1', Optional.empty(), requestContext)

        then:
        0 * response._
    }

    def 'current version gets Memento headers but no immutable caching'() {
        given:
        Response.ResponseBuilder response = Mock(Response.ResponseBuilder)

        when:
        subject.customize(response, null, 'c', '1', Optional.of(Tuple.of(Instant.parse('2022-07-04T00:00:00Z'), null)), requestContext)

        then:
        1 * response.header('Memento-Datetime', 'Mon, 4 Jul 2022 00:00:00 GMT')
        1 * response.header('Link', "<${FEATURE_URI}>; rel=\"original\"; title=\"title\"".toString())
        1 * response.header('Link', "<${FEATURE_URI}/versions>; rel=\"timemap\"; title=\"title\"".toString())
        1 * response.header('Link', "<${FEATURE_URI}/versions>; rel=\"version-history\"; title=\"title\"".toString())
        0 * response.header('Cache-Control', _)
    }

    def 'retired version is cached as immutable'() {
        given:
        Response.ResponseBuilder response = Mock(Response.ResponseBuilder)

        when:
        subject.customize(response, null, 'c', '1', Optional.of(Tuple.of(Instant.parse('2022-07-04T00:00:00Z'), Instant.parse('2023-01-01T00:00:00Z'))), requestContext)

        then:
        1 * response.header('Cache-Control', null)
        1 * response.header('Cache-Control', 'public, max-age=31536000, immutable')
        1 * response.header('Memento-Datetime', _)
        3 * response.header('Link', _)
    }

    private static OgcApiDataV2 api(List extensions) {
        new ImmutableOgcApiDataV2.Builder()
                .id('api')
                .putCollections('c', new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                        .id('c')
                        .label('c')
                        .extensions([new ImmutableFoundationConfiguration.Builder().enabled(true).build()] + extensions)
                        .build())
                .build()
    }
}
