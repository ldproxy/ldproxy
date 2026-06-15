/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.versioned.features.app

import com.fasterxml.jackson.databind.node.TextNode
import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.transactions.domain.CompositeId
import de.ii.ogcapi.transactions.domain.MutationStrategy
import de.ii.ogcapi.transactions.domain.TxAction
import de.ii.ogcapi.transactions.domain.TxActionType
import de.ii.ogcapi.versioned.features.domain.ImmutableVersionedFeaturesConfiguration
import de.ii.ogcapi.versioned.features.domain.VersionedFeaturesConfiguration
import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.FeatureTransactions
import de.ii.xtraplatform.features.domain.ImmutablePropertyUpdate
import de.ii.xtraplatform.features.domain.SchemaBase
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.BadRequestException
import spock.lang.Specification

import java.time.Instant
import java.util.Optional

class VersionedMutationStrategySpec extends Specification {

    VersionedMutationStrategy subject = new VersionedMutationStrategy()

    def 'not enabled when VERSIONED_FEATURES is not configured on the collection'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [])])

        expect:
        !subject.isEnabledForApi(apiData, 'a')
    }

    def 'not enabled when VERSIONED_FEATURES is configured but disabled'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(false)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])])

        expect:
        !subject.isEnabledForApi(apiData, 'a')
    }

    def 'enabled when VERSIONED_FEATURES is enabled on the collection'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .build()])])

        expect:
        subject.isEnabledForApi(apiData, 'a')
    }

    def 'priority is higher than the default plain strategy'() {
        expect:
        subject.priority() > 0
    }

    def 'retires on Replace (so the executor takes the retire-and-insert path)'() {
        expect:
        subject.retiresOnReplace()
    }

    def 'retires on Delete (so the executor takes the retire-only path)'() {
        expect:
        subject.retiresOnDelete()
    }

    def 'requires the versioned-insert pre-flight check'() {
        expect:
        subject.requiresInsertPreflight()
    }

    def 'splitCompositeId: passthrough when no pattern is configured'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])

        when:
        CompositeId result = subject.splitCompositeId(apiData, 'a', 'DENW36AL10000Ehc')

        then:
        result.canonical() == 'DENW36AL10000Ehc'
        result.expectedStart().isEmpty()
    }

    def 'splitCompositeId: NAS-style 14-digit suffix → canonical + parsed Instant'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .mutationTime(VersionedFeaturesConfiguration.MutationTime.SERVER)
                        .compositeIdPattern('^(?<id>.+?)(?<start>\\d{8}T\\d{6}Z)$')
                        .build()])])

        when:
        CompositeId result =
                subject.splitCompositeId(apiData, 'a', 'DEHE86202002BHuV20240215T121156Z')

        then:
        result.canonical() == 'DEHE86202002BHuV'
        result.expectedStart().get() == Instant.parse('2024-02-15T12:11:56Z')
    }

    def 'splitCompositeId: composite-shaped id that does not match the pattern → passthrough'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .mutationTime(VersionedFeaturesConfiguration.MutationTime.SERVER)
                        .compositeIdPattern('^(?<id>.+?)(?<start>\\d{8}T\\d{6}Z)$')
                        .build()])])

        when:
        CompositeId result =
                subject.splitCompositeId(apiData, 'a', 'plain_objid_no_suffix')

        then:
        result.canonical() == 'plain_objid_no_suffix'
        result.expectedStart().isEmpty()
    }

    def 'extractPrimaryIntervalStart: returns empty for an XML body without the role property'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.CLIENT, [])
        // Use the lzi/beg schema fixture (PRIMARY_INTERVAL_START role on `beg`, alias `beginnt`).
        FeatureSchema schema = schemaWithLziBegAlias()

        when:
        Optional<Instant> result =
                subject.extractPrimaryIntervalStart(
                        apiData,
                        schema,
                        MediaType.APPLICATION_XML_TYPE,
                        '<AP_PTO/>'.getBytes('UTF-8'))

        then:
        result.isEmpty()
    }

    def 'extractPrimaryIntervalStart: parses the lzi/beg value from a wfs:Replace body'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.CLIENT, [])
        FeatureSchema schema = schemaWithLziBegAlias()
        // Mirrors the inner <AP_PTO>...</AP_PTO> element WfsTransactionParser passes as
        // TxReplace.getFeature() bytes for wfs:Replace action.
        String xml = '''<AP_PTO xmlns="http://www.adv-online.de/namespaces/adv/gid/7.1"
                                xmlns:gml="http://www.opengis.net/gml/3.2"
                                gml:id="DEABCDEF1234567820251021T052449Z">
              <lebenszeitintervall>
                <AA_Lebenszeitintervall>
                  <beginnt>2025-10-21T05:46:11Z</beginnt>
                  <endet>2025-10-21T05:46:20Z</endet>
                </AA_Lebenszeitintervall>
              </lebenszeitintervall>
            </AP_PTO>'''

        when:
        Optional<Instant> result =
                subject.extractPrimaryIntervalStart(
                        apiData,
                        schema,
                        MediaType.APPLICATION_XML_TYPE,
                        xml.getBytes('UTF-8'))

        then:
        result.get() == Instant.parse('2025-10-21T05:46:11Z')
    }

    def 'extractPrimaryIntervalStart: parses the lzi/beg value from a JSON body'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.CLIENT, [])
        FeatureSchema schema = schemaWithLziBegAlias()
        String json = '{"id":"DEABCDEF12345678","lebenszeitintervall":{"beginnt":"2025-10-21T05:46:11Z"}}'

        when:
        Optional<Instant> result =
                subject.extractPrimaryIntervalStart(
                        apiData,
                        schema,
                        MediaType.APPLICATION_JSON_TYPE,
                        json.getBytes('UTF-8'))

        then:
        result.get() == Instant.parse('2025-10-21T05:46:11Z')
    }

    private FeatureSchema schemaWithLziBegAlias() {
        FeatureSchema beg = Stub(FeatureSchema)
        beg.getName() >> 'beg'
        beg.getAlias() >> Optional.of('beginnt')
        beg.getRole() >> Optional.of(SchemaBase.Role.PRIMARY_INTERVAL_START)
        beg.getProperties() >> []
        FeatureSchema end = Stub(FeatureSchema)
        end.getName() >> 'end'
        end.getAlias() >> Optional.of('endet')
        end.getRole() >> Optional.of(SchemaBase.Role.PRIMARY_INTERVAL_END)
        end.getProperties() >> []
        FeatureSchema lzi = Stub(FeatureSchema)
        lzi.getName() >> 'lzi'
        lzi.getAlias() >> Optional.of('lebenszeitintervall')
        lzi.getRole() >> Optional.empty()
        lzi.getProperties() >> [beg, end]
        FeatureSchema root = Stub(FeatureSchema)
        root.getName() >> 'ap_pto'
        root.getProperties() >> [lzi]
        return root
    }

    def 'splitCompositeId: malformed timestamp suffix → 400'() {
        given:
        // Pattern that captures non-digit chars under the `start` group so the timestamp parse
        // will fail.
        OgcApiDataV2 apiData = api([collection('a', [
                new ImmutableVersionedFeaturesConfiguration.Builder()
                        .enabled(true)
                        .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                        .mutationTime(VersionedFeaturesConfiguration.MutationTime.SERVER)
                        .compositeIdPattern('^(?<id>.+?)_(?<start>.+)$')
                        .build()])])

        when:
        subject.splitCompositeId(apiData, 'a', 'objid_NOT_A_TIMESTAMP')

        then:
        thrown(BadRequestException)
    }

    def 'disallows same-feature chains in server mode (one shared mutationTimestamp would backdate)'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }

        expect:
        subject.disallowsSameFeatureChain(apiData, action)
    }

    def 'allows same-feature chains in client mode (per-action timestamps differ)'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.CLIENT, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }

        expect:
        !subject.disallowsSameFeatureChain(apiData, action)
    }

    def 'no VERSIONED_FEATURES config: allows chains (defensive fallback)'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [])])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }

        expect:
        !subject.disallowsSameFeatureChain(apiData, action)
    }

    def 'building-block configuration type is VersionedFeaturesConfiguration'() {
        expect:
        subject.getBuildingBlockConfigurationType() == VersionedFeaturesConfiguration
    }

    def 'server mutationTime: returns the scope timestamp regardless of header'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.SERVER)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty()) == scope
        subject.resolveMutationTimestamp(
                apiData, action, scope, Optional.of(Instant.parse('2030-01-01T00:00:00Z'))) == scope
    }

    def 'client mutationTime + Delete: returns the header value when supplied'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) {
            getCollectionId() >> 'a'
            getType() >> TxActionType.DELETE
        }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')
        Instant header = Instant.parse('2025-01-01T00:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.of(header)) == header
    }

    def 'client mutationTime + Delete: 400 when header is missing (no body to extract from)'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) {
            getCollectionId() >> 'a'
            getType() >> TxActionType.DELETE
        }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        when:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty())

        then:
        thrown(BadRequestException)
    }

    def 'client mutationTime + Insert: returns placeholder scopeTimestamp when no header (body has the start value)'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) {
            getCollectionId() >> 'a'
            getType() >> TxActionType.INSERT
        }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty()) == scope
    }

    def 'client mutationTime + Insert: header takes precedence over placeholder when both possible'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) {
            getCollectionId() >> 'a'
            getType() >> TxActionType.INSERT
        }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')
        Instant header = Instant.parse('2025-01-01T00:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.of(header)) == header
    }

    def 'server mode: insertRoleOverrides forces start = ts and end = null'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.SERVER)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant ts = Instant.parse('2026-06-06T10:00:00Z')

        when:
        Map overrides = subject.insertRoleOverrides(apiData, action, ts, Optional.empty())

        then:
        overrides.size() == 2
        overrides.get(SchemaBase.Role.PRIMARY_INTERVAL_START) == ts.toString()
        overrides.containsKey(SchemaBase.Role.PRIMARY_INTERVAL_END)
        overrides.get(SchemaBase.Role.PRIMARY_INTERVAL_END) == null
    }

    def 'server mode + retire-and-insert: PREDECESSOR_INTERVAL_START set from the retired version start'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.SERVER)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant ts = Instant.parse('2026-06-06T10:00:00Z')

        when:
        Map overrides = subject.insertRoleOverrides(apiData, action, ts, Optional.of('2025-01-01T00:00:00Z'))

        then:
        overrides.size() == 3
        overrides.get(SchemaBase.Role.PRIMARY_INTERVAL_START) == ts.toString()
        overrides.get(SchemaBase.Role.PRIMARY_INTERVAL_END) == null
        overrides.get(SchemaBase.Role.PREDECESSOR_INTERVAL_START) == '2025-01-01T00:00:00Z'
    }

    def 'client mode: insertRoleOverrides is empty (body extraction lands later)'() {
        given:
        OgcApiDataV2 apiData = apiWithMode(VersionedFeaturesConfiguration.MutationTime.CLIENT)
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }

        expect:
        subject.insertRoleOverrides(apiData, action, Instant.parse('2026-06-06T10:00:00Z'), Optional.empty()).isEmpty()
    }

    def 'no mutationTime configured: insertRoleOverrides is empty'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                .build()])])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }

        expect:
        subject.insertRoleOverrides(apiData, action, Instant.parse('2026-06-06T10:00:00Z'), Optional.empty()).isEmpty()
    }

    def 'chooseUpdateMode: rejects updates to the primary-interval-start role'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [propertyUpdate(['lzi', 'beg'], '2025-01-01T00:00:00Z')]

        when:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z'))

        then:
        thrown(BadRequestException)
    }

    def 'chooseUpdateMode: rejects clearing the primary-interval-end role'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [propertyDelete(['lzi', 'end'])]

        when:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z'))

        then:
        thrown(BadRequestException)
    }

    def 'chooseUpdateMode: set-end-alone returns RETIRE_IN_PLACE'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [propertyUpdate(['lzi', 'end'], '2027-01-01T00:00:00Z')]

        expect:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z')) ==
                MutationStrategy.UpdateMode.RETIRE_IN_PLACE
    }

    def 'chooseUpdateMode: set-end-with-other rejected when no whitelist'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [
                propertyUpdate(['lzi', 'end'], '2027-01-01T00:00:00Z'),
                propertyUpdate(['anl'], 'AX_Reason')
        ]

        when:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z'))

        then:
        thrown(BadRequestException)
    }

    def 'chooseUpdateMode: set-end-with-other accepted when sibling is on the retireWithModifications whitelist'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, ['anl'])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [
                propertyUpdate(['lzi', 'end'], '2027-01-01T00:00:00Z'),
                propertyUpdate(['anl'], 'AX_Reason')
        ]

        expect:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z')) ==
                MutationStrategy.UpdateMode.RETIRE_IN_PLACE
    }

    def 'chooseUpdateMode: set-end-with-other still rejected when sibling is off-whitelist even with anl listed'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, ['anl'])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [
                propertyUpdate(['lzi', 'end'], '2027-01-01T00:00:00Z'),
                propertyUpdate(['other'], 'x')
        ]

        when:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z'))

        then:
        thrown(BadRequestException)
    }

    def 'chooseUpdateMode: regular property update returns CLONE_AND_PATCH'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        List updates = [propertyUpdate(['other'], 'x')]

        expect:
        subject.chooseUpdateMode(apiData, schema(), action, updates, Instant.parse('2026-06-06T10:00:00Z')) ==
                MutationStrategy.UpdateMode.CLONE_AND_PATCH
    }

    def 'chooseUpdateMode: empty updates rejected'() {
        given:
        OgcApiDataV2 apiData = apiWith(VersionedFeaturesConfiguration.MutationTime.SERVER, [])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }

        when:
        subject.chooseUpdateMode(apiData, schema(), action, [], Instant.parse('2026-06-06T10:00:00Z'))

        then:
        thrown(BadRequestException)
    }

    private static OgcApiDataV2 apiWith(VersionedFeaturesConfiguration.MutationTime mode, List<String> retireWith) {
        api([collection('a', [new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                .mutationTime(mode)
                .retireWithModifications(retireWith)
                .build()])])
    }

    private FeatureSchema schema() {
        // Hand-stub the tree to avoid the bean-introspection cost of ImmutableFeatureSchema —
        // which Groovy's `==` evaluator triggers on argument printing and which transitively
        // pulls in geometry types that aren't on this module's test classpath.
        FeatureSchema beg = Stub(FeatureSchema)
        beg.getName() >> 'beg'
        beg.getRole() >> Optional.of(SchemaBase.Role.PRIMARY_INTERVAL_START)
        beg.getProperties() >> []
        FeatureSchema end = Stub(FeatureSchema)
        end.getName() >> 'end'
        end.getRole() >> Optional.of(SchemaBase.Role.PRIMARY_INTERVAL_END)
        end.getProperties() >> []
        FeatureSchema lzi = Stub(FeatureSchema)
        lzi.getName() >> 'lzi'
        lzi.getRole() >> Optional.empty()
        lzi.getProperties() >> [beg, end]
        FeatureSchema anl = Stub(FeatureSchema)
        anl.getName() >> 'anl'
        anl.getRole() >> Optional.empty()
        anl.getProperties() >> []
        FeatureSchema other = Stub(FeatureSchema)
        other.getName() >> 'other'
        other.getRole() >> Optional.empty()
        other.getProperties() >> []
        FeatureSchema root = Stub(FeatureSchema)
        root.getName() >> 'a'
        root.getProperties() >> [lzi, anl, other]
        return root
    }

    private static FeatureTransactions.PropertyUpdate propertyUpdate(List<String> path, String value) {
        new ImmutablePropertyUpdate.Builder()
                .path(path)
                .value(Optional.of(new TextNode(value)))
                .build()
    }

    private static FeatureTransactions.PropertyUpdate propertyDelete(List<String> path) {
        new ImmutablePropertyUpdate.Builder()
                .path(path)
                .value(Optional.empty())
                .build()
    }

    def 'no VERSIONED_FEATURES config on collection: returns the scope timestamp (defensive fallback)'() {
        given:
        OgcApiDataV2 apiData = api([collection('a', [])])
        TxAction action = Stub(TxAction) { getCollectionId() >> 'a' }
        Instant scope = Instant.parse('2026-06-06T10:00:00Z')

        expect:
        subject.resolveMutationTimestamp(apiData, action, scope, Optional.empty()) == scope
    }

    private static OgcApiDataV2 apiWithMode(VersionedFeaturesConfiguration.MutationTime mode) {
        api([collection('a', [new ImmutableVersionedFeaturesConfiguration.Builder()
                .enabled(true)
                .timeAxis(VersionedFeaturesConfiguration.TimeAxis.VALIDITY_TIME)
                .mutationTime(mode)
                .build()])])
    }

    private static OgcApiDataV2 api(List collections) {
        ImmutableOgcApiDataV2.Builder builder = new ImmutableOgcApiDataV2.Builder().id('api')
        collections.each { builder.putCollections(it.id, it) }
        builder.build()
    }

    private static ImmutableFeatureTypeConfigurationOgcApi collection(String id, List extensions) {
        new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                .id(id)
                .label(id)
                .extensions(extensions)
                .build()
    }
}
