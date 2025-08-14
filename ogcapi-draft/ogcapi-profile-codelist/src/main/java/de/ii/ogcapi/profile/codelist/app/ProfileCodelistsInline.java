/*
 * Copyright 2025 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.profile.codelist.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaConstant;
import de.ii.ogcapi.features.core.domain.ImmutableJsonSchemaOneOf;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaInteger;
import de.ii.ogcapi.features.core.domain.JsonSchemaString;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class ProfileCodelistsInline extends ProfileCodelist {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCodelistsInline.class);

  private final Values<Codelist> codelists;

  @Inject
  ProfileCodelistsInline(ExtensionRegistry extensionRegistry, ValueStore valueStore) {
    super(extensionRegistry);
    this.codelists = valueStore.forType(Codelist.class);
  }

  @Override
  public String getId() {
    return "codelists-inline";
  }

  @Override
  public JsonSchema process(JsonSchema schema, String codelistId) {
    if (schema instanceof JsonSchemaString || schema instanceof JsonSchemaInteger) {
      Codelist codelist = codelists.get(Identifier.from(Path.of(codelistId)));
      if (codelist == null) {
        if (LOGGER.isWarnEnabled()) {
          LOGGER.warn("Codelist with ID '{}' not found, returning original schema.", codelistId);
        }
        return schema;
      }

      final List<String> enums =
          schema instanceof JsonSchemaString
              ? ((JsonSchemaString) schema).getEnums()
              : ((JsonSchemaInteger) schema).getEnums().stream().map(String::valueOf).toList();
      final boolean allValues = enums.isEmpty();

      ImmutableJsonSchemaOneOf.Builder builder =
          new ImmutableJsonSchemaOneOf.Builder().from(schema);

      codelist
          .getEntries()
          .forEach(
              (code, title) -> {
                if (allValues || enums.contains(code)) {
                  if (schema instanceof JsonSchemaString) {
                    builder.addOneOf(
                        new ImmutableJsonSchemaConstant.Builder()
                            .constant(code)
                            .title(title)
                            .build());
                  } else {
                    builder.addOneOf(
                        new ImmutableJsonSchemaConstant.Builder()
                            .constant(Integer.parseInt(code))
                            .title(title)
                            .build());
                  }
                }
              });

      return builder.build();
    } else {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn(
            "Codelist processing is only supported for string or integer schemas, returning original schema.");
      }
      return schema;
    }
  }
}
