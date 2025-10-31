/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.gltf.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeatureSchemaCache;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SchemaCacheSfFlat;
import de.ii.ogcapi.features.gltf.domain.GltfConfiguration;
import de.ii.ogcapi.features.gltf.domain.GltfSchema;
import de.ii.ogcapi.features.gltf.domain.QueriesHandlerGltf;
import de.ii.ogcapi.features.gltf.domain.SchemaFormat3dMetadata;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueriesHandler;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.values.domain.ValueStore;
import de.ii.xtraplatform.values.domain.Values;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class QueriesHandlerGltfImpl extends AbstractVolatileComposed implements QueriesHandlerGltf {

  private final FeaturesCoreProviders providers;
  private final I18n i18n;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final Metadata3dSchemaCacheImpl schemaCache;
  private final Values<Codelist> codelistStore;
  private final FeatureSchemaCache featureSchemaCache;

  @Inject
  public QueriesHandlerGltfImpl(
      I18n i18n,
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      VolatileRegistry volatileRegistry) {
    super(QueriesHandlerGltf.class.getSimpleName(), volatileRegistry, true);
    this.i18n = i18n;
    this.providers = providers;
    this.codelistStore = valueStore.forType(Codelist.class);
    this.queryHandlers =
        ImmutableMap.of(
            Query.SCHEMA, QueryHandler.with(QueryInputGltfSchema.class, this::getSchemaResponse));
    this.featureSchemaCache = new SchemaCacheSfFlat();
    this.schemaCache = new Metadata3dSchemaCacheImpl();

    onVolatileStart();

    addSubcomponent(codelistStore);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getSchemaResponse(
      QueryInputGltfSchema queryInput, ApiRequestContext requestContext) {

    return getResponse(queryInput, requestContext, providers, i18n);
  }

  private Response getResponse(
      QueryInputGltfSchema queryInput,
      ApiRequestContext requestContext,
      FeaturesCoreProviders providers,
      I18n i18n) {
    OgcApi api = requestContext.getApi();
    OgcApiDataV2 apiData = api.getData();
    String collectionId = queryInput.getCollectionId();
    QueriesHandler.ensureCollectionIdExists(apiData, collectionId);
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);

    SchemaFormat3dMetadata outputFormat =
        api.getOutputFormat(
                SchemaFormat3dMetadata.class,
                requestContext.getMediaType(),
                Optional.of(collectionId))
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    GltfConfiguration configuration =
        collectionData.getExtension(GltfConfiguration.class).orElseThrow();

    FeatureSchema featureSchema =
        featureSchemaCache.getSchema(
            providers
                .getFeatureSchema(apiData, collectionData)
                .orElse(
                    new ImmutableFeatureSchema.Builder()
                        .name(collectionId)
                        .type(SchemaBase.Type.OBJECT)
                        .build()),
            apiData,
            apiData.getCollectionData(collectionId).orElse(null),
            configuration,
            configuration);

    GltfSchema schema =
        schemaCache.getSchema(featureSchema, apiData, collectionId, codelistStore.asMap());

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || apiData
                    .getExtension(HtmlConfiguration.class, collectionId)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(schema, GltfSchema.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? getLinks(requestContext, i18n) : ImmutableList.of(),
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format(
                    "%s.schema.%s", collectionId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(schema))
        .build();
  }
}
