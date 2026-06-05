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
import de.ii.xtraplatform.cql.app.CqlImpl
import jakarta.ws.rs.core.MediaType
import spock.lang.Shared
import spock.lang.Specification

/**
 * Locks the streaming JSON transaction parser:
 *
 * <ul>
 *   <li>envelope handling (default/explicit semantic, malformed shapes)
 *   <li>per-action parsing for replace / update / delete
 *   <li>streaming insert's pull-one-feature-at-a-time iterator and its drain behaviour when the
 *       executor advances to the next action without finishing items
 * </ul>
 *
 * Streaming guarantees are the load-bearing property here — a regression that materialises the
 * whole items array up-front would not show up in a unit test that just checks the produced
 * feature bytes, so this spec asserts iterator state across action boundaries.
 */
class JsonTransactionParserSpec extends Specification {

    @Shared
    JsonTransactionParser parser

    def setupSpec() {
        parser = new JsonTransactionParser(new CqlImpl())
    }

    private static InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes('UTF-8'))
    }

    private static MediaType JSON = MediaType.APPLICATION_JSON_TYPE

    def 'canParse accepts ogc-tx+json and application/json, rejects unrelated media types'() {
        expect:
        parser.canParse(MediaType.valueOf('application/ogc-tx+json'))
        parser.canParse(MediaType.APPLICATION_JSON_TYPE)
        parser.canParse(MediaType.valueOf('application/json;charset=utf-8'))
        !parser.canParse(MediaType.valueOf('application/xml'))
        !parser.canParse(null)
    }

    def 'envelope defaults semantic to ATOMIC when omitted'() {
        when:
        def tx = parser.parse(bytes('{"transaction": []}'), JSON)

        then:
        tx.semantic == TxSemantic.ATOMIC
        !tx.actions().hasNext()

        cleanup:
        tx.close()
    }

    def 'envelope reads explicit semantic'() {
        when:
        def tx = parser.parse(bytes('{"semantic": "batch", "transaction": []}'), JSON)

        then:
        tx.semantic == TxSemantic.BATCH

        cleanup:
        tx.close()
    }

    def 'envelope rejects body that is not a JSON object'() {
        when:
        parser.parse(bytes('[]'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'envelope rejects body without a transaction array'() {
        when:
        parser.parse(bytes('{"semantic": "atomic"}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'envelope rejects invalid semantic value'() {
        when:
        parser.parse(bytes('{"semantic": "snapshot", "transaction": []}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'envelope rejects null semantic value'() {
        when:
        parser.parse(bytes('{"semantic": null, "transaction": []}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'delete action: filter parsed as cql2-json (CQL2 filter, default lang)'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "delete", "collection": "buildings", "id": "a1",
             "filter": { "op": "=", "args": [ { "property": "id" }, "9" ] }}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def it = tx.actions()
        def action = it.next()

        then:
        !it.hasNext()
        action.type == TxActionType.DELETE
        action.collectionId == 'buildings'
        action.actionId.orElse(null) == 'a1'
        ((TxDelete) action).filter.isPresent()

        cleanup:
        tx.close()
    }

    def 'replace action: requires feature object'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "replace", "collection": "buildings",
             "feature": {"not_a_feature": true}}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'replace action: happy path serialises the embedded GeoJSON Feature'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "replace", "collection": "buildings",
             "feature": {"type": "Feature", "id": "42", "properties": {"name": "X"}, "geometry": null}}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def action = (TxReplace) tx.actions().next()
        def featureJson = new String(action.feature, 'UTF-8')

        then:
        action.collectionId == 'buildings'
        action.mediaType.toString() == 'application/geo+json'
        featureJson.contains('"type":"Feature"')
        featureJson.contains('"id":"42"')

        cleanup:
        tx.close()
    }

    def 'update action: rejects empty properties (no add/modify/delete)'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "update", "collection": "buildings",
             "properties": {}}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'update action: collects add / modify / delete entries in order'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "update", "collection": "buildings",
             "properties": {
               "add": [{"name": "owner", "value": "Alice"}],
               "modify": [{"name": "height", "value": 12.5}],
               "delete": ["legacy_id"]
             }}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def action = (TxUpdate) tx.actions().next()

        then:
        action.add*.path == [['owner']]
        action.add[0].value.asText() == 'Alice'
        action.modify*.path == [['height']]
        action.modify[0].value.asDouble() == 12.5d
        action.deleteProperties == [['legacy_id']]

        cleanup:
        tx.close()
    }

    def 'update action: name accepts dot-separated string or array of segments'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "update", "collection": "AA_Meilenstein",
             "filter": { "op": "=", "args": [ { "property": "id" }, "DEHE86202002BTa0" ] },
             "properties": {
               "modify": [
                 {"name": "lebenszeitintervall.endet",
                  "value": "2025-04-23T09:38:37Z"},
                 {"name": ["lebenszeitintervall", "beginnt"],
                  "value": "2025-04-22T04:50:46Z"}
               ],
               "delete": [
                 "lebenszeitintervall.grund",
                 ["lebenszeitintervall", "wer"]
               ]
             }}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def action = (TxUpdate) tx.actions().next()

        then:
        action.modify*.path == [
                ['lebenszeitintervall', 'endet'],
                ['lebenszeitintervall', 'beginnt']
        ]
        action.deleteProperties == [
                ['lebenszeitintervall', 'grund'],
                ['lebenszeitintervall', 'wer']
        ]

        cleanup:
        tx.close()
    }

    def 'streaming insert: yields one InputStream per feature in array order'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": {"n": 1}, "geometry": null},
               {"type": "Feature", "id": "2", "properties": {"n": 2}, "geometry": null},
               {"type": "Feature", "id": "3", "properties": {"n": 3}, "geometry": null}
            ]}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def action = (TxInsert) tx.actions().next()
        def featureBlobs = []
        action.items().forEachRemaining { item -> featureBlobs << new String(item.payload().readAllBytes(), 'UTF-8') }

        then:
        action.collectionId == 'buildings'
        featureBlobs.size() == 3
        featureBlobs[0].contains('"id":"1"')
        featureBlobs[1].contains('"id":"2"')
        featureBlobs[2].contains('"id":"3"')

        cleanup:
        tx.close()
    }

    def 'streaming insert: items() may only be called once'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": {}, "geometry": null}
            ]}
          ]
        }'''
        def tx = parser.parse(bytes(body), JSON)
        def action = (TxInsert) tx.actions().next()
        action.items().forEachRemaining { item -> item.payload().readAllBytes() }

        when:
        action.items()

        then:
        thrown(IllegalStateException)

        cleanup:
        tx.close()
    }

    def 'streaming insert: parent iterator drains the unread items tail and advances to the next action'() {
        given: 'an insert whose items the executor abandons after the first feature'
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": {}, "geometry": null},
               {"type": "Feature", "id": "2", "properties": null, "geometry": null},
               {"type": "Feature", "id": "3", "properties": null, "geometry": null}
            ]},
            {"action": "delete", "collection": "buildings", "filter": { "op": "=", "args": [ { "property": "id" }, "9" ] }}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def it = tx.actions()
        def first = (TxInsert) it.next()
        def firstFeature = first.items().next()  // pull just one, abandon the rest
        firstFeature.payload().readAllBytes()
        def second = it.next()  // forces drain of the abandoned tail

        then:
        second.type == TxActionType.DELETE
        ((TxDelete) second).filter.isPresent()
        !it.hasNext()

        cleanup:
        tx.close()
    }

    def 'streaming insert: items appearing before action declaration is a hard error'() {
        given: 'streaming requires action+collection before items'
        def body = '''{
          "transaction": [
            {"items": [{"type": "Feature", "id": "1", "properties": null, "geometry": null}]},
             "action": "insert", "collection": "buildings"}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'action object missing required action property is rejected'() {
        given:
        def body = '{"transaction": [{"collection": "buildings"}]}'

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'action object missing required collection property is rejected'() {
        given:
        def body = '{"transaction": [{"action": "delete"}]}'

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx.close()
    }

    def 'action object rejects non-textual collection property'() {
        given:
        def body = '{"transaction": [{"action": "delete", "collection": 7}]}'

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx?.close()
    }

    def 'action object rejects non-textual filter-lang property'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "delete", "collection": "buildings", "filter-lang": 2,
             "filter": { "op": "=", "args": [ { "property": "id" }, "9" ] }}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx?.close()
    }

    def 'action object with unknown action discriminator is rejected while iterating'() {
        given:
        def body = '{"transaction": [{"action": "annihilate", "collection": "c"}]}'

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx?.close()
    }

    def 'insert action without items is rejected while iterating'() {
        given:
        def body = '{"transaction": [{"action": "insert", "collection": "c"}]}'

        when:
        def tx = parser.parse(bytes(body), JSON)
        tx.actions().next()

        then:
        thrown(IllegalArgumentException)

        cleanup:
        tx?.close()
    }

    def 'malformed JSON is rejected during parse'() {
        when:
        parser.parse(bytes('{not json'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'coalesce: consecutive identity-free same-collection inserts merge into one TxInsert'() {
        // given: three back-to-back inserts targeting the same collection, no id/title/description
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": null, "geometry": null},
               {"type": "Feature", "id": "2", "properties": null, "geometry": null}
            ]},
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "3", "properties": null, "geometry": null}
            ]},
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "4", "properties": null, "geometry": null}
            ]}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def it = tx.actions()
        def merged = (TxInsert) it.next()
        def itemIds = []
        merged.items().forEachRemaining { item ->
            itemIds << item.featureId().orElse(null)
            item.payload().readAllBytes() // drain to keep parser advancing
        }

        then: 'parent iterator sees one TxInsert; merged action carries all four features'
        merged.collectionId == 'buildings'
        itemIds == ['1', '2', '3', '4']
        and: 'no further actions remain'
        !it.hasNext()

        cleanup:
        tx.close()
    }

    def 'coalesce stops at a different collection: produces two TxInserts'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": null, "geometry": null}
            ]},
            {"action": "insert", "collection": "roads", "items": [
               {"type": "Feature", "id": "a", "properties": null, "geometry": null}
            ]}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def it = tx.actions()
        def first = (TxInsert) it.next()
        first.items().forEachRemaining { item -> item.payload().readAllBytes() }
        def second = (TxInsert) it.next()
        second.items().forEachRemaining { item -> item.payload().readAllBytes() }

        then:
        first.collectionId == 'buildings'
        second.collectionId == 'roads'
        !it.hasNext()

        cleanup:
        tx.close()
    }

    def 'coalesce stops at a non-insert action interleaved between same-collection inserts'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": null, "geometry": null}
            ]},
            {"action": "delete", "collection": "buildings", "filter": { "op": "=", "args": [ { "property": "id" }, "9" ] }},
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "2", "properties": null, "geometry": null}
            ]}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def it = tx.actions()
        def first = (TxInsert) it.next()
        first.items().forEachRemaining { item -> item.payload().readAllBytes() }
        def second = it.next()
        def third = (TxInsert) it.next()
        third.items().forEachRemaining { item -> item.payload().readAllBytes() }

        then:
        first.type == TxActionType.INSERT
        second.type == TxActionType.DELETE
        third.type == TxActionType.INSERT
        !it.hasNext()

        cleanup:
        tx.close()
    }

    def 'coalesce skips an insert that declares its own id (per-action identity preserved)'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": null, "geometry": null}
            ]},
            {"action": "insert", "collection": "buildings", "id": "a2", "items": [
               {"type": "Feature", "id": "2", "properties": null, "geometry": null}
            ]}
          ]
        }'''

        when:
        def tx = parser.parse(bytes(body), JSON)
        def it = tx.actions()
        def first = (TxInsert) it.next()
        first.items().forEachRemaining { item -> item.payload().readAllBytes() }
        def second = (TxInsert) it.next()
        second.items().forEachRemaining { item -> item.payload().readAllBytes() }

        then: 'each action becomes its own TxInsert, identifying id flows through to the second one'
        first.actionId.orElse(null) == null
        second.actionId.orElse(null) == 'a2'
        !it.hasNext()

        cleanup:
        tx.close()
    }
}
