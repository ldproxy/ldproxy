/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.transactions.app

import io.swagger.v3.oas.models.media.Schema
import spock.lang.Specification

/**
 * Locks the OpenAPI representation of the bundled {@code transaction-envelope.json} that
 * {@code EndpointTransactions} publishes as the request schema of POST /transactions.
 *
 * <ul>
 *   <li>The top-level schema is a non-empty composite (an {@code allOf}), not the empty
 *       {@code ObjectSchema} placeholder that the loader falls back to on failure.
 *   <li>The {@code $defs} block is lifted into {@code referencedSchemas}, each entry prefixed
 *       with {@code ogc-tx-} so the generated OpenAPI components don't collide with unrelated
 *       schemas in the API document.
 *   <li>JSON Schema 2020-12 {@code $ref}s into {@code #/$defs/...} are rewritten to OpenAPI
 *       {@code #/components/schemas/ogc-tx-...} so the published document is self-resolving.
 * </ul>
 */
class EndpointTransactionsEnvelopeSchemaSpec extends Specification {

    def "envelope loader returns the top-level schema as an allOf composite"() {
        when:
        EndpointTransactions.JsonEnvelopeSchema envelope = EndpointTransactions.jsonEnvelopeSchema()

        then:
        envelope.top != null
        envelope.top.allOf != null
        !envelope.top.allOf.isEmpty()
    }

    def "envelope loader lifts every 'defs' entry into referencedSchemas with the ogc-tx- prefix"() {
        when:
        EndpointTransactions.JsonEnvelopeSchema envelope = EndpointTransactions.jsonEnvelopeSchema()

        then:
        envelope.defs.containsKey('ogc-tx-feature')
        envelope.defs.containsKey('ogc-tx-action-metadata')
        envelope.defs.containsKey('ogc-tx-cql2JsonFilter')
        envelope.defs.containsKey('ogc-tx-nameValuePair')
        envelope.defs.containsKey('ogc-tx-filter-crs')
        envelope.defs.containsKey('ogc-tx-filter-lang')
        envelope.defs.every { _, schema -> schema instanceof Schema }
    }

    def "envelope loader rewrites defs refs into #/components/schemas/ogc-tx- refs"() {
        // Walk the JSON representation Swagger emits and assert that no #/$defs/ ref survives.
        given:
        EndpointTransactions.JsonEnvelopeSchema envelope = EndpointTransactions.jsonEnvelopeSchema()
        def topJson = io.swagger.v3.core.util.Json.mapper().writeValueAsString(envelope.top)
        def defsJson = io.swagger.v3.core.util.Json.mapper().writeValueAsString(envelope.defs)

        expect:
        !topJson.contains('#/$defs/')
        !defsJson.contains('#/$defs/')
        // At least one ref into the lifted components must remain — otherwise we forgot to
        // rewrite anything and the assertion above would be vacuously true.
        topJson.contains('#/components/schemas/ogc-tx-') || defsJson.contains('#/components/schemas/ogc-tx-')
    }

    def "envelope loader is idempotent across calls (cache returns same instance)"() {
        expect:
        EndpointTransactions.jsonEnvelopeSchema().is(EndpointTransactions.jsonEnvelopeSchema())
    }
}
