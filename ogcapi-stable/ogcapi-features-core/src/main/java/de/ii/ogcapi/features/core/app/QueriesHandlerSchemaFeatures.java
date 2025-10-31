/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.features.core.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.JsonSchemaCache;
import de.ii.ogcapi.features.core.domain.JsonSchemaExtension;
import de.ii.ogcapi.features.core.domain.QueriesHandlerSchema;
import de.ii.ogcapi.features.core.domain.SchemaFormatExtension;
import de.ii.ogcapi.features.core.domain.SchemaType;
import de.ii.ogcapi.foundation.domain.ApiMediaType;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.ExtensionRegistry;
import de.ii.ogcapi.foundation.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.Link;
import de.ii.ogcapi.foundation.domain.OgcApi;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.Profile;
import de.ii.ogcapi.foundation.domain.ProfileExtension.ResourceType;
import de.ii.ogcapi.foundation.domain.ProfileSet;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.html.domain.HtmlConfiguration;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.ImmutableFeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.values.domain.ValueStore;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
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

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  public static void checkCollectionId(OgcApiDataV2 apiData, String collectionId) {
    if (!apiData.isCollectionEnabled(collectionId)) {
      throw new NotFoundException(
          MessageFormat.format("The collection ''{0}'' does not exist in this API.", collectionId));
    }
  }

  private Response getSchemaResponse(
      QueryInputSchema queryInput, ApiRequestContext requestContext) {
    final OgcApi api = requestContext.getApi();
    final OgcApiDataV2 apiData = api.getData();
    final String collectionId = queryInput.getCollectionId();
    checkCollectionId(api.getData(), collectionId);
    FeatureTypeConfigurationOgcApi collectionData = apiData.getCollections().get(collectionId);

    final SchemaType type = queryInput.getType();
    final JsonSchemaCache schemaCache = queryInput.getSchemaCache();

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

    List<ProfileSet> allProfileSets = extensionRegistry.getExtensionsForType(ProfileSet.class);

    List<Profile> profiles =
        negotiateProfiles(
            allProfileSets,
            outputFormat,
            ResourceType.SCHEMA,
            apiData,
            Optional.of(collectionId),
            queryInput.getProfiles(),
            queryInput.getDefaultProfilesResource());

    Map<ApiMediaType, List<Profile>> alternateProfiles =
        getAlternateProfiles(
            allProfileSets,
            apiData,
            collectionId,
            requestContext.getMediaType(),
            requestContext.getAlternateMediaTypes(),
            profiles);

    List<Link> links = getLinks(requestContext, profiles, alternateProfiles, i18n);

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
            .sorted(Comparator.comparing(JsonSchemaExtension::getPriority))
            .collect(Collectors.toList());

    JsonSchemaDocument schema =
        schemaCache.getSchema(
            featureSchema, apiData, collectionData, profiles, schemaUri, jsonSchemaExtensions);

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
                String.format("%s.%s", collectionId, outputFormat.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormat.getEntity(schema, type, links, collectionId, api, requestContext))
        .build();
  }
}
