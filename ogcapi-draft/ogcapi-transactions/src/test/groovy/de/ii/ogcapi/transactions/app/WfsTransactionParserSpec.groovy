/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.transactions.domain.TxActionType
import de.ii.ogcapi.transactions.domain.TxDelete
import de.ii.ogcapi.transactions.domain.TxInsert
import de.ii.ogcapi.transactions.domain.TxReplace
import de.ii.ogcapi.transactions.domain.TxSemantic
import de.ii.ogcapi.transactions.domain.TxUpdate
import de.ii.xtraplatform.cql.domain.In
import jakarta.ws.rs.core.MediaType
import spock.lang.Shared
import spock.lang.Specification

/**
 * Locks the streaming WFS 2.0 transaction parser:
 *
 * <ul>
 *   <li>root-element validation and end-to-end iteration over multiple actions
 *   <li>{@code wfs:Insert} emits one TxInsert per consecutive-same-collection group of
 *       children, with every feature pre-buffered and exposed via its items() iterator
 *   <li>{@code wfs:Replace} / {@code wfs:Update} / {@code wfs:Delete} parse the
 *       collection name, fes:ResourceId/@rid filter, and per-property modify/delete lists
 *   <li>only {@code fes:ResourceId/@rid} filters are accepted (anything else is a hard error)
 * </ul>
 *
 * The XML reader is configured to reject DTDs / external entities; this spec doesn't assert that
 * directly (it's a factory property, not behavioural), but doing so here would couple the test to
 * the parser's internal factory configuration rather than its parsing contract.
 */
class WfsTransactionParserSpec extends Specification {

    @Shared
    WfsTransactionParser parser

