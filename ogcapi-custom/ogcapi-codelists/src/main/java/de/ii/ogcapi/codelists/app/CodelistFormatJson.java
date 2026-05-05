/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.codelists.domain.CodelistFormatExtension;
import de.ii.ogcapi.codelists.domain.Codelists;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ClassSchemaCache;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.xtraplatform.codelists.domain.Codelist;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

/**
 * @title JSON
 */
@Singleton
@AutoBind
public class CodelistFormatJson implements CodelistFormatExtension {

  private final Schema<?> schemaCodelist;
  private final Map<String, Schema<?>> referencedSchemas;

  @Inject
  CodelistFormatJson(ClassSchemaCache classSchemaCache) {
    schemaCodelist = classSchemaCache.getSchema(Codelist.class);
    referencedSchemas = classSchemaCache.getReferencedSchemas(Codelist.class);
  }

  @Override
  public ApiMediaType getMediaType() {
    return ApiMediaType.JSON_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return new ImmutableApiMediaTypeContent.Builder()
        .schema(schemaCodelist)
        .schemaRef(Codelists.SCHEMA_REF)
        .referencedSchemas(referencedSchemas)
        .ogcApiMediaType(getMediaType())
        .build();
  }

  @Override
  public Object getCodelist(
      Codelist codelist,
      String codelistId,
      OgcApiDataV2 apiData,
      ApiRequestContext requestContext,
      List<Link> links) {
    return codelist;
  }
}
