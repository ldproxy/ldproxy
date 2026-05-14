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

    def "prefixes name with parent object type's namespace"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'processStep',
                ['AX_Flurstueck', 'AX_DQMitDatenerhebung', 'LI_Lineage'],
                ['LI_Lineage': 'gmd', 'LI_ProcessStep': 'gmd', 'LI_Source': 'gmd']) == 'gmd:processStep'
    }

    def "leaves name unchanged when parent object type has no namespace mapping"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'gebaeudefunktion',
                ['AX_Gebaeude'],
                ['LI_Lineage': 'gmd']) == 'gebaeudefunktion'
    }

    def "leaves name unchanged when the stack is empty"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'foo',
                [],
                ['LI_Lineage': 'gmd']) == 'foo'
    }

    def "preserves an explicit namespace prefix in the name"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'aaa:foo',
                ['LI_Lineage'],
                ['LI_Lineage': 'gmd']) == 'aaa:foo'
    }

    def "uses the innermost parent object type"() {
        expect:
        // LI_Source is on top; description should be in gmd
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'description',
                ['LI_Lineage', 'LI_ProcessStep', 'LI_Source'],
                ['LI_Lineage': 'gmd', 'LI_ProcessStep': 'gmd', 'LI_Source': 'gmd']) == 'gmd:description'
    }

    def "handles a null parent object type entry gracefully"() {
        expect:
        FeatureTransformationContextGml.qualifyPropertyElementName(
                'foo',
                [null],
                ['LI_Lineage': 'gmd']) == 'foo'
    }
}
