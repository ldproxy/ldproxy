/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.xtralink.domain.JobProcessorBase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class MockAnswerProcessor extends MockOapJobProcessor {

  @Inject
  MockAnswerProcessor(ResourceStore resourceStore) {
    super("AnswerProcess", resourceStore);
  }

  @Override
  protected Map<String, Object> execute(Map<String, Object> inputs) {
    return Map.of("answer", 42);
  }
}
