/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.domain;

import com.github.azahnen.dagger.annotations.AutoMultiBind;
import de.ii.ogcapi.foundation.domain.ApiExtension;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import java.util.Optional;

/**
 * Supplies a value for the {@code datetime} query parameter on a Features resource when the client
 * did not supply one.
 *
 * <p>Implementations are consulted in registry order; the first non-empty result wins. The returned
 * value is then run through the standard {@code datetime} parser, so {@code "now"} or any other
 * legal {@code datetime} value is acceptable.
 *
 * <p>Consulted both at request time (to supply the parsed default value) and at OpenAPI generation
 * (to surface the default in the parameter schema), so implementations must produce a string
 * suitable for both — typically a symbolic value such as {@code "now"} rather than a fixed
 * timestamp.
 */
@AutoMultiBind
public interface DatetimeDefaultProvider extends ApiExtension {

  Optional<String> getDefault(OgcApiDataV2 apiData, FeatureTypeConfigurationOgcApi collectionData);
}
