/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.domain

import spock.lang.Specification

class PropertyElementQualifierSpec extends Specification {

    def "originObjectType wins over the parent-stack walk"() {
        // Property was tagged with originObjectType=LI_Lineage by the schema-fragment resolver;
        // that object type maps to gmd.
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'processStep',
                'LI_Lineage',
                ['AX_Flurstueck', 'AX_DQMitDatenerhebung'],
                ['LI_Lineage': 'gmd', 'LI_ProcessStep': 'gmd']) == 'gmd:processStep'
    }

    def "originObjectType without a namespace mapping falls back to default — parent is NOT walked"() {
        // Origin set (so the resolver took ownership of the namespace decision), but it has no
        // mapping in objectTypeNamespaces. The bare name is returned even though the parent
        // stack does carry a mapped object type. This is what stops a feature-root objectType
        // (e.g. BR_UmrechnungstabelleDatei → br) from leaking into properties inherited from
        // an aaa-namespaced base fragment.
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'lebenszeitintervall',
                'AA_NREO',
                ['BR_UmrechnungstabelleDatei'],
                ['BR_UmrechnungstabelleDatei': 'br']) == 'lebenszeitintervall'
    }

    def "feature root with no origin does NOT propagate its prefix to property children"() {
        // Stack size 1 = feature root only. The root's objectType pins the namespace of the
        // feature element itself; property children stay in the default namespace.
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'lebenszeitintervall',
                null,
                ['BR_UmrechnungstabelleDatei'],
                ['BR_UmrechnungstabelleDatei': 'br']) == 'lebenszeitintervall'
    }

    def "nested object propagates its objectType to inline children when there is no origin"() {
        // ISO 19115 case: processStep is declared inline within LI_Lineage's OBJECT schema, not
        // contributed by a fragment, so originObjectType is empty. The decoder/encoder walks the
        // stack and finds LI_Lineage on top (stack size ≥ 2 because the feature root is below).
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'processStep',
                null,
                ['AX_Flurstueck', 'LI_Lineage'],
                ['LI_Lineage': 'gmd']) == 'gmd:processStep'
    }

    def "leaves name unchanged when nested parent has no namespace mapping"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'gebaeudefunktion',
                null,
                ['AX_Flurstueck', 'AX_Gebaeude'],
                ['LI_Lineage': 'gmd']) == 'gebaeudefunktion'
    }

    def "leaves name unchanged when the stack is empty"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'foo', null, [], ['LI_Lineage': 'gmd']) == 'foo'
    }

    def "preserves an explicit namespace prefix in the name"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'aaa:foo',
                'LI_Lineage',
                ['AX_Flurstueck', 'LI_Lineage'],
                ['LI_Lineage': 'gmd']) == 'aaa:foo'
    }
}
