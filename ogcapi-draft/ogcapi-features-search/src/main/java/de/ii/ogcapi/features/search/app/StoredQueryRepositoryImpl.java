/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.search.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import de.ii.ogcapi.features.search.domain.ImmutableStoredQueryValue;
import de.ii.ogcapi.features.search.domain.StoredQueriesFormat;
import de.ii.ogcapi.features.search.domain.StoredQueryExpression;
import de.ii.ogcapi.features.search.domain.StoredQueryFormat;
import de.ii.ogcapi.features.search.domain.StoredQueryRepository;
import de.ii.ogcapi.features.search.domain.StoredQueryValidator;
import de.ii.ogcapi.features.search.domain.StoredQueryValue;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.SchemaValidator;
import de.ii.xtraplatform.base.domain.AppLifeCycle;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatile;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.cql.domain.Cql;
import de.ii.xtraplatform.entities.domain.ImmutableValidationResult;
import de.ii.xtraplatform.values.domain.KeyValueStore;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.web.domain.LastModified;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@AutoBind
public class StoredQueryRepositoryImpl extends AbstractVolatile
    implements StoredQueryRepository, AppLifeCycle {

  private static final Logger LOGGER = LoggerFactory.getLogger(StoredQueryRepositoryImpl.class);

  private final ExtensionRegistry extensionRegistry;
  private final KeyValueStore<StoredQueryValue> queriesStore;
  private final VolatileRegistry volatileRegistry;
  private final SchemaValidator schemaValidator;
  private final Cql cql;

  @Inject
  public StoredQueryRepositoryImpl(
      ValueStore valueStore,
      ExtensionRegistry extensionRegistry,
      VolatileRegistry volatileRegistry,
      SchemaValidator schemaValidator,
      Cql cql) {
    super(volatileRegistry, "app/storedqueries");
    this.extensionRegistry = extensionRegistry;
    this.queriesStore = valueStore.forTypeWritable(StoredQueryValue.class);
    this.volatileRegistry = volatileRegistry;
    this.schemaValidator = schemaValidator;
    this.cql = cql;
  }

  @Override
  public CompletionStage<Void> onStart(boolean isStartupAsync) {
    onVolatileStart();
    return volatileRegistry.onAvailable(queriesStore).thenRun(() -> setState(State.AVAILABLE));
  }

  @Override
  public Stream<StoredQueriesFormat> getStoredQueriesFormatStream(OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(StoredQueriesFormat.class).stream()
        .filter(format -> format.isEnabledForApi(apiData));
  }

  @Override
  public Stream<StoredQueryFormat> getStoredQueryFormatStream(OgcApiDataV2 apiData) {
    return extensionRegistry.getExtensionsForType(StoredQueryFormat.class).stream()
        .filter(format -> format.isEnabledForApi(apiData));
  }

  @Override
  public List<ApiMediaType> getStoredQueryMediaTypes(OgcApiDataV2 apiData, String queryId) {
    if (!exists(apiData, queryId)) {
      return ImmutableList.of();
    }

    return getStoredQueryFormatStream(apiData)
        .map(StoredQueryFormat::getMediaType)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public List<StoredQueryExpression> getAll(OgcApiDataV2 apiData) {
    return queriesStore.identifiers(apiData.getId()).stream()
        .map(queriesStore::get)
        .map(this::from)
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public StoredQueryExpression get(OgcApiDataV2 apiData, String queryId) {
    if (!exists(apiData, queryId)) {
      throw new NotFoundException(
          MessageFormat.format("The stored query ''{0}'' does not exist in this API.", queryId));
    }

    return from(queriesStore.get(queryId, apiData.getId()));
  }

  @Override
  public boolean exists(OgcApiDataV2 apiData, String queryId) {
    return queriesStore.has(queryId, apiData.getId());
  }

  @Override
  public Date getLastModified(OgcApiDataV2 apiData, String queryId) {
    if (exists(apiData, queryId)) {
      return LastModified.from(queriesStore.lastModified(queryId, apiData.getId()));
    }
    return null;
  }

  @Override
  public Date getLastModified(OgcApiDataV2 apiData) {
    return queriesStore.identifiers(apiData.getId()).stream()
        .map(queriesStore::lastModified)
        .max(Comparator.naturalOrder())
        .map(LastModified::from)
        .orElse(null);
  }

  @Override
  public ImmutableValidationResult.Builder validate(
      ImmutableValidationResult.Builder builder, OgcApiDataV2 apiData) {
    queriesStore
        .identifiers(apiData.getId())
        .forEach(
            identifier -> {
              try {
                StoredQueryExpression query = from(queriesStore.get(identifier));

                List<String> errors =
                    query.accept(
                        new StoredQueryValidator(query.getParameters(), schemaValidator, cql));
                builder.addAllErrors(errors);
              } catch (Throwable e) {
                builder.addErrors(
                    MessageFormat.format(
                        "Could not parse stored query ''{0}''. Reason: {1}.",
                        identifier.asPath(), e.getMessage()));
              }
            });

    return builder;
  }

  @Override
  public Set<String> getIds(OgcApiDataV2 apiData) {
    return getAll(apiData).stream()
        .map(StoredQueryExpression::getId)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public void writeStoredQueryDocument(
      OgcApiDataV2 apiData, String queryId, StoredQueryExpression query) throws IOException {
    try {
      queriesStore.put(queryId, to(query), apiData.getId()).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
  }

  @Override
  public void deleteStoredQuery(OgcApiDataV2 apiData, String queryId) throws IOException {
    try {
      queriesStore.delete(queryId, apiData.getId()).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
  }

  private StoredQueryExpression from(StoredQueryValue value) {
    return StoredQueryExpression.from(value, cql);
  }

  private StoredQueryValue to(StoredQueryExpression expression) {
    return new ImmutableStoredQueryValue.Builder().from(expression).build();
  }
}
