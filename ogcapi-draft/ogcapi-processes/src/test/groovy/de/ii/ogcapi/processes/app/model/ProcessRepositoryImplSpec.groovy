/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.model

import dagger.Lazy
import de.ii.ogcapi.processes.domain.model.ImmutableProcessData
import de.ii.ogcapi.processes.domain.model.ProcessContribution
import de.ii.ogcapi.processes.domain.model.ProcessData
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry
import de.ii.xtraplatform.values.domain.Identifier
import de.ii.xtraplatform.values.domain.ValueStore
import de.ii.xtraplatform.values.domain.Values
import java.util.concurrent.CompletableFuture
import spock.lang.Specification

class ProcessRepositoryImplSpec extends Specification {

    // the content of the value store, changed by the tests like a reload of the values
    Map<Identifier, ProcessData> stored = new LinkedHashMap<>()
    Map<String, ProcessData> contributed = new LinkedHashMap<>()
    ProcessRepositoryImpl repository

    def setup() {
        Values<ProcessData> values = Stub(Values)
        values.identifiers(*_) >> { new ArrayList<>(stored.keySet()) }
        values.get(_ as Identifier) >> { Identifier identifier -> stored.get(identifier) }
        ValueStore valueStore = Stub(ValueStore)
        valueStore.forType(ProcessData) >> values
        ProcessContribution contribution = { String apiId -> apiId == "api1" ? contributed : [:] } as ProcessContribution
        Lazy<Set<ProcessContribution>> contributions = { -> [contribution] as Set } as Lazy
        VolatileRegistry volatileRegistry = Stub(VolatileRegistry)
        volatileRegistry.onAvailable(*_) >> CompletableFuture.completedFuture(null)
        repository = new ProcessRepositoryImpl(valueStore, volatileRegistry, contributions)
        repository.onStart(false).toCompletableFuture().join()
    }

    static ProcessData process(String id, String version = "1.0.0") {
        new ImmutableProcessData.Builder().id(id).version(version).build()
    }

    def "processes added to the store after the start are found"() {
        given:
        stored.put(Identifier.from("Echo"), process("Echo"))

        expect:
        repository.getAll("api1").keySet() == ["Echo"] as Set
        repository.get("api1", "Sum").isEmpty()

        when:
        stored.put(Identifier.from("Sum", "api1"), process("Sum"))

        then:
        repository.getAll("api1").keySet() == ["Echo", "Sum"] as Set
        repository.get("api1", "Sum").isPresent()
        repository.getAll("api2").keySet() == ["Echo"] as Set
    }

    def "a changed process description is returned after the change"() {
        given:
        stored.put(Identifier.from("Sum", "api1"), process("Sum"))
        repository.getDirect("api1", "Sum")

        when:
        stored.put(Identifier.from("Sum", "api1"), process("Sum", "2.0.0"))

        then:
        repository.getDirect("api1", "Sum").getVersion() == "2.0.0"
        repository.getAll("api1").get("Sum").getVersion() == "2.0.0"
    }

    def "a global process wins over a process of the API with the same id"() {
        given:
        stored.put(Identifier.from("Sum"), process("Sum", "global"))
        stored.put(Identifier.from("Sum", "api1"), process("Sum", "api"))

        expect:
        repository.getDirect("api1", "Sum").getVersion() == "global"
        repository.getAll("api1").get("Sum").getVersion() == "global"
    }

    def "a stored process wins over a contributed process with the same id"() {
        given:
        contributed.put("Sum", process("Sum", "contributed"))
        contributed.put("Workflow", process("Workflow", "contributed"))

        when:
        stored.put(Identifier.from("Sum", "api1"), process("Sum", "stored"))

        then:
        repository.getDirect("api1", "Sum").getVersion() == "stored"
        repository.getAll("api1").collectEntries { id, p -> [id, p.getVersion()] } ==
                [Sum: "stored", Workflow: "contributed"]
    }
}
