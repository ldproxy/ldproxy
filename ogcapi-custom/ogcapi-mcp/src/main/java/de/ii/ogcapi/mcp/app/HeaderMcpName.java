/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** The header {@code Mcp-Name} of a request to the MCP endpoint. */
@Singleton
@AutoBind
public class HeaderMcpName extends AbstractHeaderMcp {

  @Inject
  HeaderMcpName(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "McpName";
  }

  @Override
  public String getName() {
    return "Mcp-Name";
  }

  @Override
  public String getDescription() {
    return "The name of the tool of a request `tools/call`, as in the request body. Required by the"
        + " Model Context Protocol from revision 2026-07-28.";
  }
}
