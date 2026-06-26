/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import spock.lang.Specification

// A /search response mixes features from several collections, each with its own GmlConfiguration.
// The GML context resolves the per-collection options (here: valueWrap) from the collection of the
// feature currently being encoded, so the same property path wraps differently per collection
// instead of all features inheriting the arbitrary first collection's config.
class CollectionEncodingResolutionSpec extends Specification {

    static final String PATH = 'qualitaetsangaben.herkunft.processStep.description'

    // ax_anschrift wraps the description in AX_LI_ProcessStep_OhneDatenerhebung_Description,
    // ax_anderefestlegungnachstrassenrecht in AX_LI_ProcessStep_MitDatenerhebung_Description.
    def encodings = [
            'ax_anschrift'                          : ImmutableCollectionEncodingGml.builder()
                    .valueWrap([(PATH): ['AX_LI_ProcessStep_OhneDatenerhebung_Description']])
                    .build(),
            'ax_anderefestlegungnachstrassenrecht'  : ImmutableCollectionEncodingGml.builder()
                    .valueWrap([(PATH): ['AX_LI_ProcessStep_MitDatenerhebung_Description']])
                    .build(),
            'aa_aktivitaet'                         : ImmutableCollectionEncodingGml.builder()
                    .build(),
    ]

    def 'resolves the bundle of the active collection, not the first one'() {
        when:
        def active = FeatureTransformationContextGml.resolveCurrentEncoding(
                encodings, Optional.of('ax_anschrift'))

        then:
        active.getValueWrap()[PATH] == ['AX_LI_ProcessStep_OhneDatenerhebung_Description']
    }

    def 'the same property path wraps differently per collection in one response'() {
        expect:
        FeatureTransformationContextGml.resolveCurrentEncoding(
                encodings, Optional.of(collectionId))
                .getValueWrap()[PATH] == wrapper

        where:
        collectionId                            || wrapper
        'ax_anschrift'                          || ['AX_LI_ProcessStep_OhneDatenerhebung_Description']
        'ax_anderefestlegungnachstrassenrecht'  || ['AX_LI_ProcessStep_MitDatenerhebung_Description']
        'aa_aktivitaet'                          || null
    }

    def 'returns any bundle before a feature is active (per-collection options are not read then)'() {
        when:
        def active = FeatureTransformationContextGml.resolveCurrentEncoding(
                encodings, Optional.empty())

        then:
        // the first map entry; its value is irrelevant pre-feature, this only asserts no throw
        active != null
    }

    def 'an unknown active collection falls back to any present bundle'() {
        when:
        def active = FeatureTransformationContextGml.resolveCurrentEncoding(
                encodings, Optional.of('not_in_response'))

        then:
        active != null
    }

    def 'an empty bundle map yields the empty encoding rather than throwing'() {
        when:
        def active = FeatureTransformationContextGml.resolveCurrentEncoding(
                [:], Optional.of('ax_anschrift'))

        then:
        active.getValueWrap().isEmpty()
        active.getCodelistProperties().isEmpty()
    }
}
