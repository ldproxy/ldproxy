/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.mcp.domain.ImmutableMcpTool;
import de.ii.ogcapi.mcp.domain.McpConfiguration;
import de.ii.ogcapi.mcp.domain.McpConfiguration.McpIncludeExclude;
import de.ii.ogcapi.mcp.domain.McpTool;
import de.ii.xtraplatform.blobs.domain.BlobReader;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The skills of an API as tools: guidance for agents in the Agent Skills format, a directory per
 * skill with a file SKILL.md and optional reference files, in the store at {@code
 * resources/skills/<apiId>/<skill>/}. A skill is a tool, because tools are the one part of MCP that
 * every client passes to the model; the tool returns the text of SKILL.md or of a reference file.
 */
class McpToolsSkills implements McpToolSource {

  static final String PREFIX = "skill_";
  static final String STORE_RESOURCE_TYPE = "skills";
  static final String SKILL_FILE = "SKILL.md";
  static final String ARGUMENT_FILE = "file";

  private static final Logger LOGGER = LoggerFactory.getLogger(McpToolsSkills.class);
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
  private static final Pattern FRONTMATTER =
      Pattern.compile("\\A---\\R(.*?)\\R---\\R?", Pattern.DOTALL);
  private static final String ANY = "*";
  // skill directory and reference files: the depth of SKILL.md and of references/<file>
  private static final int MAX_DEPTH = 4;

  private final ResourceStore skillsStore;

  McpToolsSkills(ResourceStore resourceStore) {
    this.skillsStore = resourceStore.with(STORE_RESOURCE_TYPE);
  }

  @Override
  public String getPrefix() {
    return PREFIX;
  }

  @Override
  public List<McpTool> getTools(OgcApi api, McpConfiguration configuration) {
    List<String> included =
        configuration.getIncluded().map(McpIncludeExclude::getSkills).orElse(List.of());
    List<String> excluded =
        configuration.getExcluded().map(McpIncludeExclude::getSkills).orElse(List.of());

    List<McpTool> tools = new ArrayList<>();
    for (String skill : skills(api.getId())) {
      if (!isIncluded(skill, included, excluded)) {
        continue;
      }
      try {
        toTool(api.getId(), skill).ifPresent(tools::add);
      } catch (IOException e) {
        LOGGER.warn(
            "MCP skill '{}' of API '{}' is not available: {}", skill, api.getId(), e.getMessage());
      }
    }
    return tools;
  }

  // the skills are files that can change without a change of the API
  @Override
  public int getFingerprint(OgcApi api) {
    Path dir = Path.of(api.getId());
    try (Stream<Path> files =
        skillsStore.walk(dir, MAX_DEPTH, (path, attributes) -> isFile(path, attributes))) {
      return files
          .sorted()
          .map(
              path -> {
                try {
                  return Objects.hash(
                      path,
                      skillsStore.lastModified(dir.resolve(path)),
                      skillsStore.size(dir.resolve(path)));
                } catch (IOException e) {
                  return path.hashCode();
                }
              })
          .toList()
          .hashCode();
    } catch (IOException e) {
      return 0;
    }
  }

