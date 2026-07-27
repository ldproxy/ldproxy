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

    // -------------------------------------------------------------------------------------------
    // xmlPaths chains: a flat property addressed through the element structure the GML encoder
    // writes for it. The chain's elements name no schema property, so they are consumed as a
    // whole and resolve to the mapped property.
    // -------------------------------------------------------------------------------------------

    def "xmlPaths chain resolves to the flat property it maps"() {
        given:
        def root = feature(scalar("lzi_beg", null, SchemaBase.Type.DATETIME))
        def xmlPaths = ["lzi_beg": ["lebenszeitintervall", "AA_Lebenszeitintervall", "beginnt"]]

        when:
        def resolved = UpdatePathResolver.resolve(
                root, ["lebenszeitintervall", "AA_Lebenszeitintervall", "beginnt"], false, true, xmlPaths)

        then:
        resolved*.getName() == ["lzi_beg"]
    }

    def "xmlPaths chains sharing leading elements resolve to their own property"() {
        given:
        def root = feature(
                scalar("lzi_beg", null, SchemaBase.Type.DATETIME),
                scalar("lzi_end", null, SchemaBase.Type.DATETIME))
        def xmlPaths = [
                "lzi_beg": ["lebenszeitintervall", "AA_Lebenszeitintervall", "beginnt"],
                "lzi_end": ["lebenszeitintervall", "AA_Lebenszeitintervall", "endet"]]

        expect:
        UpdatePathResolver.resolve(
                root, ["lebenszeitintervall", "AA_Lebenszeitintervall", "beginnt"], false, true, xmlPaths)
                *.getName() == ["lzi_beg"]
        UpdatePathResolver.resolve(
                root, ["lebenszeitintervall", "AA_Lebenszeitintervall", "endet"], false, true, xmlPaths)
                *.getName() == ["lzi_end"]
    }

    def "an xmlPaths chain with prefixed and injected-empty segments resolves on local names"() {
        given:
        // The ValueReference carries local names only, and an injected empty element (trailing '/')
        // is a sibling of a chain element — never part of a path to the value.
        def root = feature(scalar("gwt"))
        def xmlPaths = ["gwt": ["genauigkeitswert", "gmd:DQ_RelativeInternalPositionalAccuracy",
                                "gmd:result", "gmd:DQ_QuantitativeResult",
                                "gmd:valueUnit[xlink:href=urn:adv:uom:m]/", "gmd:value",
                                "gco:Record[xsi:type=gml:doubleList]"]]

        when:
        def resolved = UpdatePathResolver.resolve(
                root,
                ["genauigkeitswert", "DQ_RelativeInternalPositionalAccuracy", "result",
                 "DQ_QuantitativeResult", "value", "Record"],
                false, true, xmlPaths)

        then:
        resolved*.getName() == ["gwt"]
    }

    def "an xmlPaths chain segment with a repetition marker matches on its local name"() {
        given:
        // The '*' names the segment an object-array chain repeats from; the ValueReference
        // carries the plain element name.
        def root = feature(scalar("q2d_gst"))
        def xmlPaths = ["q2d_gst": ["qualitaetsangaben", "AX_DQPunktort", "herkunft",
                                    "gmd:LI_Lineage", "*gmd:processStep", "gmd:LI_ProcessStep",
                                    "gmd:description", "AX_Description"]]

        when:
        def resolved = UpdatePathResolver.resolve(
                root,
                ["qualitaetsangaben", "AX_DQPunktort", "herkunft", "LI_Lineage", "processStep",
                 "LI_ProcessStep", "description", "AX_Description"],
                false, true, xmlPaths)

        then:
        resolved*.getName() == ["q2d_gst"]
    }

    def "a path addressing only part of an xmlPaths chain is rejected with the full chain"() {
        given:
        def root = feature(scalar("lzi_beg", null, SchemaBase.Type.DATETIME))
        def xmlPaths = ["lzi_beg": ["lebenszeitintervall", "AA_Lebenszeitintervall", "beginnt"]]

        when:
        UpdatePathResolver.resolve(root, ["lebenszeitintervall"], false, true, xmlPaths)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("lzi_beg")
        ex.message.contains("lebenszeitintervall/AA_Lebenszeitintervall/beginnt")
    }

    def "an xmlPaths chain of a member property resolves inside its object"() {
        given:
        def root = feature(
                object("fdv", "zeigtAufExternes", "AA_Fachdatenverbindung",
                        scalar("art", "art"), scalar("nam")))
        def xmlPaths = ["fdv.nam": ["fachdatenobjekt", "AA_Fachdatenobjekt", "name"]]

        when:
        def resolved = UpdatePathResolver.resolve(
                root,
                ["zeigtAufExternes", "AA_Fachdatenverbindung", "fachdatenobjekt",
                 "AA_Fachdatenobjekt", "name"],
                true, true, xmlPaths)

        then:
        resolved*.getName() == ["fdv", "nam"]
    }

    def "a directly matching property wins over an xmlPaths chain"() {
        given:
        // The value-wrapping use of the option keys a chain by a property whose own element the wire
        // does carry; the ordinary lookup must still resolve it (the chain describes its content).
        def root = feature(scalar("dat", "dateTime", SchemaBase.Type.DATETIME))
        def xmlPaths = ["dat": ["dateTime", "gco:DateTime"]]

        when:
        def resolved = UpdatePathResolver.resolve(root, ["dateTime"], true, true, xmlPaths)

        then:
        resolved*.getName() == ["dat"]
    }
}
