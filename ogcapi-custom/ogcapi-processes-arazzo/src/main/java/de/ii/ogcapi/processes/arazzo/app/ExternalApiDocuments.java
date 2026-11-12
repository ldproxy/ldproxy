/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.arazzo.app;

import java.util.Map;
import java.util.Optional;

/**
 * The OpenAPI documents of external APIs, kept across the executions of workflows as long as
 * allowed.
 */
public interface ExternalApiDocuments {

  /**
   * An OpenAPI document and the URL from which it was read, after redirects; relative server URLs
   * are relative to it. A class, not a record: the documentation tool of the build does not support
   * public records.
   */
  final class Document {

    private final String location;
    private final Map<?, ?> content;

    public Document(String location, Map<?, ?> content) {
      this.location = location;
      this.content = content;
    }

    public String location() {
      return location;
    }

    public Map<?, ?> content() {
      return content;
    }
  }

  /** No documents are kept. */
  ExternalApiDocuments NONE =
      new ExternalApiDocuments() {
        @Override
        public Optional<Document> get(String key) {
          return Optional.empty();
        }

        @Override
        public void put(String key, Document document, long seconds) {}
      };

  /**
   * @return the document, if it is kept and has not expired
   */
  Optional<Document> get(String key);

  /**
   * @param seconds how long the document is kept; nothing is kept with 0 or less
   */
  void put(String key, Document document, long seconds);
}
