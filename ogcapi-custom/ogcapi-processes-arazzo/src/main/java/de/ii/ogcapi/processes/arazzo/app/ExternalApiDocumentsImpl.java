/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** The OpenAPI documents of external APIs in memory, until they expire. */
@Singleton
@AutoBind
public class ExternalApiDocumentsImpl implements ExternalApiDocuments {

  private record Entry(Document document, long expires) {}

  private final Map<String, Entry> entries;
  private final LongSupplier clock;

  @Inject
  public ExternalApiDocumentsImpl() {
    this(System::currentTimeMillis);
  }

  ExternalApiDocumentsImpl(LongSupplier clock) {
    this.entries = new ConcurrentHashMap<>();
    this.clock = clock;
  }

  @Override
  public Optional<Document> get(String key) {
    Entry entry = entries.get(key);
    if (Objects.isNull(entry)) {
      return Optional.empty();
    }
    if (entry.expires() <= clock.getAsLong()) {
      entries.remove(key, entry);
      return Optional.empty();
    }
    return Optional.of(entry.document());
  }

  @Override
  public void put(String key, Document document, long seconds) {
    long now = clock.getAsLong();
    // expired documents are removed when another one is added, the number of keys is small
    entries.values().removeIf(entry -> entry.expires() <= now);
    if (seconds > 0) {
      entries.put(key, new Entry(document, now + seconds * 1000));
    }
  }
}
