/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.pubsub.app;

import com.github.azahnen.dagger.annotations.AutoBind;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ogcapi.features.core.domain.SchemaDeriverCollectionProperties;
import de.ii.ogcapi.foundation.domain.ApiMetadata;
import de.ii.ogcapi.foundation.domain.ApiRequestContext;
import de.ii.ogcapi.foundation.domain.HeaderCaching;
import de.ii.ogcapi.foundation.domain.HeaderContentDisposition;
import de.ii.ogcapi.foundation.domain.I18n;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.foundation.domain.QueryHandler;
import de.ii.ogcapi.foundation.domain.QueryInput;
import de.ii.ogcapi.foundation.domain.QueryInputGeneric;
import de.ii.ogcapi.pubsub.domain.AsyncApiDefinitionFormatExtension;
import de.ii.ogcapi.pubsub.domain.QueriesHandlerPubSub;
import de.ii.ogcapi.pubsub.domain.asyncapi.AsyncApi;
import de.ii.ogcapi.pubsub.domain.asyncapi.AsyncApiChannel;
import de.ii.ogcapi.pubsub.domain.asyncapi.AsyncApiReference;
import de.ii.ogcapi.pubsub.domain.asyncapi.AsyncApiServer;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApi;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiChannel;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiComponents;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiContact;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiInfo;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiLicense;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiMessage;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiOperation;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiOperationBindingsMqtt;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiReference;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiSecurity;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiServer;
import de.ii.ogcapi.pubsub.domain.asyncapi.ImmutableAsyncApiServerBindingsMqtt;
import de.ii.xtraplatform.base.domain.ETag;
import de.ii.xtraplatform.base.domain.resiliency.AbstractVolatileComposed;
import de.ii.xtraplatform.base.domain.resiliency.VolatileRegistry;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.features.domain.FeatureSchema;
import de.ii.xtraplatform.features.domain.SchemaBase.Type;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaAllOf;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaBoolean;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaInteger;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaNumber;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaObject;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaRef;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaString;
import de.ii.xtraplatform.jsonschema.domain.ImmutableJsonSchemaTrue;
import de.ii.xtraplatform.jsonschema.domain.JsonSchema;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument;
import de.ii.xtraplatform.jsonschema.domain.JsonSchemaDocument.VERSION;
import de.ii.xtraplatform.values.domain.ValueStore;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;

