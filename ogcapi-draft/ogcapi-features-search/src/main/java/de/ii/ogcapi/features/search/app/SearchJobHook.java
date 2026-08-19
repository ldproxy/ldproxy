/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import de.ii.xtraplatform.features.domain.JobHook;
import de.ii.xtraplatform.xtralink.domain.JobProcessing;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reports the progress from a {@link JobHook} into the job's progress details. The total is only
 * known once the feature pipeline runs ({@code numberReturned} for paged queries, the sub-query
 * count otherwise).
 *
 * <p>TODO: canceling a job is not yet supported — the job queue has no cancel operation so far;
 * once it does, {@link #isCancelRequested()} is the place to surface it (the pipeline already
 * checkpoints per feature).
 *
 * <p>The job's progress counters are deliberately not touched: the execute partial carries a total
 * of one unit, and the backend's failure handling tops a partial up by {@code total - current} —
 * counters driven beyond the partial's total would be corrected downwards and leave the job
 * permanently unfinished. Until the queue exposes an update on the job itself, fine-grained
 * progress therefore goes into the details ({@code init} with a total delta of zero), which is safe
 * on every outcome.
 *
 * <p>Updates are throttled to roughly one percent steps to keep the per-feature overhead low.
 */
public class SearchJobHook implements JobHook {

  private final JobProcessing jobs;
  private final String jobId;
  private final AtomicInteger total;
  private final AtomicLong current;
  private final AtomicLong lastReported;

  public SearchJobHook(JobProcessing jobs, String jobId) {
    this.jobs = jobs;
    this.jobId = jobId;
    this.total = new AtomicInteger(0);
    this.current = new AtomicLong(0);
    this.lastReported = new AtomicLong(0);
  }

  @Override
  public void init(int total) {
    this.total.set(total);
    jobs.init(jobId, 0, details(0, total));
  }

  @Override
  public void update(int delta) {
    long now = current.addAndGet(delta);
    long step = Math.max(1, total.get() / 100);
    long last = lastReported.get();
    if (now - last >= step && lastReported.compareAndSet(last, now)) {
      jobs.init(jobId, 0, details(now, total.get()));
    }
  }

  /** Writes the final details; idempotent, must run on every exit path. */
  public void finish() {
    long now = current.get();
    if (lastReported.getAndSet(now) != now || now == 0) {
      jobs.init(jobId, 0, details(now, total.get()));
    }
  }

  private static Map<String, Object> details(long current, int total) {
    return Map.of("featuresProcessed", current, "featuresTotal", total);
  }
}
