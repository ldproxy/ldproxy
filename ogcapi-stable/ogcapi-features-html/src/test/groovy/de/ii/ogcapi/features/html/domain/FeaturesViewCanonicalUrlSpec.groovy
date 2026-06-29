/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.html.domain

import de.ii.ogcapi.features.core.domain.FeatureQueryScope
import spock.lang.Specification

class FeaturesViewCanonicalUrlSpec extends Specification {

    def "an ad-hoc query response suppresses the canonical (self) link"() {
        given: "a feature-collection view for a transient ad-hoc query (POST, no addressable resource)"
        def view = ModifiableFeatureCollectionView.create()
                .setUri(URI.create("http://localhost:7080/foo/search"))
                .setQueryScope(FeatureQueryScope.AD_HOC_QUERY)

        expect: "no canonical link is offered"
        view.getCanonicalUrl() == Optional.empty()
    }

    def "a stored query response keeps the canonical (self) link"() {
        given: "a feature-collection view for a stored query (GET, addressable resource)"
        def view = ModifiableFeatureCollectionView.create()
                .setUri(URI.create("http://localhost:7080/foo/search/a-query?f=html"))
                .setQueryScope(FeatureQueryScope.STORED_QUERY)

        expect: "the canonical link is the resource URL without query parameters"
        view.getCanonicalUrl() == Optional.of("http://localhost:7080/foo/search/a-query")
    }

    def "a regular collection response keeps the canonical (self) link"() {
        given: "a feature-collection view for a collection (default scope)"
        def view = ModifiableFeatureCollectionView.create()
                .setUri(URI.create("http://localhost:7080/foo/collections/bar/items?f=html"))

        expect: "the scope defaults to COLLECTION and the canonical link is retained"
        view.queryScope() == FeatureQueryScope.COLLECTION
        view.getCanonicalUrl() == Optional.of("http://localhost:7080/foo/collections/bar/items")
    }
}
