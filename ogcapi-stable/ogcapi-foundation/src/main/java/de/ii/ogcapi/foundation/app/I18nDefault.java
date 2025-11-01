/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.foundation.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.LogContext;
import de.ii.xtraplatform.blobs.domain.Blob;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class I18nDefault implements I18n, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(I18nDefault.class);

  private static final String I18N_DIR_NAME = "i18n";
  private static final String HTML_DIR_NAME = "html";
  private static final Pattern I18N_FILE_PATTERN =
      Pattern.compile("i18n_([0-9a-zA-Z]{2,8})\\.properties");
  private static final Map<Locale, ResourceBundle> PREDEFINED_BUNDLES =
      Map.of(
          Locale.ENGLISH, ResourceBundle.getBundle("i18n", Locale.ENGLISH),
          Locale.GERMAN, ResourceBundle.getBundle("i18n", Locale.GERMAN));

  private final Map<Locale, ResourceBundle> i18nBundles;
  private final ResourceStore i18nStore;

  @Inject
  public I18nDefault(ResourceStore blobStore) {
    this.i18nBundles = new HashMap<>(PREDEFINED_BUNDLES);
    this.i18nStore = blobStore.with(HTML_DIR_NAME, I18N_DIR_NAME);
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    i18nStore.onReady().thenRunAsync(this::init, Executors.newSingleThreadExecutor()).join();

    return CompletableFuture.completedFuture(null);
  }

  private void init() {
    try {
      i18nStore
          .walk(
              Path.of(""), 1, (path, attributes) -> attributes.isValue() && !attributes.isHidden())
          .forEach(
              path -> {
                Matcher matcher = I18N_FILE_PATTERN.matcher(path.toString());
                if (!matcher.matches()) {
                  LOGGER.warn("Ignoring invalid i18n file: {}", path);
                  return;
                }

                Locale locale = Locale.forLanguageTag(matcher.group(1));
                try {
                  i18nBundles.put(
                      locale,
                      new CustomBundle(
                          i18nStore.get(path).get(),
                          PREDEFINED_BUNDLES.getOrDefault(
                              locale, PREDEFINED_BUNDLES.get(Locale.ENGLISH))));

                  if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Loaded i18n file: {}", path);
                  }
                } catch (IOException e) {
                  LogContext.errorAsWarn(LOGGER, e, "Could not load i18n file: {}", path);
                }
              });
    } catch (Throwable e) {
      LogContext.errorAsWarn(LOGGER, e, "Could not load custom i18n files");
    }
  }

  @Override
  public Set<Locale> getLanguages() {
    return i18nBundles.keySet();
  }

  @Override
  public String get(String key) {
    return get(key, Optional.of(Locale.ENGLISH));
  }

  @Override
  public String get(String key, Optional<Locale> language) {
    try {
      return i18nBundles.get(language.orElse(Locale.ENGLISH)).getString(key);
    } catch (MissingResourceException ex) {
      // just return the key
      return key;
    }
  }

  @Override
  public Set<String> getKeys() {
    return PREDEFINED_BUNDLES.get(Locale.ENGLISH).keySet();
  }

  @Override
  public Set<String> getKeysWithPrefix(String prefix) {
    return getKeys().stream().filter(key -> key.startsWith(prefix)).collect(Collectors.toSet());
  }

  static class CustomBundle extends PropertyResourceBundle {

    public CustomBundle(Blob blob, ResourceBundle parent) throws IOException {
      super(new ByteArrayInputStream(blob.content()));
      setParent(parent);
    }
  }
}
