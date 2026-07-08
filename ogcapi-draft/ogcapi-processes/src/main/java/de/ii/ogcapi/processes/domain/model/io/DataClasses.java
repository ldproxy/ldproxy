/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.io;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface DataClasses {

  Optional<List<URI>> getDataClasses();
}
