/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app

import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApi
import de.ii.ogcapi.mcp.domain.ImmutableMcpConfiguration
import de.ii.ogcapi.mcp.domain.ImmutableMcpIncludeExclude
import de.ii.ogcapi.mcp.domain.McpConfiguration
import de.ii.xtraplatform.blobs.domain.BlobReader
import de.ii.xtraplatform.blobs.domain.ResourceStore
import jakarta.ws.rs.NotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiPredicate
import java.util.stream.Stream
import spock.lang.Specification
import spock.lang.TempDir

class McpToolsSkillsSpec extends Specification {

    static final String SKILL = '''---
name: bgt
description: >-
  Read before answering questions about the topography.
  English: topography.
---

# The BGT
'''

    @TempDir
    Path root

    McpToolsSkills source
    OgcApi api

    def setup() {
        write('bgt/bgt/SKILL.md', SKILL)
        write('bgt/bgt/references/waarden-wegdeel.md', '# wegdeel')
        write('bgt/bgt/references/.hidden.md', 'hidden')
        write('bgt/notes/README.md', 'not a skill, no SKILL.md')
        write('bgt/nodescription/SKILL.md', '---\nname: x\n---\n# no description')
        write('bag/bag/SKILL.md', SKILL.replace('bgt', 'bag'))

        ResourceStore skillsStore = Stub(ResourceStore)
        skillsStore.walk(_ as Path, _ as Integer, _ as BiPredicate) >> { Path path, Integer depth, BiPredicate matcher ->
            walk(root.resolve(path), depth, matcher)
        }
        skillsStore.content(_ as Path) >> { Path path ->
            Files.isRegularFile(root.resolve(path)) ? Optional.of(Files.newInputStream(root.resolve(path))) : Optional.empty()
        }
        skillsStore.lastModified(_ as Path) >> 0L
        skillsStore.size(_ as Path) >> 0L
        ResourceStore resourceStore = Stub(ResourceStore)
        resourceStore.with('skills') >> skillsStore

        source = new McpToolsSkills(resourceStore)
        api = Stub(OgcApi)
        api.getId() >> 'bgt'
        api.getData() >> new ImmutableOgcApiDataV2.Builder().id('bgt').serviceType('OGC_API').label('BGT').build()
    }

    def 'every directory of the API with a SKILL.md and a description is a tool'() {
        when:
        def tools = source.getTools(api, configuration([], []))

        then:
        tools*.id == ['skill_bgt']
        tools[0].name == 'Skill - bgt'
        tools[0].description == 'Read before answering questions about the topography. English: topography.'
        tools[0].readOnly
    }

    def 'the argument file lists the files of the skill, SKILL.md first, without hidden files'() {
        when:
        def tool = source.getTools(api, configuration([], []))[0]

        then:
        tool.inputSchema.properties.file.enum == ['SKILL.md', 'references/waarden-wegdeel.md']
    }

    def 'a call returns SKILL.md, or the reference file given in the argument file'() {
        given:
        def tool = source.getTools(api, configuration([], []))[0]

        when:
        def skill = source.call(api, tool, [:], Optional.empty())
        def reference = source.call(api, tool, [file: 'references/waarden-wegdeel.md'], Optional.empty())

        then:
        skill == [file: 'SKILL.md', content: SKILL]
        reference == [file: 'references/waarden-wegdeel.md', content: '# wegdeel']
    }

    def 'a call rejects a file that the skill does not list'() {
        given:
        def tool = source.getTools(api, configuration([], []))[0]

        when:
        source.call(api, tool, [file: file], Optional.empty())

        then:
        thrown(NotFoundException)

        where:
        file << ['../bag/SKILL.md', '../../bag/bag/SKILL.md', 'references/.hidden.md', '/etc/passwd', 'missing.md']
    }

    def 'included and excluded select the skills'() {
        expect:
        McpToolsSkills.isIncluded('bgt', included, excluded) == result

        where:
        included     | excluded | result
        []           | []       | true
        ['*']        | []       | true
        ['bgt']      | []       | true
        ['bag']      | []       | false
        []           | ['bgt']  | false
        ['*']        | ['bgt']  | false
    }

    def 'excluded removes a skill from the tools'() {
        when:
        def tools = source.getTools(api, configuration([], ['bgt']))

        then:
        tools.isEmpty()
    }

    def 'the frontmatter is read as YAML, a file without frontmatter has none'() {
        expect:
        McpToolsSkills.frontmatter(SKILL) == [name: 'bgt', description: 'Read before answering questions about the topography. English: topography.']
        McpToolsSkills.frontmatter('# no frontmatter') == [:]
        McpToolsSkills.frontmatter('---\n: [invalid\n---\n') == [:]
    }

    def 'the instructions name the skill tools'() {
        given:
        def apiData = new ImmutableOgcApiDataV2.Builder().id('bgt').serviceType('OGC_API').label('BGT').build()

        expect:
        McpServerImpl.instructions(apiData, ['skill_bgt']) ==
                'BGT\n\nFurther guidance for this API is available with the tool skill_bgt:' +
                ' which tool answers which question, what the values mean and what to watch out for.'
        McpServerImpl.instructions(apiData, []) == 'BGT'
    }

    private void write(String path, String content) {
        Path file = root.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private static Stream<Path> walk(Path dir, int depth, BiPredicate matcher) {
        if (!Files.isDirectory(dir)) {
            return Stream.empty()
        }
        return Files.walk(dir, depth)
                .filter { Files.isRegularFile(it) }
                .map { dir.relativize(it) }
                .filter { matcher.test(it, attributes(it)) }
    }

    private static BlobReader.PathAttributes attributes(Path path) {
        return new BlobReader.PathAttributes() {
            boolean isValue() { true }

            boolean isHidden() { path.fileName.toString().startsWith('.') }
        }
    }

    private static McpConfiguration configuration(List<String> included, List<String> excluded) {
        def builder = new ImmutableMcpConfiguration.Builder().enabled(true)
        if (!included.isEmpty()) {
            builder.included(new ImmutableMcpIncludeExclude.Builder().skills(included).build())
        }
        if (!excluded.isEmpty()) {
            builder.excluded(new ImmutableMcpIncludeExclude.Builder().skills(excluded).build())
        }
        return builder.build()
    }
}
