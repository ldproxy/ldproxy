/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gml.app

import de.ii.ogcapi.features.gml.domain.ImmutableGmlConfiguration
import de.ii.ogcapi.features.gml.domain.ImmutableSrsNameMapping
import de.ii.ogcapi.features.gml.domain.ImmutableUomMapping
import de.ii.ogcapi.features.gml.domain.ImmutableVariableName
import de.ii.xtraplatform.crs.domain.EpsgCrs
import spock.lang.Specification

/**
 * Verifies that {@link FeaturesFormatGml#toInputProfile} surfaces every reversible
 * GmlConfiguration option to the decoder's input profile, including the two list-to-map
 * conversions ({@code srsNameMappings}, {@code uomMappings}) and the per-entry direction
 * reversal of {@code variableObjectElementNames} (encoder: source value → wire qualified name;
 * decoder profile: wire qualified name → source value). The decoder's runtime behaviour for
 * each option is exercised in {@code FeatureTokenDecoderGmlSpec}; this spec only locks
 * the GmlConfiguration → input-profile mapping so a regression there does not silently turn
 * a configured option into a no-op.
 */
class ToInputProfileSpec extends Specification {

    def 'every reversible GmlConfiguration option propagates to the input profile'() {
        given:
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .useAlias(true)
                .gmlIdPrefix('urn:adv:oid:')
                .featureRefTemplate('urn:adv:oid:{{value}}')
                .codelistUriTemplate('https://registry.gdi-de.org/codelist/de.adv-online.gid/{{codelistId}}/{{value}}')
                .defaultNamespace('aaa')
                .putApplicationNamespaces('aaa', 'http://www.adv-online.de/namespaces/adv/gid/7.1')
                .putApplicationNamespaces('gmd', 'http://www.isotc211.org/2005/gmd')
                .putObjectTypeNamespaces('LI_Lineage', 'gmd')
                .putObjectTypeNamespaces('CI_ResponsibleParty', 'gmd')
                .putCodelistProperties('anl', 'AA_Anlassart')
                .putCodelistProperties('som', 'AA_WeitereModellart')
                .putValueWrap('lebenszeitintervall', ['AA_Lebenszeitintervall', 'beginnt'])
                .putValueWrap('qag.dpl.prs.des', ['AX_LI_ProcessStep_MitDatenerhebung_Description'])
                .addXmlAttributes('mat.som.codeListValue')
                .addSrsNameMappings(new ImmutableSrsNameMapping.Builder()
                        .crs(EpsgCrs.of(25832))
                        .value('urn:adv:crs:ETRS89_UTM32')
                        .build())
                .addSrsNameMappings(new ImmutableSrsNameMapping.Builder()
                        .crs(EpsgCrs.of(4326))
                        .value('urn:adv:crs:WGS84_Lat-Lon')
                        .build())
                .addUomMappings(new ImmutableUomMapping.Builder()
                        .uom('m')
                        .value('urn:adv:uom:m')
                        .build())
                .addUomMappings(new ImmutableUomMapping.Builder()
                        .uom('grad')
                        .value('urn:adv:uom:grad')
                        .build())
                // Encoder direction: source value → wire qualified name. The decoder profile
                // reverses each entry's mapping so wire-form lookups go in O(1).
                .putVariableObjectElementNames('AX_DQOhneDatenerhebung', new ImmutableVariableName.Builder()
                        .property('herkunft.processStep.description')
                        .putMapping('Erhebung', 'aaa:AX_LI_ProcessStep_OhneDatenerhebung_Erhebung')
                        .putMapping('Punktort', 'aaa:AX_LI_ProcessStep_OhneDatenerhebung_Punktort')
                        .build())
                .build()

        when:
        def profile = FeaturesFormatGml.toInputProfile(config)

        then:
        profile.useAlias
        profile.gmlIdPrefix == 'urn:adv:oid:'
        profile.featureRefTemplate == 'urn:adv:oid:{{value}}'
        profile.codelistUriTemplate == 'https://registry.gdi-de.org/codelist/de.adv-online.gid/{{codelistId}}/{{value}}'
        profile.defaultNamespace == 'aaa'
        profile.applicationNamespaces == [
                aaa: 'http://www.adv-online.de/namespaces/adv/gid/7.1',
                gmd: 'http://www.isotc211.org/2005/gmd'
        ]
        profile.objectTypeNamespaces == [LI_Lineage: 'gmd', CI_ResponsibleParty: 'gmd']
        profile.codelistProperties == [anl: 'AA_Anlassart', som: 'AA_WeitereModellart']
        profile.valueWrap == [
                lebenszeitintervall: ['AA_Lebenszeitintervall', 'beginnt'],
                'qag.dpl.prs.des': ['AX_LI_ProcessStep_MitDatenerhebung_Description']
        ]
        profile.xmlAttributes == ['mat.som.codeListValue']

        and: 'list-of-pairs srsNameMappings flatten to a wire→CRS map'
        profile.srsNameMappings == [
                'urn:adv:crs:ETRS89_UTM32': EpsgCrs.of(25832),
                'urn:adv:crs:WGS84_Lat-Lon': EpsgCrs.of(4326)
        ]

        and: 'list-of-pairs uomMappings flatten to a wire→canonical UoM map'
        profile.uomMappings == ['m': 'urn:adv:uom:m', 'grad': 'urn:adv:uom:grad']

        and: 'variableObjectElementNames keeps property direction, reverses mapping direction'
        profile.variableObjectElementNames.containsKey('AX_DQOhneDatenerhebung')
        def variable = profile.variableObjectElementNames.get('AX_DQOhneDatenerhebung')
        variable.property == 'herkunft.processStep.description'
        variable.mapping == [
                'aaa:AX_LI_ProcessStep_OhneDatenerhebung_Erhebung': 'Erhebung',
                'aaa:AX_LI_ProcessStep_OhneDatenerhebung_Punktort': 'Punktort'
        ]
    }

    def 'null-valued string options default to empty strings rather than NPE'() {
        // featureRefTemplate / codelistUriTemplate / gmlIdPrefix / defaultNamespace come from the
        // ldproxy config as nullable; the input profile is a value type with non-null string
        // fields, so toInputProfile coalesces nulls to "" — a regression here would either NPE
        // at construction or silently treat the option as "always match", both wrong.
        given:
        def config = new ImmutableGmlConfiguration.Builder()
                .enabled(true)
                .build()

        when:
        def profile = FeaturesFormatGml.toInputProfile(config)

        then:
        profile.gmlIdPrefix == ''
        profile.featureRefTemplate == ''
        profile.codelistUriTemplate == ''
        profile.defaultNamespace == ''
        profile.srsNameMappings.isEmpty()
        profile.uomMappings.isEmpty()
        profile.variableObjectElementNames.isEmpty()
        profile.codelistProperties.isEmpty()
        profile.valueWrap.isEmpty()
        profile.xmlAttributes.isEmpty()
        profile.applicationNamespaces.isEmpty()
        profile.objectTypeNamespaces.isEmpty()
        !profile.useAlias
    }
}
