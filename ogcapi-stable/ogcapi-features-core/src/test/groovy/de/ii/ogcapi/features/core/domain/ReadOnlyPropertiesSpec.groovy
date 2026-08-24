/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain

import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import spock.lang.Specification

class ReadOnlyPropertiesSpec extends Specification {

    def 'a property that is excluded from the receivables is read-only'() {
        given: 'a feature type with a server-maintained timestamp'
        FeatureSchema featureType = featureType(
                value('name'),
                readOnly('updated'))

        expect:
        ReadOnlyProperties.of(featureType) == ['updated'] as Set
    }

    def 'the identifier is not reported, although it is published as read-only'() {
        given:
        FeatureSchema featureType = featureType(
                id('id'),
                value('name'))

        expect: 'an identifier in a request body is ignored, not rejected'
        ReadOnlyProperties.of(featureType).isEmpty()
    }

    def 'a property that is excluded from both scopes is not read-only'() {
        given: 'a property that is neither returned nor received'
        FeatureSchema featureType = featureType(
                new ImmutableFeatureSchema.Builder()
                        .name('internal')
                        .type(SchemaBase.Type.STRING)
                        .addExcludedScopes(SchemaBase.Scope.RECEIVABLE, SchemaBase.Scope.RETURNABLE)
                        .build())

        expect: 'it is not part of the published schema at all, so it is not read-only there'
        ReadOnlyProperties.of(featureType).isEmpty()
    }

    def 'a write-only property is not read-only'() {
        given:
        FeatureSchema featureType = featureType(
                new ImmutableFeatureSchema.Builder()
                        .name('secret')
                        .type(SchemaBase.Type.STRING)
                        .addExcludedScopes(SchemaBase.Scope.RETURNABLE)
                        .build())

        expect:
        ReadOnlyProperties.of(featureType).isEmpty()
    }

    def 'the path uses the name of the property, not its source path'() {
        given: 'a property whose name differs from the column it is read from'
        FeatureSchema featureType = featureType(
                new ImmutableFeatureSchema.Builder()
                        .name('lastChange')
                        .sourcePath('changed_at')
                        .type(SchemaBase.Type.DATETIME)
                        .addExcludedScopes(SchemaBase.Scope.RECEIVABLE)
                        .build())

        expect: 'the request body uses the name, so the check has to as well'
        ReadOnlyProperties.of(featureType) == ['lastChange'] as Set
    }

    def 'a read-only property of a nested object is reported with its full path'() {
        given:
        FeatureSchema featureType = featureType(
                new ImmutableFeatureSchema.Builder()
                        .name('owner')
                        .type(SchemaBase.Type.OBJECT)
                        .putPropertyMap('name', value('name'))
                        .putPropertyMap('registered', readOnly('registered'))
                        .build())

        expect:
        ReadOnlyProperties.of(featureType) == ['owner.registered'] as Set
    }

    def 'a read-only object is reported once, not once per property in it'() {
        given:
        FeatureSchema featureType = featureType(
                new ImmutableFeatureSchema.Builder()
                        .name('audit')
                        .type(SchemaBase.Type.OBJECT)
                        .addExcludedScopes(SchemaBase.Scope.RECEIVABLE)
                        .putPropertyMap('by', value('by'))
                        .putPropertyMap('at', value('at'))
                        .build())

        expect:
        ReadOnlyProperties.of(featureType) == ['audit'] as Set
    }

    def 'a property of a read-only object is covered by the object'() {
        given:
        Set<String> readOnly = ['audit'] as Set

        expect:
        ReadOnlyProperties.contains(readOnly, 'audit')
        ReadOnlyProperties.contains(readOnly, 'audit.by')

        and: 'a name that merely starts with the same characters is not covered'
        !ReadOnlyProperties.contains(readOnly, 'auditor')
        !ReadOnlyProperties.contains(readOnly, 'name')
    }

    private static FeatureSchema featureType(FeatureSchema... properties) {
        ImmutableFeatureSchema.Builder builder = new ImmutableFeatureSchema.Builder()
                .name('buildings')
                .type(SchemaBase.Type.OBJECT)
        properties.each { builder.putPropertyMap(it.getName(), it) }
        builder.build()
    }

    private static FeatureSchema value(String name) {
        new ImmutableFeatureSchema.Builder()
                .name(name)
                .type(SchemaBase.Type.STRING)
                .build()
    }

    private static FeatureSchema readOnly(String name) {
        new ImmutableFeatureSchema.Builder()
                .name(name)
                .type(SchemaBase.Type.DATETIME)
                .addExcludedScopes(SchemaBase.Scope.RECEIVABLE)
                .build()
    }

    private static FeatureSchema id(String name) {
        new ImmutableFeatureSchema.Builder()
                .name(name)
                .type(SchemaBase.Type.STRING)
                .role(SchemaBase.Role.ID)
                .addExcludedScopes(SchemaBase.Scope.RECEIVABLE)
                .build()
    }
}
