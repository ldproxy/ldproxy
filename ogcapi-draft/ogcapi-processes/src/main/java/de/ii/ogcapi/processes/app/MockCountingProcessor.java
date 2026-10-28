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
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Singleton
@AutoBind(interfaces = JobProcessorBase.class)
public class MockCountingProcessor extends MockOapJobProcessor {

  @Inject
  MockCountingProcessor(ResourceStore resourceStore) {
    super("CountingProcess", resourceStore);
  }

  @Override
  protected Map<String, Object> execute(Map<String, Object> inputs) {
    int n = (Integer) inputs.get("N");
    List<Integer> numbers = IntStream.rangeClosed(1, n).boxed().toList();
    return Map.of("numbers", numbers);
  }
}
