/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.resources.domain;

import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.PermissionGroup;
import de.ii.ogcapi.foundation.domain.PermissionGroup.Base;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.WithDryRun;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.io.InputStream;
import org.immutables.value.Value;

public interface QueriesHandlerResources
    extends QueriesHandler<QueriesHandlerResources.Query>, Volatile2 {

  String GROUP_RESOURCES = "resources";
  PermissionGroup GROUP_RESOURCES_READ =
      PermissionGroup.of(Base.READ, GROUP_RESOURCES, "access file resources");
  PermissionGroup GROUP_RESOURCES_WRITE =
      PermissionGroup.of(Base.WRITE, GROUP_RESOURCES, "mutate file resources");

  enum Query implements QueryIdentifier {
    RESOURCES,
    CREATE_REPLACE,
    DELETE,
    RESOURCE
  }

  @Value.Immutable
  interface QueryInputResources extends QueryInput {
    boolean getIncludeLinkHeader();
  }

  @Value.Immutable
  interface QueryInputResource extends QueryInput {
    String getResourceId();
  }

  @Value.Immutable
  @Value.Style(builder = "new")
  interface QueryInputResourceCreateReplace extends QueryInput, WithDryRun {
    String getResourceId();

    InputStream getRequestBody();

    boolean getStrict();
  }

  @Value.Immutable
  @Value.Style(builder = "new")
  interface QueryInputResourceDelete extends QueryInput {
    String getResourceId();

    OgcApi getDataset();
  }
}
