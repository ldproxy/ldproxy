/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.ogcapi.processes.domain.model.ProcessData
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator
import io.swagger.v3.core.util.Json
import spock.lang.Specification

class McpToolsProcessesSpec extends Specification {

    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).findAndRegisterModules()
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()

    static final OgcApiDataV2 API = new ImmutableOgcApiDataV2.Builder()
            .id('buildings')
            .serviceType('OGC_API')
            .label('Buildings')
            .description('The buildings and addresses.')
            .build()

    static ProcessData process(String yaml) {
        YAML.readValue(yaml, ProcessData)
    }

    static final ProcessData COUNTS = process("""\
        id: counts
        title: Counts per area
        description: Counts the buildings per area.
        version: 1.0.0
        jobControlOptions: [SYNC_EXECUTE, ASYNC_EXECUTE]
        inputs:
          municipality:
            title: Municipality
            description: The code of the municipality.
            schema:
              type: string
              pattern: '^[0-9]{4}\$'
          years:
            title: Years
            minOccurs: 0
            maxOccurs: 3
            schema:
              type: integer
              minimum: 1000
        outputs:
          groups:
            title: Groups
            schema:
              type: array
              items:
                type: object
        """.stripIndent())

    McpToolsProcesses tools = new McpToolsProcesses(null, null, null, null, null, JSON)

    static Map<String, Object> asMap(io.swagger.v3.oas.models.media.Schema<?> schema) {
        JSON.readValue(Json.pretty(schema), Map)
    }

    def 'a process is a tool with the inputs as arguments'() {
        when:
        def tool = tools.toTool(API, COUNTS)
        def input = asMap(tool.inputSchema)

        then:
        tool.id == 'process_counts'
        tool.name == 'Process - Counts per area'
        tool.description == 'Counts the buildings per area.' + System.lineSeparator() + 'The buildings and addresses.'
        !tool.readOnly
        input.type == 'object'
        input.required == ['municipality']
        input['properties']['municipality'] == [type: 'string', pattern: '^[0-9]{4}$', title: 'Municipality', description: 'The code of the municipality.']
    }

    def 'an input with more than one value is an array of its values'() {
        when:
        def input = asMap(tools.toTool(API, COUNTS).inputSchema)

        then:
        input['properties']['years']['type'] == 'array'
        input['properties']['years']['maxItems'] == 3
        !input['properties']['years'].containsKey('minItems')
        input['properties']['years']['items'] == [type: 'integer', minimum: 1000]
        input['properties']['years']['title'] == 'Years'
    }

    def 'the outputs are the properties of the result, none of them required'() {
        when:
        def output = asMap(tools.toTool(API, COUNTS).outputSchema)

        then:
        output.type == 'object'
        !output.containsKey('required')
        output['properties']['groups'] == [type: 'array', items: [type: 'object'], title: 'Groups']
    }

    def 'the schemas of a process tool are valid JSON Schema 2020-12 documents'() {
        given:
        def validator = new DefaultJsonSchemaValidator(JSON)
        def tool = tools.toTool(API, COUNTS)

        expect:
        validator.validateSchema(asMap(tool.inputSchema)).valid()
        validator.validateSchema(asMap(tool.outputSchema)).valid()
    }

    def 'an exclusive bound of a process input is an exclusive bound of the tool schema'() {
        given:
        def nearest = process("""\
            id: nearest
            title: Nearest
            version: 1.0.0
            inputs:
              distance:
                title: Distance
                schema:
                  type: number
                  exclusiveMinimum: 0
            outputs: {}
            """.stripIndent())
        def validator = new DefaultJsonSchemaValidator(JSON)

        when:
        def input = McpSchemaVersions.toJsonSchema2020(asMap(tools.toTool(API, nearest).inputSchema))

        then:
        input['properties']['distance'] == [type: 'number', exclusiveMinimum: 0, title: 'Distance']
        validator.validateSchema(input).valid()
    }

    def 'a property without a type allows values of any type'() {
        given:
        def aggregate = process("""\
            id: aggregate
            title: Aggregate
            version: 1.0.0
            inputs:
              source:
                title: Source
                schema:
                  type: object
                  properties:
                    collection:
                      description: A collection id or a list of collection ids.
                    query:
                      type: string
            outputs: {}
            """.stripIndent())
        def validator = new DefaultJsonSchemaValidator(JSON)

        when:
        def input = McpSchemaVersions.toJsonSchema2020(asMap(tools.toTool(API, aggregate).inputSchema))
        def source = input['properties']['source']

        then:
        source.type == 'object'
        !source['properties']['collection'].containsKey('type')
        source['properties']['query'].type == 'string'
        validator.validateSchema(input).valid()
    }

    def 'a process without inputs and outputs has empty object schemas'() {
        given:
        def answer = process("""\
            id: answer
            version: 1.0.0
            """.stripIndent())

        when:
        def tool = tools.toTool(API, answer)

        then:
        tool.name == 'Process - answer'
        tool.description.startsWith('Process answer')
        asMap(tool.inputSchema) == [type: 'object']
        asMap(tool.outputSchema) == [type: 'object']
    }

    def 'processes are only tools if they are included'() {
        expect:
        McpToolsProcesses.isIncluded(COUNTS, included, excluded) == result

        where:
        included                       | excluded          || result
        []                             | []                || false
        ['*']                          | []                || true
        ['counts']                     | []                || true
        ['Counts per area']            | []                || true
        ['other']                      | []                || false
        ['*']                          | ['counts']        || false
        ['counts']                     | ['Counts per area'] || false
    }

    def 'only processes that can be executed synchronously are tools'() {
        given:
        def process = process("""\
            id: p
            version: 1.0.0
            jobControlOptions: ${options}
            """.stripIndent())

        expect:
        McpToolsProcesses.canExecuteSync(process) == result

        where:
        options                         || result
        '[]'                            || true
        '[SYNC_EXECUTE]'                || true
        '[SYNC_EXECUTE, ASYNC_EXECUTE]' || true
        '[ASYNC_EXECUTE]'               || false
    }

    def 'a feature collection in the outputs is returned without the links of the features'() {
        given:
        def collection = [
            type    : 'FeatureCollection',
            features: [[type: 'Feature', id: '1', links: [[rel: 'predecessor-version', href: 'x']], properties: [a: 1], distance: 5.9d]]]

        expect:
        McpToolsProcesses.forToolResult(collection) == [
            type    : 'FeatureCollection',
            features: [[type: 'Feature', id: '1', properties: [a: 1], distance: 5.9d]]]
        McpToolsProcesses.forToolResult([groups: [], total: [count: 0L]]) == [groups: [], total: [count: 0L]]
        McpToolsProcesses.forToolResult(42) == 42
    }
}
