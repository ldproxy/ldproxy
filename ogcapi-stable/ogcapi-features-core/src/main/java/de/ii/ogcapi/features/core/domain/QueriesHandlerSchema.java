/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.features.core.domain.QueriesHandlerSchema.Query;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.WithProfiles;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import org.immutables.value.Value;

public interface QueriesHandlerSchema extends QueriesHandler<Query>, Volatile2 {

  enum Query implements QueryIdentifier {
    SCHEMA
  }

  @Value.Immutable
  interface QueryInputSchema extends QueryInput, WithProfiles {
    String getCollectionId();

    SchemaType getType();

    JsonSchemaCache getSchemaCache();
  }
}
