/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles3d.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.common.domain.GenericFormatExtension;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.xtraplatform.tiles3d.domain.spec.Tileset3d;
import java.util.List;

@AutoMultiBind
public interface Format3dTilesTileset extends GenericFormatExtension {

  Object getEntity(
      Tileset3d tileset,
      List<Link> links,
      String collectionId,
      OgcApi api,
      ApiRequestContext requestContext);
}
