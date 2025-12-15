/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiBuildingBlock;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExternalDocumentation;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.mcp.domain.ImmutableMcpConfiguration;
import de.ii.xtraplatform.entities.domain.ValidationResult;
import de.ii.xtraplatform.entities.domain.ValidationResult.MODE;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @title MCP
 * @langEn Publish [MCP (Model Context Protocol)](https://modelcontextprotocol.io) tools based on
 *     collection queries and stored queries.
 * @langDe Veröffentlichung von [MCP (Model Context Protocol)](https://modelcontextprotocol.io)
 *     Tools basierend auf Collection-Queries und Stored-Queries.
 * @scopeEn This building block automatically derives MCP tool definitions from the selected
 *     collections and stored queries.
 *     <p>The `/{apiId}/mcp` endpoint can be added to AI tools that support custom MCP connectors,
 *     for example
 *     [Claude](https://support.claude.com/en/articles/11175166-getting-started-with-custom-connectors-using-remote-mcp)
 *     and
 *     [ChatGPT](https://help.openai.com/en/articles/12584461-developer-mode-apps-and-full-mcp-connectors-in-chatgpt-beta).
 *     <p>The `/{apiId}/mcp/schema` endpoint only has informational character and can be used to
 *     check the generated MCP definitions.
 * @scopeDe Dieser Baustein leitet automatisch MCP Tool-Definitionen aus den ausgewählten
 *     Collections und Stored-Queries ab.
 *     <p>Der Endpunkt `/{apiId}/mcp` kann KI-Werkzeugen hinzugefügt werden, die benutzerdefinierte
 *     MCP-Konnektoren unterstützen, z. B.
 *     [Claude](https://support.claude.com/en/articles/11175166-getting-started-with-custom-connectors-using-remote-mcp)
 *     und
 *     [ChatGPT](https://help.openai.com/en/articles/12584461-developer-mode-apps-and-full-mcp-connectors-in-chatgpt-beta).
 *     <p>Der Endpunkt `/{apiId}/mcp/schema` hat nur informativen Charakter und kann verwendet
 *     werden, um die generierten MCP-Definitionen zu überprüfen.
 * @ref:cfg {@link de.ii.ogcapi.mcp.domain.McpConfiguration}
 * @ref:cfgProperties {@link de.ii.ogcapi.mcp.domain.ImmutableMcpConfiguration}
 * @ref:endpoints {@link de.ii.ogcapi.mcp.app.EndpointMcp}, {@link
 *     de.ii.ogcapi.mcp.app.EndpointMcpSchema}
 */
@Singleton
@AutoBind
public class McpBuildingBlock implements ApiBuildingBlock {

  public static final Optional<SpecificationMaturity> MATURITY =
      Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  public static final Optional<ExternalDocumentation> SPEC = Optional.empty();

  @Inject
  public McpBuildingBlock() {}

  @Override
  public ExtensionConfiguration getDefaultConfiguration() {
    return new ImmutableMcpConfiguration.Builder().enabled(false).build();
  }

  @Override
  public ValidationResult onStartup(OgcApi api, MODE apiValidation) {
    return ApiBuildingBlock.super.onStartup(api, apiValidation);
  }
}
