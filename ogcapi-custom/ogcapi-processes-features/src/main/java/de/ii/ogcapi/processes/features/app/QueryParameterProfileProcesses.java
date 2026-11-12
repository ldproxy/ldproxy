/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.common.domain.QueryParameterProfile;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.ogcapi.foundation.domain.SpecificationMaturity;
import de.ii.ogcapi.processes.features.domain.ProcessesFeaturesConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * @title profile
 * @endpoints processes/{processId}/execution
 * @langEn The profiles of the features that a process returns, e.g. how their references are
 *     encoded, as with the parameter `profile` of `/collections/{collectionId}/items`. A profile in
 *     the parameters of the source of the features takes precedence. The parameter applies to the
 *     synchronous and the asynchronous execution.
 * @langDe Die Profile der Features, die ein Prozess liefert, z.B. wie ihre Referenzen kodiert
 *     werden, wie beim Parameter `profile` von `/collections/{collectionId}/items`. Ein Profil in
 *     den Parametern der Quelle der Features hat Vorrang. Der Parameter gilt für die synchrone und
 *     die asynchrone Ausführung.
 */
@Singleton
@AutoBind
public class QueryParameterProfileProcesses extends QueryParameterProfile {

  @Inject
  QueryParameterProfileProcesses(
      ExtensionRegistry extensionRegistry, SchemaValidator schemaValidator) {
    super(extensionRegistry, schemaValidator);
  }

  @Override
  public String getId() {
    return "profileProcesses";
  }

  @Override
  public String getDescription() {
    return "The profiles of the features that the process returns, e.g. how their references are"
        + " encoded, as with the parameter 'profile' of /collections/{collectionId}/items. A"
        + " profile in the parameters of the source of the features takes precedence.";
  }

  // the execution endpoint asks for its parameters as for a GET request
  @Override
  public boolean matchesPath(String definitionPath) {
    return "/processes/{processId}/execution".equals(definitionPath);
  }

  @Override
  public ResourceType getResourceType() {
    return ResourceType.FEATURE;
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesFeaturesConfiguration.class;
  }

  @Override
  public Optional<SpecificationMaturity> getSpecificationMaturity() {
    return Optional.of(SpecificationMaturity.DRAFT_LDPROXY);
  }
}
