/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.mcp.domain;

import de.ii.ogcapi.collections.queryables.domain.QueryParameterTemplateQueryable;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import java.util.List;

public interface McpServer {

  McpSchema getSchema(
      OgcApiDataV2 apiData, List<QueryParameterTemplateQueryable> queryParameterTemplates);
}
