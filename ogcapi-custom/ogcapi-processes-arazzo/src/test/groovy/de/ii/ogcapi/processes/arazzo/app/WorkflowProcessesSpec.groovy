/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app

import static de.ii.ogcapi.processes.arazzo.app.ArazzoFixtures.document
import static de.ii.ogcapi.processes.arazzo.app.ArazzoFixtures.workflow

import de.ii.ogcapi.foundation.infra.json.ClassSchemaCacheImpl
import de.ii.ogcapi.processes.domain.model.OgcProcess
import de.ii.ogcapi.processes.domain.model.OgcProcessSummary.JobControlOptions
import de.ii.ogcapi.processes.domain.model.Property
import de.ii.xtraplatform.values.domain.Identifier
import spock.lang.Specification

class WorkflowProcessesSpec extends Specification {

    def "a workflow is a process with its inputs and outputs"() {
        given:
        def doc = document([workflow("pand-per-buurt", [[stepId: "a", operationId: "executeProcess"]], [
                summary    : "Panden per buurt",
                description: "Telt de panden per buurt.",
                inputs     : [type: "object", required: ["gemeente"], properties: [
                        gemeente: [type: "string", title: "Gemeentecode", pattern: "^[0-9]{4}\$", examples: ["0307"]],
                        status  : [type: "array", items: [type: "string"], default: ["Pand in gebruik"]],
                        limit   : [type: "integer", minimum: 1, default: 10],
                ]],
                outputs    : [groups: '$steps.a.outputs.groups', total: '$steps.a.outputs.total'],
        ])])

        when:
        def process = WorkflowProcesses.toProcess(doc, doc.workflows[0])

        then:
        process.id == "pand-per-buurt"
        process.title.get() == "Panden per buurt"
        process.description.get() == "Telt de panden per buurt."
        process.version == "1.0.0"
        process.jobControlOptions == [JobControlOptions.SYNC_EXECUTE, JobControlOptions.ASYNC_EXECUTE]
        process.job.partials == ["arazzo-workflow:execute"]
        process.inputs.keySet() == ["gemeente", "status", "limit"] as Set
        process.inputs.gemeente.minOccurs == 1
        process.inputs.gemeente.title.get() == "Gemeentecode"
        process.inputs.gemeente.schema.pattern.get() == '^[0-9]{4}$'
        process.inputs.status.minOccurs == 0
        process.inputs.status.schema.items.get().type == Property.Type.STRING
        process.inputs.limit.schema.minimum.get() == 1
        process.outputs.keySet() == ["groups", "total"] as Set
        process.outputs.groups.schema.type.isEmpty()
    }

    def "the extension x-ldproxy-readOnly makes the process of a workflow read-only"() {
        given:
        def doc = document([
                workflow("liest", [[stepId: "a", operationId: "executeProcess"]], ["x-ldproxy-readOnly": true]),
                workflow("schrijft", [[stepId: "a", operationId: "executeProcess"]]),
        ])

        when:
        def readOnly = doc.workflows.collect { WorkflowProcesses.toProcess(doc, it).readOnly }

        then:
        readOnly == [true, false]
    }

    def "an input schema with keywords that process descriptions do not support is rejected: #keyword"() {
        given:
        def doc = document([workflow("w", [[stepId: "a", operationId: "executeProcess"]], [inputs: [type: "object", properties: [x: schema]]])])

        when:
        WorkflowProcesses.toProcess(doc, doc.workflows[0])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains(keyword)

        where:
        keyword               | schema
        "oneOf"               | [oneOf: [[type: "string"], [type: "integer"]]]
        "\$ref"               | ['$ref': "#/components/schemas/x"]
        "additionalProperties"| [type: "object", properties: [a: [type: "string"]], additionalProperties: false]
        "properties"          | [type: "object", properties: [a: [type: "object", properties: [b: [type: "string"]]]]]
        "minimum"             | [type: "array", items: [type: "integer", minimum: 0]]
    }

    def "the input schema may be a reusable schema of the components"() {
        given:
        def doc = document([workflow("w", [[stepId: "a", operationId: "executeProcess"]], [inputs: ['$ref': "#/components/inputs/area"]])],
                [components: [inputs: [area: [type: "object", properties: [buurt: [type: "string"]]]]]])

        expect:
        WorkflowProcesses.toProcess(doc, doc.workflows[0]).inputs.keySet() == ["buurt"] as Set
    }

    def "the id of a stored description is the file name without the extension"() {
        expect:
        WorkflowRunner.storedDocumentId(url) == id

        where:
        url                         || id
        "shared.yaml"               || "shared"
        "./shared.yml"              || "shared"
        "workflows/bag/shared.json" || "shared"
        "shared"                    || "shared"
        "./11-dakkapel.arazzo.yaml" || "11-dakkapel"
    }

    def "an array input may have object items with properties"() {
        given:
        def doc = document([workflow("w", [[stepId: "a", operationId: "executeProcess"]], [inputs: [type: "object", properties: [
                antwoorden: [type: "array", items: [type: "object", required: ["id"], properties: [id: [type: "integer"], antwoord: [type: "string", description: "Het antwoord"]]]]]]])])

        when:
        def items = WorkflowProcesses.toProcess(doc, doc.workflows[0]).inputs.antwoorden.schema.items.get()

        then:
        items.type == Property.Type.OBJECT
        items.required == ["id"]
        items.properties.keySet() == ["id", "antwoord"] as Set
        items.properties.antwoord == [type: "string", description: "Het antwoord"]
    }

    def "the schema of a process description in the API definition can be derived"() {
        when:
        def schema = new ClassSchemaCacheImpl().getSchema(OgcProcess)

        then:
        schema.properties.containsKey("inputs")
    }

    def "only the published workflows are processes, the inputs set by the server are not inputs"() {
        given:
        def inputs = [type: "object", required: ["apiKey", "adres"], properties: [apiKey: [type: "string", format: "password"], adres: [type: "string"]]]
        def doc = document([
                workflow("vraag", [[stepId: "a", workflowId: "hulp"]], [inputs: inputs]),
                // not published, so the unsupported keyword does not matter
                workflow("hulp", [[stepId: "a", operationId: "executeProcess"]], [inputs: [type: "object", properties: [x: [oneOf: [[type: "string"]]]]]]),
        ])

        when:
        def processes = WorkflowProcesses.derive(Identifier.from("dso", "workflows"), doc, new WorkflowProcesses.Selection(["vraag"], ["apiKey"] as Set))

        then:
        processes.keySet() == ["vraag"] as Set
        processes.vraag.inputs.keySet() == ["adres"] as Set
        processes.vraag.inputs.adres.minOccurs == 1
    }

    def "the operations with a handler can be steps"() {
        expect:
        InternalStepDispatcher.isSupported(method, path.split("/").findAll { !it.isEmpty() }) == supported

        where:
        method | path                                    || supported
        "GET"  | "/collections/pand/items"               || true
        "GET"  | "/collections/pand/items/{featureId}"   || true
        "GET"  | "/search/adres-op-postcode"             || true
        "POST" | "/search/adres-op-postcode"             || true
        "POST" | "/search"                               || true
        "POST" | "/processes/{processId}/execution"      || true
        "GET"  | "/collections"                          || false
        "GET"  | "/collections/pand/queryables"          || false
        "GET"  | "/search"                               || false
        "POST" | "/collections/pand/items"               || false
        "GET"  | "/collections/pand/items/{featureId}/versions" || false
    }
}
