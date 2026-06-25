/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.ogcapi.foundation.domain.Profile
import spock.lang.Specification

/**
 * GML encodes feature references natively, so it must drop the "rel" profile set from the property
 * transformations (otherwise the generic reduction would collapse the resolved {id, title, type}
 * object to {title, href} and discard the type discriminator the GML encoder needs for the
 * element-name suffix). Other profile sets (e.g. "val" for codelist values) must be kept.
 */
class WithoutRelProfilesSpec extends Specification {

    Profile profile(String profileSet, String id) {
        Stub(Profile) {
            getProfileSet() >> profileSet
            getId() >> id
        }
    }

    def 'rel profiles are removed, other profile sets are kept'() {
        given:
        def rel = profile('rel', 'rel-as-link')
        def val = profile('val', 'val-as-code')
        def versions = profile('versions', 'versions-as-features-unique-ids')

        when:
        def result = FeaturesFormatGml.withoutRelProfiles([rel, val, versions])

        then:
        result == [val, versions]
    }

    def 'all three rel variants are removed'() {
        given:
        def relKey = profile('rel', 'rel-as-key')
        def relUri = profile('rel', 'rel-as-uri')
        def relLink = profile('rel', 'rel-as-link')

        when:
        def result = FeaturesFormatGml.withoutRelProfiles([relKey, relUri, relLink])

        then:
        result.isEmpty()
    }

    def 'an empty profile list stays empty'() {
        expect:
        FeaturesFormatGml.withoutRelProfiles([]).isEmpty()
    }
}
