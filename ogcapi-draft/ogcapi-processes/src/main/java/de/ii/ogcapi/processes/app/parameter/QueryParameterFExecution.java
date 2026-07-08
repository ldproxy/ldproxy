/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app.parameter;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterF;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FormatExtension;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.processes.domain.ProcessesCoreConfiguration;
import de.ii.ogcapi.processes.domain.format.ExecuteResponseBodyFormatExtension;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

// ToDo docs
@Singleton
@AutoBind
public class QueryParameterFExecution extends QueryParameterF {

  @Inject
  public QueryParameterFExecution(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    super(extensionRegistry, schemaValidator);
  }

  @Override
  public String getId() {
    return "fExecution";
  }

  @Override
  public boolean matchesPath(String definitionPath) {
    return "/processes/{processId}/execution".equals(definitionPath);
  }

  @Override
  protected Class<? extends FormatExtension> getFormatClass() {
    return ExecuteResponseBodyFormatExtension.class;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesCoreConfiguration.class;
  }
}
