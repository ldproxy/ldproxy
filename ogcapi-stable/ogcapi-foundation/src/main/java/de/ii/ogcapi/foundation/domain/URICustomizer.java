/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.domain;

import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

/**
 * @author zahnen
 */
@SuppressWarnings({
  "PMD.TooManyMethods",
  "PMD.GodClass"
}) // this class needs that many methods, a refactoring makes no sense
public class URICustomizer extends URIBuilder {
  public URICustomizer() {
    super();
  }

  public URICustomizer(String string) throws URISyntaxException {
    super(string);
  }

  public URICustomizer(URI uri) {
    super(uri);
  }

  public URICustomizer copy() {
    try {
      return new URICustomizer(this.toString());
    } catch (URISyntaxException e) {
      // ignore
    }
    return this;
  }

  @Override
  public URICustomizer clearParameters() {
    super.clearParameters();
    return this;
  }

  @Override
  public URICustomizer setScheme(String scheme) {
    super.setScheme(scheme);
    return this;
  }

  @Override
  public URICustomizer setPathSegments(List<String> pathSegments) {
    super.setPathSegments(pathSegments);
    return this;
  }

  public URICustomizer ensureParameter(final String parameter, final String value) {
    if (this.getQueryParams().stream()
        .noneMatch(nameValuePair -> nameValuePair.getName().equals(parameter))) {
      this.setParameter(parameter, value);
    }
    return this;
  }

  public URICustomizer removeParameters(final String... parameters) {
    final List<String> parameterList = Arrays.asList(parameters);
    final List<NameValuePair> queryParams =
        this.getQueryParams().stream()
            .filter(nameValuePair -> !parameterList.contains(nameValuePair.getName()))
            .collect(Collectors.toList());

    this.setParameters(queryParams);

    return this;
  }

  public String getLastPathSegment() {
    final List<String> pathSegments = getPathSegments();

    return pathSegments.isEmpty() ? null : pathSegments.get(pathSegments.size() - 1);
  }

  public URICustomizer prependPathSegments(final String... segments) {
    return prependPathSegments(List.of(segments));
  }

  public URICustomizer prependPathSegments(final Iterable<String> segments) {
    final List<String> pathSegments = getPathSegments();

    this.setPathSegments(
        new ImmutableList.Builder<String>().addAll(segments).addAll(pathSegments).build());

    return this;
  }

  public URICustomizer ensureLastPathSegment(final String segment) {
    final List<String> pathSegments = getPathSegments();

    if (pathSegments.isEmpty() || !pathSegments.get(pathSegments.size() - 1).equals(segment)) {
      this.setPathSegments(
          new ImmutableList.Builder<String>().addAll(pathSegments).add(segment).build());
    }
    return this;
  }

  public URICustomizer ensureLastPathSegments(final String... segments) {
    List<String> pathSegments = getPathSegments();

    if (pathSegments.size() == 1 && pathSegments.get(0).isEmpty()) {
      pathSegments = List.of();
    }

    int pathSegmentsIndex =
        pathSegments.size() >= segments.length ? pathSegments.size() - segments.length : 0;

    boolean match = true;
    int restIndex = 0;
    for (int k = pathSegmentsIndex; k < pathSegments.size(); k++) {
      for (int l = 0; k + l < pathSegments.size(); l++) {
        if (!pathSegments.get(k + l).equals(segments[l])) {
          match = false;
          break;
        }
        restIndex = l + 1;
      }
      if (match) {
        break;
      } else {
        match = true;
      }
    }

    int segmentsIndex = match ? restIndex : 0;

    this.setPathSegments(
        new ImmutableList.Builder<String>()
            .addAll(pathSegments)
            .addAll(Arrays.asList(segments).subList(segmentsIndex, segments.length))
            .build());

    return this;
  }

  public URICustomizer cutPathAfterSegments(final String... segments) {
    if (segments.length <= 0) {
      return this;
    }

    final List<String> pathSegments = getPathSegments();
    int pathSegmentsIndex = -1;

    for (int i = 0; i <= pathSegments.size() - segments.length; i++) {
      boolean found = true;
      for (int j = 0; j < segments.length; j++) {
        if (!pathSegments.get(i + j).equals(segments[j])) {
          found = false;
          break;
        }
      }
      if (found) {
        pathSegmentsIndex = i;
      }
    }

    if (pathSegmentsIndex == -1) {
      return this;
    }

    boolean match = true;
    int cutIndex = 0;
    for (int k = pathSegmentsIndex; k < pathSegments.size(); k++) {
      for (int l = 0; k + l < pathSegments.size() && l < segments.length; l++) {
        if (!pathSegments.get(k + l).equals(segments[l])) {
          match = false;
          break;
        }
        cutIndex = k + l + 1;
      }
      if (match) {
        break;
      } else {
        match = true;
      }
    }

    int segmentsIndex = match ? cutIndex : pathSegments.size();

    this.setPathSegments(
        new ImmutableList.Builder<String>().addAll(pathSegments.subList(0, segmentsIndex)).build());

    return this;
  }

  public URICustomizer removePathSegment(final String segment, final int index) {
    final List<String> pathSegments = getPathSegments();
    final int i = index < 0 ? pathSegments.size() + index : index;

    if (i >= 0 && i < pathSegments.size() && pathSegments.get(i).equals(segment)) {
      this.setPathSegments(
          new ImmutableList.Builder<String>()
              .addAll(pathSegments.subList(0, i))
              .addAll(pathSegments.subList(i + 1, pathSegments.size()))
              .build());
    }
    return this;
  }

  public URICustomizer removeLastPathSegment(final String segment) {
    final List<String> pathSegments = getPathSegments();

    if (!pathSegments.isEmpty() && pathSegments.get(pathSegments.size() - 1).equals(segment)) {
      this.setPathSegments(pathSegments.subList(0, pathSegments.size() - 1));
    }
    return this;
  }

  public URICustomizer removeLastPathSegments(final int numberOfSegments) {
    final List<String> pathSegments = getPathSegments();

    if (pathSegments.size() >= numberOfSegments) {
      this.setPathSegments(pathSegments.subList(0, pathSegments.size() - numberOfSegments));
    }

    return this;
  }

  public URICustomizer replaceInPath(String original, String replacement) {
    this.setPath(this.getPath().replaceFirst(original, replacement));
    return this;
  }

  public URICustomizer ensureTrailingSlash() {
    if (Objects.nonNull(this.getPath()) && !this.getPath().endsWith("/")) {
      this.setPath(this.getPath() + "/");
    }
    return this;
  }

  public URICustomizer ensureNoTrailingSlash() {
    if (Objects.nonNull(this.getPath()) && this.getPath().endsWith("/")) {
      this.setPath(this.getPath().substring(0, getPath().length() - 1));
    }
    return this;
  }
}