@Singleton
@AutoBind
public class QueriesHandlerPubSubImpl extends AbstractVolatileComposed
    implements QueriesHandlerPubSub {

  private final Map<Query, QueryHandler<? extends QueryInput>> queryHandlers;
  private final Map<String, AsyncApi> asyncApiDefinitions;
  private final FeaturesCoreProviders providers;
  private final Supplier<Map<String, Codelist>> codelistSupplier;
  private final I18n i18n;

  @Inject
  public QueriesHandlerPubSubImpl(
      FeaturesCoreProviders providers,
      ValueStore valueStore,
      VolatileRegistry volatileRegistry,
      I18n i18n) {
    super(QueriesHandlerPubSub.class.getSimpleName(), volatileRegistry, true);
    this.providers = providers;
    this.codelistSupplier = valueStore.forType(Codelist.class)::asMap;
    this.i18n = i18n;
    this.queryHandlers =
        ImmutableMap.of(
            Query.ASYNC_API_DEFINITION,
            QueryHandler.with(QueryInputGeneric.class, this::getAsyncApiDefinitionResponse));
    this.asyncApiDefinitions = new HashMap<>();

    onVolatileStart();

    addSubcomponent(valueStore);

    onVolatileStarted();
  }

  @Override
  public Map<Query, QueryHandler<? extends QueryInput>> getQueryHandlers() {
    return queryHandlers;
  }

  private Response getAsyncApiDefinitionResponse(
      QueryInput queryInput, ApiRequestContext requestContext) {

    AsyncApiDefinitionFormatExtension outputFormatExtension =
        requestContext
            .getApi()
            .getOutputFormat(
                AsyncApiDefinitionFormatExtension.class,
                requestContext.getMediaType(),
                Optional.empty())
            .orElseThrow(
                () ->
                    new NotAcceptableException(
                        MessageFormat.format(
                            "The requested media type ''{0}'' is not supported for this resource.",
                            requestContext.getMediaType())));

    OgcApiDataV2 apiData = requestContext.getApi().getData();

    if (!asyncApiDefinitions.containsKey(apiData.getId())) {

      ImmutableAsyncApiComponents.Builder components = ImmutableAsyncApiComponents.builder();

      ImmutableAsyncApiInfo.Builder info =
          ImmutableAsyncApiInfo.builder()
              .title(apiData.getLabel())
              .description(apiData.getDescription().orElse(""));

      if (apiData.getMetadata().isPresent()) {
        ApiMetadata md = apiData.getMetadata().get();
        md.getVersion().ifPresent(info::version);
        if (md.getContactName().isPresent()
            || md.getContactUrl().isPresent()
            || md.getContactEmail().isPresent()) {
          ImmutableAsyncApiContact.Builder contact = ImmutableAsyncApiContact.builder();
          md.getContactName().ifPresent(contact::name);
          md.getContactUrl().ifPresent(v -> contact.url(URI.create(v)));
          md.getContactEmail().ifPresent(contact::email);
          info.contact(contact.build());
        }
        if (md.getEffectiveLicenseName().isPresent()) {
          // license name is required
          ImmutableAsyncApiLicense.Builder license =
              ImmutableAsyncApiLicense.builder().name(md.getEffectiveLicenseName().get());
          md.getEffectiveLicenseUrl().ifPresent(v -> license.url(URI.create(v)));
          info.license(license.build());
        }
      }

      PubSubConfiguration cfg = apiData.getExtension(PubSubConfiguration.class).get();
      Set<String> brokersInUse = PubSubConfiguration.getBrokersInUse(apiData);
      ImmutableMap.Builder<String, AsyncApiServer> servers = ImmutableMap.builder();
      cfg.getBrokers()
          .forEach(
              (key, value) -> {
                if (brokersInUse.contains(key)) {
                  ImmutableAsyncApiServer.Builder server =
                      ImmutableAsyncApiServer.builder()
                          .url(String.format("%s:%d", value.getHost(), value.getPort()));

                  if (value.getSsl()) {
                    server.protocol("secure-mqtt");
                  }

                  if (value.getUsername().isPresent() && value.getPassword().isPresent()) {
                    server.security(
                        ImmutableList.of(ImmutableMap.of("userPassword", ImmutableList.of())));
                    components.putSecuritySchemes(
                        "userPassword", ImmutableAsyncApiSecurity.builder().build());
                  }

                  server.bindings(
                      ImmutableAsyncApiServerBindingsMqtt.builder()
                          .clientId(Objects.requireNonNullElse(cfg.getPublisher(), "ldproxy"))
                          .cleanSession(true)
                          .build());

                  servers.put(key, server.build());
                }
              });

      ImmutableMap.Builder<String, AsyncApiChannel> channels = ImmutableMap.builder();
      apiData
          .getCollections()
          .forEach(
              (collectionId, collectionData) -> {
                PubSubConfiguration collectionCfg =
                    collectionData.getExtension(PubSubConfiguration.class).get();
                Optional<FeatureSchema> featureSchema =
                    providers.getFeatureSchema(apiData, collectionData);
                if (collectionCfg.isEnabled() && !collectionCfg.getPublications().isEmpty()) {
                  components.putMessages(
                      String.format("featureChange_%s", collectionId),
                      ImmutableAsyncApiMessage.builder()
                          .name("featureChangeMessage")
                          .title("Feature Change")
                          .summary("Information about a new, updated or deleted feature.")
                          .description(
                              "The message is a GeoJSON representation of the feature with three additional properties: `$id` with a UUID for the publication; `$pubtime` with the timestamp when the publication was created; `$operation` with `create`, `update`, or `delete`. In case of `create` or `update`, the feature includes the id, the geometry and the feature properties. For `delete`, only the id is included.")
                          .payload(
                              new ImmutableJsonSchemaAllOf.Builder()
                                  .addAllOf(
                                      new ImmutableJsonSchemaRef.Builder()
                                          .ref("https://geojson.org/schema/Feature.json")
                                          .build(),
                                      new ImmutableJsonSchemaObject.Builder()
                                          .addRequired("properties")
                                          .properties(
                                              ImmutableMap.of(
                                                  "properties",
                                                  new ImmutableJsonSchemaObject.Builder()
                                                      .addRequired("$id", "$pubtime", "$operation")
                                                      .additionalProperties(
                                                          ImmutableJsonSchemaTrue.builder().build())
                                                      .putProperties(
                                                          "$id",
                                                          new ImmutableJsonSchemaString.Builder()
                                                              .format("uuid")
                                                              .build())
                                                      .putProperties(
                                                          "$pubtime",
                                                          new ImmutableJsonSchemaString.Builder()
                                                              .format("date-time")
                                                              .build())
                                                      .putProperties(
                                                          "$operation",
                                                          new ImmutableJsonSchemaString.Builder()
                                                              .enums(
                                                                  List.of(
                                                                      "create", "update", "delete"))
                                                              .build())
                                                      .build()))
                                          .build())
                                  .build())
                          .build());

                  collectionCfg
                      .getPublications()
                      .forEach(
                          (pubId, pub) -> {
                            AsyncApiReference message =
                                pub.getProperty()
                                    .map(
                                        propertyName -> {
                                          Type type =
                                              featureSchema
                                                  .flatMap(
                                                      schema ->
                                                          schema.getAllNestedProperties().stream()
                                                              .filter(
                                                                  p ->
                                                                      propertyName.equals(
                                                                          p.getName()))
                                                              .map(FeatureSchema::getType)
                                                              .findFirst())
                                                  .orElse(Type.STRING);

                                          components.putMessages(
                                              String.format(
                                                  "valueChange_%s_%s", collectionId, propertyName),
                                              ImmutableAsyncApiMessage.builder()
                                                  .name("valueChangeMessage")
                                                  .title("Value Change")
                                                  .summary(
                                                      String.format(
                                                          "Information about an updated value for property '%s' of a feature in collection '%s'.",
                                                          propertyName, collectionData.getLabel()))
                                                  .contentType("plain/text")
                                                  .payload(
                                                      type == Type.INTEGER
                                                          ? new ImmutableJsonSchemaInteger.Builder()
                                                              .build()
                                                          : type == Type.FLOAT
                                                              ? new ImmutableJsonSchemaNumber
                                                                      .Builder()
                                                                  .build()
                                                              : type == Type.BOOLEAN
                                                                  ? new ImmutableJsonSchemaBoolean
                                                                          .Builder()
                                                                      .build()
                                                                  : new ImmutableJsonSchemaString
                                                                          .Builder()
                                                                      .build())
                                                  .build());
                                          return ImmutableAsyncApiReference.builder()
                                              .ref(
                                                  String.format(
                                                      "#/components/messages/valueChange_%s_%s",
                                                      collectionId, propertyName))
                                              .build();
                                        })
                                    .orElse(
                                        ImmutableAsyncApiReference.builder()
                                            .ref(
                                                String.format(
                                                    "#/components/messages/featureChange_%s",
                                                    collectionId))
                                            .build());
                            channels.put(
                                String.format(
                                    "ogcapi/%s/%s/collections/%s/%s",
                                    cfg.getPublisher(), apiData.getId(), collectionId, pubId),
                                ImmutableAsyncApiChannel.builder()
                                    .subscribe(
                                        ImmutableAsyncApiOperation.builder()
                                            .operationId(
                                                String.format(
                                                    "featureChange_%s_%s",
                                                    collectionId,
                                                    pubId
                                                        .replace("{", "")
                                                        .replace("}", "")
                                                        .replace("/", "_")))
                                            .summary(
                                                String.format(
                                                    "Publishes changes to features of collection '%s'.",
                                                    collectionData.getLabel()))
                                            .parameters(
                                                pub.getParameters().entrySet().stream()
                                                    .collect(
                                                        Collectors.toUnmodifiableMap(
                                                            Entry::getKey,
                                                            entry ->
                                                                getSchema(
                                                                    featureSchema,
                                                                    entry.getValue()))))
                                            .bindings(
                                                ImmutableAsyncApiOperationBindingsMqtt.builder()
                                                    .qos(pub.getMqttQos().getCode())
                                                    .retain(pub.getRetain())
                                                    .build())
                                            .message(message)
                                            .build())
                                    .servers(ImmutableList.of(pub.getBroker()))
                                    .build());
                          });
                }
              });

      asyncApiDefinitions.put(
          apiData.getId(),
          ImmutableAsyncApi.builder()
              .info(info.build())
              .servers(servers.build())
              .channels(channels.build())
              .components(components.build())
              .build());
    }

    AsyncApi apiDefinition = asyncApiDefinitions.get(apiData.getId());

    Date lastModified = getLastModified(queryInput);
    EntityTag etag =
        ETag.from(apiDefinition, AsyncApi.FUNNEL, outputFormatExtension.getMediaType().label());
    Response.ResponseBuilder response = evaluatePreconditions(requestContext, lastModified, etag);
    if (Objects.nonNull(response)) {
      return response.build();
    }

    return prepareSuccessResponse(
            requestContext,
            null,
            HeaderCaching.of(lastModified, etag, queryInput),
            null,
            HeaderContentDisposition.of(
                String.format("asyncapi.%s", outputFormatExtension.getMediaType().fileExtension())),
            i18n.getLanguages())
        .entity(outputFormatExtension.getAsyncApiEntity(apiDefinition, requestContext))
        .build();
  }

  private JsonSchema getSchema(Optional<FeatureSchema> schema, String propertyName) {
    if (schema.isPresent()) {
      SchemaDeriverCollectionProperties schemaDeriver =
          new SchemaDeriverCollectionProperties(
              VERSION.current(),
              Optional.empty(),
              "ignore",
              Optional.empty(),
              codelistSupplier.get(),
              ImmutableList.of(propertyName));

      JsonSchema result =
          ((JsonSchemaDocument) schema.get().accept(schemaDeriver))
              .getProperties()
              .get(propertyName);

      if (Objects.nonNull(result)) {
        return result;
      }
    }

    return new ImmutableJsonSchemaString.Builder().build();
  }
}
