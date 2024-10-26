/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import de.ii.ogcapi.features.search.domain.QueryExpression;
import de.ii.ogcapi.foundation.domain.OgcApiDataV2;
import de.ii.ogcapi.styles.domain.MbStyleStylesheet;
import de.ii.xtraplatform.base.app.StoreImpl;
import de.ii.xtraplatform.base.app.SubstitutionsImpl;
import de.ii.xtraplatform.base.domain.AppContext;
import de.ii.xtraplatform.base.domain.ImmutableStoreConfiguration;
import de.ii.xtraplatform.base.domain.ImmutableStoreConfiguration.Builder;
import de.ii.xtraplatform.base.domain.Jackson;
import de.ii.xtraplatform.base.domain.JacksonProvider;
import de.ii.xtraplatform.base.domain.StoreConfiguration;
import de.ii.xtraplatform.base.domain.StoreSource.Content;
import de.ii.xtraplatform.base.domain.Substitutions;
import de.ii.xtraplatform.blobs.domain.ResourceStore;
import de.ii.xtraplatform.codelists.domain.Codelist;
import de.ii.xtraplatform.entities.app.EntityDataDefaultsStoreImpl;
import de.ii.xtraplatform.entities.app.EntityDataStoreImpl;
import de.ii.xtraplatform.entities.app.EventStoreDefault;
import de.ii.xtraplatform.entities.domain.EntityData;
import de.ii.xtraplatform.entities.domain.EntityDataBuilder;
import de.ii.xtraplatform.entities.domain.EntityDataDefaultsStore;
import de.ii.xtraplatform.entities.domain.EntityDataStore;
import de.ii.xtraplatform.entities.domain.EntityFactoriesImpl;
import de.ii.xtraplatform.entities.domain.EntityFactory;
import de.ii.xtraplatform.entities.domain.EventStore;
import de.ii.xtraplatform.entities.domain.EventStoreDriver;
import de.ii.xtraplatform.entities.domain.EventStoreSubscriber;
import de.ii.xtraplatform.entities.domain.ReplayEvent;
import de.ii.xtraplatform.entities.infra.EventStoreDriverFs;
import de.ii.xtraplatform.features.domain.ProviderData;
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSql;
import de.ii.xtraplatform.features.sql.domain.FeatureProviderSqlData;
import de.ii.xtraplatform.services.domain.Service;
import de.ii.xtraplatform.services.domain.ServiceData;
import de.ii.xtraplatform.streams.domain.Event;
import de.ii.xtraplatform.values.api.ValueEncodingJackson;
import de.ii.xtraplatform.values.app.ValueFactoriesImpl;
import de.ii.xtraplatform.values.domain.Identifier;
import de.ii.xtraplatform.values.domain.StoredValue;
import de.ii.xtraplatform.values.domain.ValueEncoding.FORMAT;
import de.ii.xtraplatform.values.domain.ValueFactory;
import de.ii.xtraplatform.values.domain.annotations.FromValueStore;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LdproxyCfgImpl implements LdproxyCfg {

  private static final Logger LOGGER = LoggerFactory.getLogger(LdproxyCfgImpl.class);

  private final EntityDataDefaultsStore entityDataDefaultsStore;
  private final EntityDataStore<EntityData> entityDataStore;
  private final Path dataDirectory;
  private final StoreConfiguration storeConfiguration;
  private final Builders builders;
  private final Migrations migrations;
  private final ValueEncodingJackson<EntityData> valueEncoding;
  private final ObjectMapper objectMapper;
  private final RequiredIncludes requiredIncludes;
  private final de.ii.xtraplatform.entities.domain.EntityFactories entityFactories;
  private final de.ii.xtraplatform.values.domain.ValueFactories valueFactories;
  private final Map<String, JsonSchema> entitySchemas;
  private final Map<String, String> rawSchemas;
  private final List<Identifier> entityIdentifiers;
  private final List<Identifier> defaultIdentifiers;
  private final List<Identifier> overrideIdentifiers;
  private final EventSubscriptionsSync eventSubscriptions;

  public LdproxyCfgImpl(Path dataDirectory) {
    this(dataDirectory, false);
  }

  public LdproxyCfgImpl(Path dataDirectory, boolean noDefaults) {
    this(dataDirectory, detectStore(dataDirectory), noDefaults);
  }

  public LdproxyCfgImpl(Path dataDirectory, StoreConfiguration sc, boolean noDefaults) {
    this.dataDirectory = dataDirectory;
    this.storeConfiguration = sc;
    this.requiredIncludes = new RequiredIncludes();
    this.builders = new Builders() {};
    Jackson jackson = new JacksonProvider(JacksonSubTypes::ids, false);
    this.valueEncoding = new ValueEncodingJackson<EntityData>(jackson, false);
    this.objectMapper = valueEncoding.getMapper(FORMAT.YML);
    this.eventSubscriptions = new EventSubscriptionsSync();
    EventStoreDriver storeDriver = new EventStoreDriverFs(dataDirectory);
    EventStore eventStore =
        new EventStoreDefault(
            new StoreImpl(dataDirectory, storeConfiguration), storeDriver, eventSubscriptions);
    ((EventStoreDefault) eventStore).onStart(false).toCompletableFuture().join();
    this.entityIdentifiers = new ArrayList<>();
    this.defaultIdentifiers = new ArrayList<>();
    this.overrideIdentifiers = new ArrayList<>();
    eventStore.subscribe(
        new EventStoreSubscriber() {
          @Override
          public List<String> getEventTypes() {
            return List.of(
                EntityDataDefaultsStore.EVENT_TYPE,
                EntityDataStore.EVENT_TYPE_ENTITIES,
                EntityDataStore.EVENT_TYPE_OVERRIDES);
          }

          @Override
          public void onEmit(Event event) {
            if (event instanceof ReplayEvent && Objects.nonNull(((ReplayEvent) event).format())) {
              // System.out.println("EVENT " + ((ReplayEvent) event).asPath());
              if (Objects.equals(((ReplayEvent) event).format().toLowerCase(), "yml")) {
                if (Objects.equals(
                    ((ReplayEvent) event).type(), EntityDataStore.EVENT_TYPE_ENTITIES)) {
                  entityIdentifiers.add(((ReplayEvent) event).identifier());
                } else if (Objects.equals(
                    ((ReplayEvent) event).type(), EntityDataDefaultsStore.EVENT_TYPE)) {
                  defaultIdentifiers.add(((ReplayEvent) event).identifier());
                } else if (Objects.equals(
                    ((ReplayEvent) event).type(), EntityDataStore.EVENT_TYPE_OVERRIDES)) {
                  overrideIdentifiers.add(((ReplayEvent) event).identifier());
                }
              }
            }
          }
        });
    AppContext appContext = new AppContextCfg();
    ResourceStore mockResourceStore = new MockResourceStore(dataDirectory);
    OgcApiExtensionRegistry extensionRegistry = new OgcApiExtensionRegistry(appContext);
    Set<EntityFactory> factories =
        EntityFactories.factories(appContext, extensionRegistry, mockResourceStore);
    Substitutions substitutions = new SubstitutionsImpl(appContext);
    this.entityFactories = new EntityFactoriesImpl(() -> factories);
    this.entityDataDefaultsStore =
        new EntityDataDefaultsStoreImpl(
            appContext, eventStore, jackson, substitutions, () -> factories);
    this.entityDataStore =
        new EntityDataStoreImpl(
            appContext,
            eventStore,
            jackson,
            substitutions,
            () -> factories,
            entityDataDefaultsStore,
            mockResourceStore,
            new MockValueStore(),
            noDefaults);
    this.entitySchemas = new HashMap<>();
    this.rawSchemas = new HashMap<>();
    this.migrations = Migrations.create(entityDataStore);
    Set<ValueFactory> vFactories = ValueFactories.factories(entityDataStore);
    this.valueFactories = new ValueFactoriesImpl(() -> vFactories);
  }

  private static StoreConfiguration detectStore(Path dataDirectory) {
    Optional<ImmutableStoreConfiguration> sc =
        LayoutImpl.detectSource(dataDirectory)
            .map(storeSourceFs -> new Builder().addSources(storeSourceFs).build());

    if (sc.isEmpty()) {
      throw new IllegalArgumentException("No store detected in " + dataDirectory);
    }

    return sc.get();
  }

  @Override
  public Path getEntitiesPath() {
    return dataDirectory
        .resolve(Content.ENTITIES.getPrefix())
        .resolve(Content.INSTANCES.getPrefix());
  }

  @Override
  public Path getValuesPath() {
    return dataDirectory.resolve(Content.VALUES.getPrefix());
  }

  @Override
  public Builders builder() {
    return builders;
  }

  @Override
  public Migrations migrations() {
    return migrations;
  }

  @Override
  public Path getDataDirectory() {
    return dataDirectory;
  }

  @Override
  public ObjectMapper getObjectMapper() {
    return objectMapper;
  }

  @Override
  public EntityDataDefaultsStore getEntityDataDefaultsStore() {
    return entityDataDefaultsStore;
  }

  @Override
  public EntityDataStore<EntityData> getEntityDataStore() {
    return entityDataStore;
  }

  @Override
  public de.ii.xtraplatform.entities.domain.EntityFactories getEntityFactories() {
    return entityFactories;
  }

  @Override
  public de.ii.xtraplatform.values.domain.ValueFactories getValueFactories() {
    return valueFactories;
  }

  @Override
  public List<Identifier> getEntityIdentifiers() {
    return entityIdentifiers;
  }

  @Override
  public List<Identifier> getDefaultIdentifiers() {
    return defaultIdentifiers;
  }

  @Override
  public List<Identifier> getOverrideIdentifiers() {
    return overrideIdentifiers;
  }

  @Override
  public void ignoreEventsFor(String type, Identifier identifier) {
    eventSubscriptions.addIgnore(type, identifier);
  }

  public void init() throws IOException {
    ObjectMapper jsonMapper = entityDataStore.getValueEncoding().getMapper(FORMAT.JSON);
    JsonMetaSchema metaSchema =
        JsonMetaSchema.builder(
                "https://json-schema.org/draft/2020-12/schema", JsonMetaSchema.getV202012())
            .addKeyword(new DeprecatedKeyword())
            .build();
    JsonSchemaFactory factory =
        JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(VersionFlag.V202012))
            .addMetaSchema(metaSchema)
            .objectMapper(jsonMapper)
            .build();

    for (String entityType : List.of("codelists", "providers", "services", "users")) {
      URL schemaResource =
          Resources.getResource(
              LdproxyCfgImpl.class, String.format("/json-schema/entities/%s.json", entityType));

      String rawSchema =
          new String(
              Resources.asByteSource(schemaResource).openStream().readAllBytes(),
              StandardCharsets.UTF_8);
      JsonSchema schema = factory.getSchema(rawSchema);
      schema.initializeValidators();

      this.entitySchemas.put(entityType, schema);
      this.rawSchemas.put("entities/" + entityType, rawSchema);
    }
  }

  @Override
  public void initStore() {
    ((EntityDataDefaultsStoreImpl) entityDataDefaultsStore)
        .onStart(false)
        .toCompletableFuture()
        .join();
    ((EntityDataStoreImpl) entityDataStore).onStart(false).toCompletableFuture().join();
  }

  @Override
  public Set<ValidationMessage> validateEntity(Path entityPath, String entityType)
      throws IOException {
    if (!entitySchemas.containsKey(entityType)) {
      throw new IllegalStateException();
    }
    // System.out.println("VALIDATE " + entityPath);

    JsonNode jsonNode = objectMapper.readTree(entityPath.toFile());

    return entitySchemas.get(entityType).validate(jsonNode);
  }

  @Override
  public Set<ValidationMessage> validateEntity(String entityCfg, String entityType)
      throws IOException {
    if (!entitySchemas.containsKey(entityType)) {
      throw new IllegalStateException();
    }
    // System.out.println("VALIDATE " + entityPath);

    JsonNode jsonNode = objectMapper.readTree(entityCfg);

    return entitySchemas.get(entityType).validate(jsonNode);
  }

  @Override
  public <T extends EntityData> void writeEntity(T data, Path... patches) throws IOException {
    Path path = getEntityPath(data);
    Identifier identifier = Identifier.from(data.getId(), getType(data));

    T patched = applyPatch(identifier, data, patches);
    Map<String, Object> asMap = getEntityDataStore().asMap(identifier, patched);

    Map<String, Object> withoutDefaults =
        getEntityDataDefaultsStore()
            .subtractDefaults(identifier, data.getEntitySubType(), asMap, List.of("enabled"));

    path.getParent().toFile().mkdirs();
    objectMapper.writeValue(path.toFile(), withoutDefaults);
  }

  @Override
  public <T extends EntityData> void writeEntity(T data, OutputStream outputStream)
      throws IOException {
    writeEntity(data);

    Path path = getEntityPath(data);

    Files.copy(path, outputStream);
  }

  @Override
  public <T extends StoredValue> void writeValue(T data, String name, String... path)
      throws IOException {
    FORMAT format = getFormat(data);
    Path valuePath = getValuePath(data, format, name, path);
    valuePath.getParent().toFile().mkdirs();
    valueEncoding.getMapper(format).writeValue(valuePath.toFile(), data);
  }

  @Override
  public boolean hasValue(String type, String name, String... path) {
    FORMAT format = getFormat(getValueClass(type));
    Path valuePath = getValuePath(type, format, name, path);

    return Files.exists(valuePath);
  }

  @Override
  public void writeZippedStore(OutputStream outputStream) throws IOException {
    ZipOutputStream zipOut = new ZipOutputStream(outputStream);
    zipFile(dataDirectory.toFile(), dataDirectory.toFile().getName(), zipOut, true);
    zipOut.close();
  }

  private <T extends EntityData> T applyPatch(Identifier identifier, T data, Path... patches)
      throws IOException {
    if (patches.length == 0) {
      return data;
    }

    EntityDataBuilder<EntityData> builder =
        entityDataStore.getBuilder(identifier, Optional.ofNullable(getSubType(data))).from(data);

    for (Path patch : patches) {
      objectMapper.readerForUpdating(builder).readValue(patch.toFile());
    }

    return (T) builder.build();
  }

  // TODO: for which entity type, writes application defaults as well
  public <T extends EntityData> void writeDefaults(T data, Path... defaults) throws IOException {
    Identifier defaultsIdentifier = Identifier.from(data.getId(), getType(data), getSubType(data));
    EntityDataBuilder<EntityData> builder =
        entityDataDefaultsStore.getBuilder(defaultsIdentifier).fillRequiredFieldsWithPlaceholders();

    for (Path defaultFile : defaults) {
      objectMapper.readerForUpdating(builder).readValue(defaultFile.toFile());
    }

    Map<String, Object> asMap =
        entityDataDefaultsStore.asMap(
            Identifier.from(data.getId(), getType(data)), builder.build());
    try {
      entityDataDefaultsStore.patch(data.getId(), asMap, getType(data), getSubType(data)).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      }
      throw e;
    }
  }

  @Override
  public <T extends EntityData> Path getEntityPath(T data) {
    return getEntitiesPath().resolve(Path.of(getType(data), FORMAT.YML.apply(data.getId())));
  }

  public <T extends StoredValue> Path getValuePath(
      T data, FORMAT format, String name, String... path) {
    return getValuePath(getType(data), format, name, path);
  }

  public Path getValuePath(String type, FORMAT format, String name, String... path) {
    return getValuesPath().resolve(Path.of(type, path)).resolve(format.apply(name));
  }

  @Override
  public Map<String, String> getRawSchemas() {
    return rawSchemas;
  }

  private static <T extends EntityData> String getType(T data) {
    if (data instanceof ProviderData) {
      return ProviderData.ENTITY_TYPE;
    }
    if (data instanceof ServiceData) {
      return Service.TYPE;
    }
    return null;
  }

  private static <T extends StoredValue> String getType(T data) {
    /*System.out.println("ANN " + data.getClass().getAnnotation(FromValueStore.class));
    return Optional.ofNullable(data.getClass().getAnnotation(FromValueStore.class))
        .map(FromValueStore::type)
        .orElse(null);*/
    if (data instanceof Codelist) {
      return "codelists";
    }
    if (data instanceof QueryExpression) {
      return "queries";
    }
    if (data instanceof MbStyleStylesheet) {
      return "maplibre-styles";
    }
    return null;
  }

  private static Class<? extends StoredValue> getValueClass(String type) {
    if (Objects.equals(type, "codelists")) {
      return Codelist.class;
    }
    if (Objects.equals(type, "queries")) {
      return QueryExpression.class;
    }
    if (Objects.equals(type, "maplibre-styles")) {
      return MbStyleStylesheet.class;
    }
    return null;
  }

  private static <T extends StoredValue> FORMAT getFormat(T data) {
    return getFormat(data.getClass());
  }

  private static FORMAT getFormat(Class<? extends StoredValue> valueClass) {
    return Optional.ofNullable(valueClass.getAnnotation(FromValueStore.class))
        .map(FromValueStore::defaultFormat)
        .orElse(FORMAT.YML);
  }

  private static <T extends EntityData> String getSubType(T data) {
    if (data instanceof Codelist) {
      return null;
    }
    if (data instanceof FeatureProviderSqlData) {
      return FeatureProviderSql.ENTITY_SUB_TYPE.replace('/', '.').toLowerCase();
    }
    if (data instanceof OgcApiDataV2) {
      return OgcApiDataV2.SERVICE_TYPE.toLowerCase();
    }
    return null;
  }

  private static void zipFile(
      File fileToZip, String fileName, ZipOutputStream zipOut, boolean skipParentDir)
      throws IOException {
    if (fileToZip.isHidden()) {
      return;
    }
    if (fileToZip.isDirectory()) {
      if (skipParentDir) {
        File[] children = fileToZip.listFiles();
        for (File childFile : children) {
          zipFile(childFile, childFile.getName(), zipOut, false);
        }
        return;
      }
      if (fileName.endsWith("/")) {
        zipOut.putNextEntry(new ZipEntry(fileName));
        zipOut.closeEntry();
      } else {
        zipOut.putNextEntry(new ZipEntry(fileName + "/"));
        zipOut.closeEntry();
      }
      File[] children = fileToZip.listFiles();
      for (File childFile : children) {
        zipFile(childFile, fileName + "/" + childFile.getName(), zipOut, false);
      }
      return;
    }
    FileInputStream fis = new FileInputStream(fileToZip);
    ZipEntry zipEntry = new ZipEntry(fileName);
    zipOut.putNextEntry(zipEntry);
    byte[] bytes = new byte[1024];
    int length;
    while ((length = fis.read(bytes)) >= 0) {
      zipOut.write(bytes, 0, length);
    }
    fis.close();
  }
}
