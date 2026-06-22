/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app


import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration
import spock.lang.Specification

/**
 * Locks the {@code applicationNamespaces} → effective namespace prefix map merge that
 * {@link FeaturesFormatGml#getFeatureEncoder} feeds into {@code FeatureTransformationContextGml}.
 *
 * <p>The {@code STANDARD_NAMESPACES} prefixes (gml, xlink, xsi, sf, wfs) are reserved — the GML
 * writers reference them internally — so they always win. An application namespace that reuses a
 * reserved prefix is dropped (never overrides the standard binding), which also prevents the
 * duplicate-key failure an unfiltered merge of both maps would raise (the
 * {@code Multiple entries with same key: wfs=...} 400 on {@code ?f=xml}). A reserved prefix bound
 * to a different namespace is a genuine misconfiguration; reusing it for the same namespace is a
 * harmless redundancy and dropped silently.
 */
class MergeNamespacesSpec extends Specification {

    static final String WFS_NS = 'http://www.opengis.net/wfs/2.0'

    static config(Map<String, String> applicationNamespaces) {
        new ImmutableGmlConfiguration.Builder()
                .applicationNamespaces(applicationNamespaces)
                .build()
    }

    def 'no application namespaces: result is exactly the standard set'() {
        when:
        def merged = FeaturesFormatGml.mergeNamespaces(config([:]))

        then: 'every reserved prefix is present and nothing else'
        merged['wfs'] == WFS_NS
        merged['gml'] == 'http://www.opengis.net/gml/3.2'
        merged['xlink'] == 'http://www.w3.org/1999/xlink'
        merged['xsi'] == 'http://www.w3.org/2001/XMLSchema-instance'
        merged['sf'] == 'http://www.opengis.net/ogcapi-features-1/1.0/sf'
        merged.keySet() == ['gml', 'gml21', 'gml31', 'xlink', 'xml', 'xsi', 'sf', 'wfs'] as Set
    }

    def 'non-reserved application prefix is kept alongside the standard set'() {
        when:
        def merged = FeaturesFormatGml.mergeNamespaces(config(['adv': 'http://www.adv-online.de/namespaces/adv/gid/7.1']))

        then:
        merged['adv'] == 'http://www.adv-online.de/namespaces/adv/gid/7.1'
        merged['wfs'] == WFS_NS
    }

    def 'reserved prefix bound to the SAME namespace is dropped silently and standard still wins'() {
        when: 'the wfs prefix is redeclared with the identical URI (the duplicate-key repro)'
        def merged = FeaturesFormatGml.mergeNamespaces(config(['wfs': WFS_NS]))

        then: 'no duplicate-key failure; the standard binding is present'
        merged['wfs'] == WFS_NS
    }

    def 'reserved prefix bound to a DIFFERENT namespace is dropped; standard binding wins'() {
        when: 'an application tries to rebind a reserved prefix'
        def merged = FeaturesFormatGml.mergeNamespaces(config(['wfs': 'http://example.com/not-wfs']))

        then: 'the standard binding is kept, the application one ignored'
        merged['wfs'] == WFS_NS
    }
}