    def setupSpec() {
        parser = new WfsTransactionParser()
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes('UTF-8'))
    }

    private static MediaType XML = MediaType.valueOf('application/xml')

    def 'canParse accepts application/xml and application/gml+xml; rejects others'() {
        expect:
        parser.canParse(MediaType.valueOf('application/xml'))
        parser.canParse(MediaType.valueOf('application/gml+xml'))
        !parser.canParse(MediaType.APPLICATION_JSON_TYPE)
        !parser.canParse(null)
    }

    def 'semantic is always ATOMIC (WFS has no batch semantics)'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"/>'''

        when:
        def tx = parser.parse(bytes(body), XML)

        then:
        tx.semantic == TxSemantic.ATOMIC

        cleanup:
        tx.close()
    }

    def 'rejects body whose root is not wfs:Transaction'() {
        given:
        def body = '<wfs:OtherRoot xmlns:wfs="http://www.opengis.net/wfs/2.0"/>'

        when:
        parser.parse(bytes(body), XML)

        then:
        thrown(IllegalArgumentException)
    }

    def 'wfs:Insert bundles all same-collection children into one TxInsert, items in document order'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Insert handle="ins-1">
                <ax:AX_Buildings><ax:id>1</ax:id></ax:AX_Buildings>
                <ax:AX_Buildings><ax:id>2</ax:id></ax:AX_Buildings>
              </wfs:Insert>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        def it = tx.actions()
        def insert = (TxInsert) it.next()
        def items = insert.items()
        def firstItem = items.next()
        def secondItem = items.next()
        def firstBytes = firstItem.payload().readAllBytes()
        def secondBytes = secondItem.payload().readAllBytes()

        then: 'a single TxInsert covers the whole wfs:Insert'
        insert.collectionId == 'AX_Buildings'
        insert.actionId.orElse(null) == 'ins-1'
        insert.mediaType.toString() == 'application/gml+xml'

        and: 'every feature child is exposed in document order via items()'
        new String(firstBytes, 'UTF-8').contains('<ax:id>1</ax:id>')
        new String(secondBytes, 'UTF-8').contains('<ax:id>2</ax:id>')
        !items.hasNext()

        and:
        !it.hasNext()

        cleanup:
        tx.close()
    }

    def 'wfs:Insert feature payload carries ancestor namespace declarations'() {
        // given: gml: prefix declared only on the wfs:Transaction root
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:gml="http://www.opengis.net/gml/3.2"
                             xmlns:ax="http://example.org/ax">
              <wfs:Insert>
                <ax:AX_Buildings gml:id="b-1"><ax:id>1</ax:id></ax:AX_Buildings>
              </wfs:Insert>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        def insert = (TxInsert) tx.actions().next()
        def payload = new String(insert.items().next().payload().readAllBytes(), 'UTF-8')

        then: 'the gml: prefix bound on the ancestor is preserved on the feature element'
        // re-parse the payload as a standalone document - prefix must resolve
        def f = javax.xml.stream.XMLInputFactory.newInstance()
        f.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, Boolean.FALSE)
        def r = f.createXMLEventReader(new ByteArrayInputStream(payload.getBytes('UTF-8')))
        def root = null
        while (r.hasNext()) {
            def ev = r.nextEvent()
            if (ev.isStartElement()) { root = ev.asStartElement(); break }
        }
        root != null
        root.name.localPart == 'AX_Buildings'
        root.name.namespaceURI == 'http://example.org/ax'
        // gml:id attribute now resolves to its namespace
        def gmlIdAttr = root.getAttributeByName(
            new javax.xml.namespace.QName('http://www.opengis.net/gml/3.2', 'id'))
        gmlIdAttr != null
        gmlIdAttr.value == 'b-1'

        cleanup:
        tx.close()
    }

    def 'wfs:Replace parses feature payload and the fes:ResourceId filter'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:fes="http://www.opengis.net/fes/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Replace handle="r1">
                <ax:AX_Buildings><ax:id>42</ax:id><ax:height>9</ax:height></ax:AX_Buildings>
                <fes:Filter><fes:ResourceId rid="42"/></fes:Filter>
              </wfs:Replace>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        def action = (TxReplace) tx.actions().next()
        def featureText = new String(action.feature, 'UTF-8')

        then:
        action.type == TxActionType.REPLACE
        action.collectionId == 'AX_Buildings'
        action.actionId.orElse(null) == 'r1'
        action.mediaType.toString() == 'application/gml+xml'
        featureText.contains('<ax:id>42</ax:id>')
        action.filter.isPresent()
        action.filter.get() instanceof In

        cleanup:
        tx.close()
    }

    def 'wfs:Update collects Property name/value pairs as modify; missing Value -> delete'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:fes="http://www.opengis.net/fes/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Update typeName="ax:AX_Buildings" handle="u1">
                <wfs:Property>
                  <wfs:ValueReference>height</wfs:ValueReference>
                  <wfs:Value>12.5</wfs:Value>
                </wfs:Property>
                <wfs:Property>
                  <wfs:ValueReference>legacy_id</wfs:ValueReference>
                </wfs:Property>
                <fes:Filter><fes:ResourceId rid="42"/></fes:Filter>
              </wfs:Update>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        def action = (TxUpdate) tx.actions().next()

        then:
        action.collectionId == 'AX_Buildings'
        action.actionId.orElse(null) == 'u1'
        action.modify*.name == ['height']
        action.modify[0].value.asText() == '12.5'
        action.deleteProperties == ['legacy_id']
        action.filter.isPresent()
        action.filter.get() instanceof In

        cleanup:
        tx.close()
    }

    def 'wfs:Delete requires a fes:ResourceId filter; collection from typeName'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:fes="http://www.opengis.net/fes/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Delete typeName="ax:AX_Buildings" handle="d1">
                <fes:Filter>
                  <fes:ResourceId rid="42"/>
                  <fes:ResourceId rid="43"/>
                </fes:Filter>
              </wfs:Delete>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        def action = (TxDelete) tx.actions().next()

        then:
        action.type == TxActionType.DELETE
        action.collectionId == 'AX_Buildings'
        action.actionId.orElse(null) == 'd1'
        action.filter.isPresent()
        action.filter.get() instanceof In

        cleanup:
        tx.close()
    }

    def 'wfs:Delete without a filter is a hard error (spec disallows bulk delete via this parser)'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Delete typeName="ax:AX_Buildings"/>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'a non-ResourceId fes:Filter predicate is rejected'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:fes="http://www.opengis.net/fes/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Delete typeName="ax:AX_Buildings">
                <fes:Filter>
                  <fes:PropertyIsEqualTo>
                    <fes:ValueReference>height</fes:ValueReference>
                    <fes:Literal>9</fes:Literal>
                  </fes:PropertyIsEqualTo>
                </fes:Filter>
              </wfs:Delete>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'multiple actions stream in document order across Insert/Update/Delete'() {
        given:
        def body = '''<?xml version="1.0"?>
            <wfs:Transaction xmlns:wfs="http://www.opengis.net/wfs/2.0"
                             xmlns:fes="http://www.opengis.net/fes/2.0"
                             xmlns:ax="http://example.org/ax">
              <wfs:Insert>
                <ax:AX_Buildings><ax:id>1</ax:id></ax:AX_Buildings>
              </wfs:Insert>
              <wfs:Update typeName="ax:AX_Buildings">
                <wfs:Property>
                  <wfs:ValueReference>height</wfs:ValueReference>
                  <wfs:Value>5</wfs:Value>
                </wfs:Property>
                <fes:Filter><fes:ResourceId rid="1"/></fes:Filter>
              </wfs:Update>
              <wfs:Delete typeName="ax:AX_Buildings">
                <fes:Filter><fes:ResourceId rid="2"/></fes:Filter>
              </wfs:Delete>
            </wfs:Transaction>'''

        when:
        def tx = parser.parse(bytes(body), XML)
        def types = []
        def it = tx.actions()
        while (it.hasNext()) {
            types << it.next().type
        }

        then:
        types == [TxActionType.INSERT, TxActionType.UPDATE, TxActionType.DELETE]

        cleanup:
        tx.close()
    }
}
