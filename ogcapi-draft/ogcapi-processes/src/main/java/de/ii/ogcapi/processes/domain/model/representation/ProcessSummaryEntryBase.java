/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.domain.model.representation;

import com.google.common.hash.Funnel;
import de.ii.ogcapi.foundation.domain.PageRepresentationWithId;
import de.ii.ogcapi.processes.domain.model.ProcessSummary;
import java.nio.charset.StandardCharsets;

public abstract class ProcessSummaryEntryBase extends PageRepresentationWithId
    implements ProcessSummary {

  @SuppressWarnings("UnstableApiUsage")
  public static final Funnel<ProcessSummaryEntryBase> FUNNEL =
      (from, into) -> {
        PageRepresentationWithId.FUNNEL.funnel(from, into);
        into.putString(from.getVersion(), StandardCharsets.UTF_8);
        from.getJobControlOptions()
            .ifPresent(
                l ->
                    l.stream()
                        .map(JOB_CONTROL_OPTIONS::name)
                        .sorted()
                        .forEachOrdered(name -> into.putString(name, StandardCharsets.UTF_8)));
        from.getKeywords()
            .ifPresent(
                l ->
                    l.stream()
                        .sorted()
                        .forEachOrdered(
                            keyword -> into.putString(keyword, StandardCharsets.UTF_8)));
      };
}
