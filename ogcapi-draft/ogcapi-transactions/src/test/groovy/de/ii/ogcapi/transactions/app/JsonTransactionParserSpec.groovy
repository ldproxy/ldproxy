/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import de.ii.ogcapi.foundation.infra.json.SchemaValidatorImpl
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
        parser = new JsonTransactionParser(new CqlImpl(), new SchemaValidatorImpl())
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

    def 'delete action: filter parsed as cql2-text (string filter, default lang)'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "delete", "collection": "buildings", "id": "a1",
             "filter": "id = '17'"}
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

    def 'replace action: requires properties.feature object'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "replace", "collection": "buildings",
             "properties": {"not_a_feature": true}}
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
             "properties": {"feature": {"type": "Feature", "id": "42", "properties": {"name": "X"}}}}
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
        action.add*.name == ['owner']
        action.add[0].value.asText() == 'Alice'
        action.modify*.name == ['height']
        action.modify[0].value.asDouble() == 12.5d
        action.deleteProperties == ['legacy_id']

        cleanup:
        tx.close()
    }

    def 'streaming insert: yields one InputStream per feature in array order'() {
        given:
        def body = '''{
          "transaction": [
            {"action": "insert", "collection": "buildings", "items": [
               {"type": "Feature", "id": "1", "properties": {"n": 1}},
               {"type": "Feature", "id": "2", "properties": {"n": 2}},
               {"type": "Feature", "id": "3", "properties": {"n": 3}}
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
               {"type": "Feature", "id": "1", "properties": {}}
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
               {"type": "Feature", "id": "1", "properties": {}},
               {"type": "Feature", "id": "2", "properties": {}},
               {"type": "Feature", "id": "3", "properties": {}}
            ]},
            {"action": "delete", "collection": "buildings", "filter": "id = '9'"}
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
            {"items": [{"type": "Feature", "id": "1", "properties": {}}],
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

    // --- validateEnvelope (Prefer: handling=strict) --------------------------

    private static byte[] utf8(String s) {
        return s.getBytes('UTF-8')
    }

    def 'bundled envelope schema resource loads and parses as JSON'() {
        // Smoke check that the runtime resource exists, is valid JSON, and declares the JSON
        // Schema draft. The behavioural assertions further down exercise the schema content.
        given:
        def stream = JsonTransactionParser.classLoader.getResourceAsStream(
                'de/ii/ogcapi/transactions/transaction-envelope.json')

        expect:
        stream != null
        def root = new groovy.json.JsonSlurper().parse(stream)
        root.'$schema' == 'https://json-schema.org/draft/2020-12/schema'
        root.allOf instanceof List
        !root.allOf.isEmpty()
    }

    def 'validateEnvelope accepts a well-formed transaction body'() {
        given:
        def body = '''{
            "semantic": "batch",
            "transaction": [
              {"action": "delete", "collection": "c"},
              {"action": "insert", "collection": "c", "items": [{"type": "Feature", "properties": {}}]}
            ]
        }'''

        when:
        parser.validateEnvelope(utf8(body), JSON)

        then:
        noExceptionThrown()
    }

    def 'validateEnvelope rejects a body that is not a JSON object'() {
        when:
        parser.validateEnvelope(utf8('[]'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'validateEnvelope rejects a body missing the required transaction array'() {
        when:
        parser.validateEnvelope(utf8('{"semantic": "atomic"}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'validateEnvelope rejects an unknown semantic enum value'() {
        when:
        parser.validateEnvelope(utf8('{"semantic": "eventually", "transaction": []}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'validateEnvelope rejects an action whose action discriminator is unknown'() {
        when:
        parser.validateEnvelope(
                utf8('{"transaction": [{"action": "annihilate", "collection": "c"}]}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'validateEnvelope rejects an insert action without items'() {
        when:
        parser.validateEnvelope(
                utf8('{"transaction": [{"action": "insert", "collection": "c"}]}'), JSON)

        then:
        thrown(IllegalArgumentException)
    }

    def 'validateEnvelope rejects malformed JSON'() {
        when:
        parser.validateEnvelope(utf8('{not json'), JSON)

        then:
        thrown(IllegalArgumentException)
    }
}
