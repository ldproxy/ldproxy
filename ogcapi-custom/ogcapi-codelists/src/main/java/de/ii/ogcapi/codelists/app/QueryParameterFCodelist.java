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
import de.ii.ogcapi.codelists.domain.CodelistsConfiguration;
import de.ii.ogcapi.common.domain.QueryParameterF;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @langEn Select the output format of the response. If no value is provided, the standard HTTP
 *     rules apply, i.e., the "Accept" header will be used to determine the format.
 * @langDe Wählt das Ausgabeformat der Antwort. Wenn kein Wert angegeben wird, gelten die
 *     Standard-HTTP Regeln, d.h. der "Accept"-Header wird zur Bestimmung des Formats verwendet.
 * @title f
 * @endpoints Codelist
 */
@Singleton
@AutoBind
public class QueryParameterFCodelist extends QueryParameterF {

  @Inject
  public QueryParameterFCodelist(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    super(extensionRegistry, schemaValidator);
  }

  @Override
  public String getId() {
    return "fCodelist";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return definitionPath.equals("/codelists/{codelistId}");
  }

  @Override
  protected Class<? extends FormatExtension> getFormatClass() {
    return CodelistFormatExtension.class;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return CodelistsConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return CodelistsBuildingBlock.MATURITY;
  }
}