  @Override
  public Map<String, Object> call(
      OgcApi api,
      McpTool tool,
      Map<String, Object> arguments,
      Optional<ApiRequestContext> mcpRequest)
      throws IOException {
    String skill = tool.getId().substring(PREFIX.length());
    String file =
        Optional.ofNullable(arguments)
            .map(args -> args.get(ARGUMENT_FILE))
            .map(Object::toString)
            .filter(value -> !value.isBlank())
            .orElse(SKILL_FILE);

    // only the files of the skill that the tool lists, never a path outside of the skill
    if (!files(api.getId(), skill).contains(file)) {
      throw new NotFoundException("The skill '%s' has no file '%s'.".formatted(skill, file));
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put(ARGUMENT_FILE, file);
    result.put(
        "content",
        read(Path.of(api.getId(), skill).resolve(file))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "The skill '%s' has no file '%s'.".formatted(skill, file))));
    return result;
  }

  static boolean isIncluded(String skill, List<String> included, List<String> excluded) {
    return (included.isEmpty() || included.contains(ANY) || included.contains(skill))
        && !excluded.contains(skill);
  }

  // the directories of the API that contain a SKILL.md
  private List<String> skills(String apiId) {
    try (Stream<Path> files =
        skillsStore.walk(
            Path.of(apiId),
            2,
            (path, attributes) ->
                isFile(path, attributes)
                    && path.getNameCount() == 2
                    && SKILL_FILE.equals(path.getFileName().toString()))) {
      return files.map(path -> path.getName(0).toString()).sorted().toList();
    } catch (IOException e) {
      LOGGER.warn("The MCP skills of API '{}' could not be read: {}", apiId, e.getMessage());
      return List.of();
    }
  }

  // the files of a skill relative to its directory, with '/' as separator, SKILL.md first
  private List<String> files(String apiId, String skill) throws IOException {
    try (Stream<Path> files =
        skillsStore.walk(
            Path.of(apiId, skill), MAX_DEPTH - 1, (path, attributes) -> isFile(path, attributes))) {
      return Stream.concat(
              Stream.of(SKILL_FILE),
              files
                  .map(path -> String.join("/", toStrings(path)))
                  .filter(file -> !SKILL_FILE.equals(file))
                  .sorted())
          .toList();
    }
  }

  private Optional<McpTool> toTool(String apiId, String skill) throws IOException {
    Optional<String> content = read(Path.of(apiId, skill, SKILL_FILE));
    if (content.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Object> frontmatter = frontmatter(content.get());
    Optional<String> description =
        Optional.ofNullable(frontmatter.get("description"))
            .map(Object::toString)
            .map(String::strip)
            .filter(value -> !value.isEmpty());
    if (description.isEmpty()) {
      LOGGER.warn(
          "MCP skill '{}' of API '{}' is not available, SKILL.md has no description in its"
              + " frontmatter",
          skill,
          apiId);
      return Optional.empty();
    }

    List<String> files = files(apiId, skill);
    ObjectSchema inputSchema = new ObjectSchema();
    if (files.size() > 1) {
      StringSchema file = new StringSchema();
      file.setDescription(
          "The file of the skill to read; without this argument "
              + SKILL_FILE
              + ", which says when to read the other files.");
      files.forEach(file::addEnumItem);
      inputSchema.addProperty(ARGUMENT_FILE, file);
    }

    ObjectSchema outputSchema = new ObjectSchema();
    outputSchema.addProperty(ARGUMENT_FILE, new StringSchema().description("The file."));
    outputSchema.addProperty(
        "content", new StringSchema().description("The text of the file (Markdown)."));
    outputSchema.setRequired(List.of(ARGUMENT_FILE, "content"));

    String name = Optional.ofNullable(frontmatter.get("name")).map(Object::toString).orElse(skill);
    return Optional.of(
        new ImmutableMcpTool.Builder()
            .id(PREFIX + skill)
            .name("Skill - " + name)
            .description(description.get())
            .inputSchema(inputSchema)
            .outputSchema(outputSchema)
            .build());
  }

  // the YAML between the first two lines '---'; without a frontmatter or with invalid YAML none
  static Map<String, Object> frontmatter(String content) {
    Matcher matcher = FRONTMATTER.matcher(content);
    if (!matcher.find()) {
      return Map.of();
    }
    try {
      Object yaml = YAML.readValue(matcher.group(1), Object.class);
      if (yaml instanceof Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
      }
    } catch (IOException e) {
      LOGGER.debug("Invalid frontmatter in SKILL.md: {}", e.getMessage());
    }
    return Map.of();
  }

  private Optional<String> read(Path path) throws IOException {
    Optional<InputStream> content = skillsStore.content(path);
    if (content.isEmpty()) {
      return Optional.empty();
    }
    try (InputStream inputStream = content.get()) {
      return Optional.of(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static boolean isFile(Path path, BlobReader.PathAttributes attributes) {
    return attributes.isValue()
        && !attributes.isHidden()
        && Stream.of(toStrings(path)).noneMatch(name -> name.startsWith("."));
  }

  private static String[] toStrings(Path path) {
    String[] names = new String[path.getNameCount()];
    for (int i = 0; i < names.length; i++) {
      names[i] = path.getName(i).toString();
    }
    return names;
  }
}
