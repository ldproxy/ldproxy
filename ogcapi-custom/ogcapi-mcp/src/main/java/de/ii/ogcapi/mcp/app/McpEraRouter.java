/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.app;

import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

/**
 * The MCP endpoint of an API serves clients of both protocol eras: a modern client (revision
 * 2026-07-28 and later) declares its protocol version on every request, over HTTP also in the
 * header MCP-Protocol-Version; a legacy client sends no such header with its initialize request and
 * an earlier version with the requests that follow. The header selects the server, the modern
 * server rejects a request whose header does not match its body.
 */
final class McpEraRouter extends HttpServlet {

  private static final long serialVersionUID = 1L;

  static final String PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
  static final String FIRST_MODERN_VERSION = "2026-07-28";

  private final transient HttpServletStatelessServerTransport legacy;
  private final transient HttpServletMcpTransport modern;

  McpEraRouter(HttpServletStatelessServerTransport legacy, HttpServletMcpTransport modern) {
    super();
    this.legacy = legacy;
    this.modern = modern;
  }

  // protocol versions are dates, a later modern version also goes to the modern server, which
  // answers with the versions it supports
  static boolean isModern(String protocolVersion) {
    return Objects.nonNull(protocolVersion)
        && FIRST_MODERN_VERSION.compareTo(protocolVersion.trim()) <= 0;
  }

  @Override
  protected void service(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    if (isModern(request.getHeader(PROTOCOL_VERSION_HEADER))) {
      modern.service(request, response);
    } else {
      legacy.service(request, response);
    }
  }

  void close() {
    legacy.close();
    modern.closeGracefully();
  }
}
