/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

/** A dispatcher with the operations of a small API "bag"; the responses come from closures. */
class FakeDispatcher implements StepDispatcher {

    static final Map<String, StepDispatcher.Operation> OPERATIONS = [
            "pand.getItems"         : new StepDispatcher.Operation("bag", "pand.getItems", "GET", "/collections/pand/items", true),
            "pand.getItem"          : new StepDispatcher.Operation("bag", "pand.getItem", "GET", "/collections/pand/items/{featureId}", true),
            "executeProcess"        : new StepDispatcher.Operation("bag", "executeProcess", "POST", "/processes/{processId}/execution", true),
            "getCollections"        : new StepDispatcher.Operation("bag", "getCollections", "GET", "/collections", false),
    ]

    final Map<String, Closure<StepResponse>> responses = [:]
    final List<StepRequest> requests = []

    @Override
    Optional<String> apiOfSource(String url, String apiId) {
        return url == "/bag/api" || url == "https://example.org/bag/api" ? Optional.of("bag") : Optional.empty()
    }

    @Override
    Optional<StepDispatcher.Operation> operation(String apiId, String operationId) {
        return Optional.ofNullable(OPERATIONS[operationId])
    }

    @Override
    Optional<StepDispatcher.Operation> operation(String apiId, String method, String path) {
        return Optional.ofNullable(OPERATIONS.values().find { it.method() == method && it.path() == path })
    }

    @Override
    StepResponse dispatch(StepRequest request, int maximumResponseSize) {
        requests.add(request)
        Closure<StepResponse> response = responses[request.operationId()]
        return response != null ? response.call(request, requests.size()) : new StepResponse(404, [:], Optional.empty())
    }

    static StepResponse ok(Object body, Map<String, String> headers = [:]) {
        return new StepResponse(200, headers, Optional.of(body))
    }
}
