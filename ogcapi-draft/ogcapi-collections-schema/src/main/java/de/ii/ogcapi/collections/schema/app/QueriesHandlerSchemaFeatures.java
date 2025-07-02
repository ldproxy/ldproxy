/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.collections.schema.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.collections.schema.domain.QueriesHandlerSchema;
import de.ii.ogcapi.collections.schema.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.JsonSchema;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument;
import de.ii.ogcapi.features.core.domain.JsonSchemaDocument.VERSION;
import de.ii.ogcapi.features.core.domain.JsonSchemaExtension;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.base.domain.resiliency.VolatileUnavailableException;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.values.domain.ValueStore;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class QueriesHandlerSchemaFeatures extends AbstractVolatileComposed
    implements QueriesHandlerSchema {

  private final FeaturesCoreProviders providers;
  private final I18n i18n;
  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private JsonSchemaCache schemaCache;
  private final ExtensionRegistry extensionRegistry;

  @Inject
  public QueriesHandlerSchemaFeatures(
      ExtensionRegistry extensionRegistry,
      I18n i18n,
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      VolatileRegistry volatileRegistry) {
    super(QueriesHandlerSchema.class.getSimpleName(), volatileRegistry, true);
    this.extensionRegistry = extensionRegistry;
    this.i18n = i18n;
    this.providers = providers;
    this.queryHandlers =
        ImmutableMap.of(
            Query.SCHEMA, QueryHandler.with(QueryInputSchema.class, this::getSchemaResponse));

    onVolatileStart();

    addSubcomponent(valueStore);

    volatileRegistry
        .onAvailable(valueStore)
        .thenRun(
            () ->
                this.schemaCache =
                    new SchemaCacheFeatures(valueStore.forType(Codelist.class)::asMap));

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getSchemaResponse(
      QueryInputSchema queryInput, ApiRequestContext requestContext) {
    final OgcApi api = requestContext.getApi();
    final OgcApiDataV2 apiData = api.getData();

    final String collectionId = queryInput.getCollectionId();
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);

    SchemaFormatExtension outputFormat =
        api.getOutputFormat(
                SchemaFormatExtension.class,
                requestContext.getMediaType(),
                Optional.of(collectionId))
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    // return getResponse(queryInput, requestContext, providers, i18n);
    List<Link> links = getLinks(requestContext, i18n);

    Optional<String> schemaUri =
        links.stream()
            .filter(link -> link.getRel().equals("self"))
            .map(Link::getHref)
            .map(link -> !link.contains("?") ? link : link.substring(0, link.indexOf("?")))
            .findAny();

    FeatureSchema featureSchema =
        providers
            .getFeatureSchema(apiData, collectionData)
            .orElse(
                new ImmutableFeatureSchema.Builder()
                    .name(collectionId)
                    .type(SchemaBase.Type.OBJECT)
                    .build());

    List<JsonSchemaExtension> jsonSchemaExtensions =
        extensionRegistry.getExtensionsForType(JsonSchemaExtension.class).stream()
            .filter(e -> e.isEnabledForApi(apiData, collectionData.getId()))
            .collect(Collectors.toList());

    JsonSchemaDocument schema =
        schemaCache.getSchema(
            featureSchema, apiData, collectionData, schemaUri, jsonSchemaExtensions);

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        !outputFormat.getMediaType().type().equals(MediaType.TEXT_HTML_TYPE)
                || apiData
                    .getExtension(HtmlConfiguration.class, collectionId)
                    .map(HtmlConfiguration::getSendEtags)
                    .orElse(false)
            ? ETag.from(schema, JsonSchema.FUNNEL, outputFormat.getMediaType().label())
            : null;
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) return response.build();

    return prepareSuccessResponse(
            requestContext,
            queryInput.getIncludeLinkHeader() ? links : null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("%s.%s", collectionId, outputFormat.getMediaType().fileExtension())))
        .entity(outputFormat.getEntity(schema, links, collectionId, api, requestContext))
        .build();
  }

  @Override
  public JsonSchemaDocument getJsonSchema(
      FeatureSchema featureSchema,
      OgcApiDataV2 apiData,
      FeatureTypeConfigurationOgcApi collectionData,
      Optional<String> schemaUri,
      VERSION version) {
    if (Objects.isNull(schemaCache)) {
      throw new VolatileUnavailableException("JsonSchemaCache not available");
    }

    List<JsonSchemaExtension> jsonSchemaExtensions =
        extensionRegistry.getExtensionsForType(JsonSchemaExtension.class).stream()
            .filter(e -> e.isEnabledForApi(apiData, collectionData.getId()))
            .collect(Collectors.toList());

    return schemaCache.getSchema(
        featureSchema, apiData, collectionData, schemaUri, jsonSchemaExtensions, version);
  }
}
