/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.xtraplatform.features.domain.FeatureSchema
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema
import de.ii.xtraplatform.features.domain.SchemaBase
import spock.lang.Specification

class UpdatePathResolverSpec extends Specification {

    static FeatureSchema scalar(String name, String alias = null, SchemaBase.Type type = SchemaBase.Type.STRING) {
        def b = new ImmutableFeatureSchema.Builder()
                .name(name)
                .type(type)
                .sourcePath(name)
        if (alias != null) {
            b.alias(alias)
        }
        return b.build()
    }

    static FeatureSchema object(String name, String alias, String objectType, FeatureSchema... children) {
        def b = new ImmutableFeatureSchema.Builder()
                .name(name)
                .type(SchemaBase.Type.OBJECT)
        if (alias != null) b.alias(alias)
        if (objectType != null) b.objectType(objectType)
        children.each { b.putPropertyMap(it.getName(), it) }
        return b.build()
    }

    static FeatureSchema feature(FeatureSchema... properties) {
        def b = new ImmutableFeatureSchema.Builder()
                .name("AA_Meilenstein")
                .type(SchemaBase.Type.OBJECT)
                .sourcePath("/meilenstein")
        properties.each { b.putPropertyMap(it.getName(), it) }
        return b.build()
    }

    def "flat property matched by id when useAlias=false"() {
        given:
        def root = feature(scalar("amtlicheFlaeche", "afl"))

        when:
        def resolved = UpdatePathResolver.resolve(root, ["amtlicheFlaeche"], false)

        then:
        resolved*.getName() == ["amtlicheFlaeche"]
        UpdatePathResolver.toOutputPath(resolved, false) == ["amtlicheFlaeche"]
        UpdatePathResolver.toOutputPath(resolved, true) == ["afl"]
    }

    def "flat property matched by alias when useAlias=true"() {
        given:
        def root = feature(scalar("amtlicheFlaeche", "afl"))

        when:
        def resolved = UpdatePathResolver.resolve(root, ["afl"], true)

        then:
        resolved*.getName() == ["amtlicheFlaeche"]
    }

    def "id and alias are not interchangeable; mismatched form is rejected"() {
        given:
        def root = feature(scalar("amtlicheFlaeche", "afl"))

        when:
        UpdatePathResolver.resolve(root, ["afl"], false)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("'afl'")
        ex.message.contains("AA_Meilenstein")
    }

    def "WFS XPath input: object-type step required and validated"() {
        given:
        def root = feature(
                object("lebenszeitintervall", null, "AA_Lebenszeitintervall",
                        scalar("endet")))

        when: 'path includes the object-type step'
        def resolved = UpdatePathResolver.resolve(
                root, ["lebenszeitintervall", "AA_Lebenszeitintervall", "endet"], false, true)

        then:
        resolved*.getName() == ["lebenszeitintervall", "endet"]

        when: 'path omits the object-type step under WFS rules'
        UpdatePathResolver.resolve(root, ["lebenszeitintervall", "endet"], false, true)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("'AA_Lebenszeitintervall'")
        ex.message.contains("'endet'")
    }

    def "ldproxy-canonical input: no object-type step expected"() {
        given:
        def root = feature(
                object("lebenszeitintervall", null, "AA_Lebenszeitintervall",
                        scalar("endet")))

        when:
        def resolved = UpdatePathResolver.resolve(
                root, ["lebenszeitintervall", "endet"], false, false)

        then:
        resolved*.getName() == ["lebenszeitintervall", "endet"]
    }

    def "WFS XPath input: object-type mismatch is rejected"() {
        given:
        def root = feature(
                object("lebenszeitintervall", null, "AA_Lebenszeitintervall",
                        scalar("endet")))

        when:
        UpdatePathResolver.resolve(
                root, ["lebenszeitintervall", "WrongType", "endet"], false, true)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("AA_Lebenszeitintervall")
        ex.message.contains("WrongType")
    }

    def "useAlias applied per segment, WFS XPath form"() {
        given:
        def root = feature(
                object("lebenszeitintervall", "lzi", "AA_Lebenszeitintervall",
                        scalar("endet", "end")))

        when:
        def resolved = UpdatePathResolver.resolve(
                root, ["lzi", "AA_Lebenszeitintervall", "end"], true, true)

        then:
        resolved*.getName() == ["lebenszeitintervall", "endet"]
        UpdatePathResolver.toOutputPath(resolved, true) == ["lzi", "end"]
        UpdatePathResolver.toOutputPath(resolved, false) == ["lebenszeitintervall", "endet"]
    }

    def "unknown segment is rejected with the parent's name"() {
        given:
        def root = feature(scalar("amtlicheFlaeche"))

        when:
        UpdatePathResolver.resolve(root, ["doesNotExist"], false)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("'doesNotExist'")
        ex.message.contains("AA_Meilenstein")
    }

    def "empty path is rejected"() {
        given:
        def root = feature(scalar("amtlicheFlaeche"))

        when:
        UpdatePathResolver.resolve(root, [], false)

        then:
        thrown(IllegalArgumentException)
    }
}
