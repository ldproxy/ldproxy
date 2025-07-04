/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.codelists.domain;

import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import org.immutables.value.Value;

public interface QueriesHandlerCodelists
    extends QueriesHandler<QueriesHandlerCodelists.Query>, Volatile2 {

  String GROUP_CODELISTS = "codelists";
  PermissionGroup GROUP_CODELISTS_READ =
      PermissionGroup.of(Base.READ, GROUP_CODELISTS, "access codelists");

  enum Query implements QueryIdentifier {
    CODELISTS,
    CODELIST
  }

  @Value.Immutable
  interface QueryInputCodelists extends QueryInput {}

  @Value.Immutable
  interface QueryInputCodelist extends QueryInput {
    String getCodelistId();
  }
}
