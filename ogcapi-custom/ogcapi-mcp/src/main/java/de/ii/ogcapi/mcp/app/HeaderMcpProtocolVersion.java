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

/** The header {@code MCP-Protocol-Version} of a request to the MCP endpoint. */
@Singleton
@AutoBind
public class HeaderMcpProtocolVersion extends AbstractHeaderMcp {

  @Inject
  HeaderMcpProtocolVersion(SchemaValidator schemaValidator) {
    super(schemaValidator);
  }

  @Override
  public String getId() {
    return "McpProtocolVersion";
  }

  @Override
  public String getName() {
    return "MCP-Protocol-Version";
  }

  @Override
  public String getDescription() {
    return "The version of the Model Context Protocol that the request uses, for example"
        + " `2026-07-28`. A client of the revision 2026-07-28 or later sends it with every"
        + " request; a client of an earlier revision sends it with every request after"
        + " `initialize`.";
  }
}
