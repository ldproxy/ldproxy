/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

/**
 * Data outside the API configuration that API definitions are derived from and that can change
 * while the API is running, for example codelists or stored queries.
 */
// an extension that is registered with the other API extensions, not a lambda target
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface ApiDefinitionDependency extends ApiExtension {

  /**
   * @return a revision that changes whenever the data changes, cached API definitions are derived
   *     again then
   */
  long getRevision();
}
