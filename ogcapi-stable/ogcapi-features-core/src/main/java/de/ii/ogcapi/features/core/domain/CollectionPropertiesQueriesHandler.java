/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import de.ii.ogcapi.features.core.app.CollectionPropertiesQueriesHandlerImpl;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.WithProfiles;
import org.immutables.value.Value;

public interface CollectionPropertiesQueriesHandler
    extends QueriesHandler<CollectionPropertiesQueriesHandlerImpl.Query> {

  enum Query implements QueryIdentifier {
    COLLECTION_PROPERTIES
  }

  @Value.Immutable
  interface QueryInputCollectionProperties extends QueryInput, WithProfiles {
    String getCollectionId();

    CollectionPropertiesType getType();

    JsonSchemaCache getSchemaCache();
  }
}
