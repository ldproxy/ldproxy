/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.ApiDefinitionDependency;
import de.ii.ogcapi.processes.arazzo.domain.ArazzoDocument;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The Arazzo workflows: each workflow is a process of the API, the API definition is therefore
 * derived again when the workflows change.
 */
@Singleton
@AutoBind
public class ApiDefinitionDependencyWorkflows implements ApiDefinitionDependency {

  private final Values<ArazzoDocument> workflows;

  @Inject
  public ApiDefinitionDependencyWorkflows(ValueStore valueStore) {
    this.workflows = valueStore.forType(ArazzoDocument.class);
  }

  @Override
  public long getRevision() {
    return workflows.getRevision();
  }
}
