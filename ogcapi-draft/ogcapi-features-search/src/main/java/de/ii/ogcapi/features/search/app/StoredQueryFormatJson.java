/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryFormat;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;

@Singleton
@AutoBind
public class StoredQueryFormatJson implements StoredQueryFormat {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(MediaType.APPLICATION_JSON_TYPE)
          .label("JSON")
          .parameter("json")
          .fileExtension("json")
          .build();

  private final Schema<?> schema;
  private final Map<String, Schema<?>> referencedSchemas;

  @Inject
  public StoredQueryFormatJson(ClassSchemaCache classSchemaCache) {
    this.schema = classSchemaCache.getSchema(StoredQueryExpression.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(StoredQueryExpression.class);
  }

  @Override
  public boolean canSupportTransactions() {
    return true;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schema)
        .schemaRef(StoredQueryExpression.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(MEDIA_TYPE)
        .build();
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public String getFileExtension() {
    return "json";
  }

  @Override
  public Object getEntity(
      StoredQueryExpression query, OgcApiDataV2 apiData, ApiRequestContext requestContext) {
    return query;
  }
}
