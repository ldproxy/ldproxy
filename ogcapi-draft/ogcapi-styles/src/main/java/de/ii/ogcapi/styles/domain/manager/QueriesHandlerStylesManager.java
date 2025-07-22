/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.domain.manager;

import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryIdentifier;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.WithDryRun;
import de.ii.xtraplatform.base.domain.resiliency.Volatile2;
import java.util.Optional;
import javax.ws.rs.core.MediaType;
import org.immutables.value.Value;

public interface QueriesHandlerStylesManager
    extends QueriesHandler<QueriesHandlerStylesManager.Query>, Volatile2 {

  enum Query implements QueryIdentifier {
    CREATE_STYLE,
    REPLACE_STYLE,
    DELETE_STYLE,
    REPLACE_STYLE_METADATA,
    UPDATE_STYLE_METADATA
  }

  @Value.Immutable
  interface QueryInputStyleCreateReplace extends QueryInput, WithDryRun {
    Optional<String> getCollectionId();

    Optional<String> getStyleId();

    MediaType getContentType();

    byte[] getRequestBody();

    boolean getStrict();

    // temp
    @Value.Default
    default boolean getDryRun() {
      return false;
    }
  }

  @Value.Immutable
  interface QueryInputStyleDelete extends QueryInput {
    Optional<String> getCollectionId();

    String getStyleId();
  }

  @Value.Immutable
  interface QueryInputStyleMetadata extends QueryInput, WithDryRun {
    Optional<String> getCollectionId();

    String getStyleId();

    MediaType getContentType();

    byte[] getRequestBody();

    boolean getStrict();
  }
}
