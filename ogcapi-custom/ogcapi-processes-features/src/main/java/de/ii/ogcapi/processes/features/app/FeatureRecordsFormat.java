/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.processes.features.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import de.ii.ogcapi.features.core.domain.FeatureFormatExtension;
import de.ii.ogcapi.features.core.domain.FeatureTransformationContext;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiMediaTypeContent;
import de.ii.ogcapi.foundation.domain.ExtensionConfiguration;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.ImmutableApiMediaType;
import de.ii.ogcapi.processes.features.domain.ProcessesFeaturesConfiguration;
import de.ii.xtraplatform.features.domain.FeatureTokenEncoder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.core.MediaType;
import java.util.Locale;
import java.util.Optional;

/**
 * An internal feature format of the processes of this building block: one line of JSON per feature
 * with the collection, the id, the values of the properties per property path and the primary
 * geometry. The processes run the queries of the API with this format and read the lines while they
 * are written.
 */
@Singleton
@AutoBind
public class FeatureRecordsFormat extends FeatureFormatExtension {

  static final ApiMediaType MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "vnd.ldproxy.feature-records+x-ndjson"))
          .label("Feature records")
          .parameter("records")
          .build();

  private static final ApiMediaType COLLECTION_MEDIA_TYPE =
      new ImmutableApiMediaType.Builder()
          .type(new MediaType("application", "json"))
          .label("JSON")
          .parameter("json")
          .build();

  @Inject
  public FeatureRecordsFormat(
      ExtensionRegistry extensionRegistry, FeaturesCoreProviders providers) {
    super(extensionRegistry, providers);
  }

  @Override
  public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
    return ProcessesFeaturesConfiguration.class;
  }

  @Override
  public ApiMediaType getMediaType() {
    return MEDIA_TYPE;
  }

  @Override
  public ApiMediaType getCollectionMediaType() {
    return COLLECTION_MEDIA_TYPE;
  }

  @Override
  public ApiMediaTypeContent getContent() {
    return null;
  }

  @Override
  public boolean isInternal() {
    return true;
  }

  @Override
  public boolean canEncodeFeatures() {
    return true;
  }

  // a query with hits only writes one line with the number of matching features
  @Override
  public boolean supportsHitsOnly() {
    return true;
  }

  @Override
  public boolean supportsRootConcat() {
    return true;
  }

  @Override
  public boolean supportsHeterogeneousFeatureCollections() {
    return true;
  }

  @Override
  public Optional<FeatureTokenEncoder<?>> getFeatureEncoder(
      FeatureTransformationContext transformationContext, Optional<Locale> language) {
    return Optional.of(new FeatureEncoderRecords(transformationContext));
  }
}
