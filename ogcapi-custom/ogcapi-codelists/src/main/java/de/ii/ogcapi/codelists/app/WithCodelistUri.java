/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaString;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.JsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.JsonSchemaString;
import de.ii.ogcapi.features.core.domain.JsonSchemaVisitor;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.web.domain.URICustomizer;
import java.net.URI;
import java.net.URISyntaxException;

public class WithCodelistUri implements JsonSchemaVisitor {

  private final URI serviceUri;
  private final OgcApiDataV2 apiData;

  public WithCodelistUri(URI serviceUri, OgcApiDataV2 apiData) {
    this.serviceUri = serviceUri;
    this.apiData = apiData;
  }

  @Override
  public JsonSchema visit(JsonSchema schema) {
    if (schema.getCodelistId().isPresent()) {
      String codelistId = schema.getCodelistId().get();
      try {
        String codelistUri =
            new URICustomizer(serviceUri)
                .ensureNoTrailingSlash()
                .ensureLastPathSegments(apiData.getSubPath().toArray(new String[0]))
                .ensureLastPathSegments("codelists", codelistId)
                .build()
                .toString();
        if (schema instanceof JsonSchemaString string) {
          return new ImmutableJsonSchemaString.Builder()
              .from(string)
              .codelistUri(codelistUri)
              .build();
        } else if (schema instanceof JsonSchemaInteger integer) {
          return new ImmutableJsonSchemaInteger.Builder()
              .from(integer)
              .codelistUri(codelistUri)
              .build();
        } else if (schema instanceof JsonSchemaOneOf oneOf) {
          return new ImmutableJsonSchemaOneOf.Builder()
              .from(oneOf)
              .codelistUri(codelistUri)
              .build();
        }
      } catch (URISyntaxException e) {
        // ignore
      }
    }

    return visitProperties(schema);
  }
}
