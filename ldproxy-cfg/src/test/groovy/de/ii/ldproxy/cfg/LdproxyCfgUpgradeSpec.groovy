/*
 * Copyright 2026 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.cfg

import de.ii.xtraplatform.entities.domain.EntityData
import de.ii.xtraplatform.entities.domain.EntityDataStore
import de.ii.xtraplatform.entities.domain.EntityMigration
import de.ii.xtraplatform.values.domain.Identifier
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Replays the steps of an entity upgrade as executed by xtracfg: load the entity, apply the
 * applicable migrations, serialize to a map and subtract the defaults.
 */
class LdproxyCfgUpgradeSpec extends Specification {

    @TempDir
    Path store

    static class Upgraded {
        List<String> migrations = []
        Map<String, Object> result
    }

    Upgraded upgrade(String entityType, String id, String yaml) {
        Files.createDirectories(store.resolve("entities/instances/${entityType}"))
        Files.writeString(store.resolve("entities/instances/${entityType}/${id}.yml"), yaml)

        LdproxyCfg cfg = LdproxyCfg.create(store)
        cfg.initStore()

        Identifier identifier = Identifier.from(id, entityType)
        EntityData entityData = cfg.getEntityDataStore().get(identifier)
        EntityData defaults = cfg.getEntityDataDefaultsStore()
                .getBuilder(EntityDataStore.defaults(identifier, entityData.getEntitySubType()))
                .fillRequiredFieldsWithPlaceholders()
                .build()

        Upgraded upgraded = new Upgraded()
        for (EntityMigration<?, ?> migration : cfg.migrations().entity()) {
            if (migration.isApplicable(entityData, Optional.of(defaults))) {
                upgraded.migrations << migration.getSubject()
                entityData = migration.migrateRaw(entityData, Optional.of(defaults))
            }
        }

        Map<String, Object> asMap = cfg.getEntityDataStore().asMap(identifier, entityData)
        upgraded.result = cfg.getEntityDataDefaultsStore()
                .subtractDefaults(identifier, entityData.getEntitySubType(), asMap)

        return upgraded
    }

    static Map<String, Object> block(Map<String, Object> config, String buildingBlock) {
        return ((List<Map<String, Object>>) config.get("api")).find { it.get("buildingBlock") == buildingBlock }
    }

    def "deprecated building block options of a service are replaced or removed"() {
        when:
        Upgraded upgraded = upgrade("services", "api", '''---
id: api
serviceType: OGC_API
api:
- buildingBlock: JSON_FG
  enabled: true
  geojsonCompatibility: false
  featureType:
  - 'nas:{{type}}'
- buildingBlock: FEATURES_HTML
  enabled: true
  geometryProperties:
  - lod1Solid
- buildingBlock: FEATURES_EXTENSIONS
  enabled: true
  intersectsParameter: true
collections:
  c1:
    id: c1
    label: c1
    api:
    - buildingBlock: JSON_FG
      featureType:
      - other
''')
        Map<String, Object> jsonFg = block(upgraded.result, "JSON_FG")
        Map<String, Object> html = block(upgraded.result, "FEATURES_HTML")
        Map<String, Object> c1 = ((Map<String, Object>) upgraded.result.get("collections")).get("c1") as Map<String, Object>
        Map<String, Object> c1JsonFg = block(c1, "JSON_FG")

        then:
        upgraded.migrations.containsAll(["building block JSON_FG", "building block FEATURES_HTML", "building block FEATURES_EXTENSIONS"])
        jsonFg.get("enabled") == true
        jsonFg.get("supportPlusProfile") == false
        jsonFg.get("featureTypeV1") == "nas:{{type}}"
        !jsonFg.containsKey("geojsonCompatibility")
        !jsonFg.containsKey("featureType")
        html.get("enabled") == true
        !html.containsKey("geometryProperties")
        block(upgraded.result, "FEATURES_EXTENSIONS") == null
        c1JsonFg.get("featureTypeV1") == "other"
        !c1JsonFg.containsKey("featureType")
    }

    def "a service without deprecated options needs no migration"() {
        when:
        Upgraded upgraded = upgrade("services", "api", '''---
id: api
serviceType: OGC_API
api:
- buildingBlock: JSON_FG
  enabled: true
  supportPlusProfile: false
  featureTypeV1: 'nas:{{type}}'
''')

        then:
        upgraded.migrations.isEmpty()
        block(upgraded.result, "JSON_FG").get("featureTypeV1") == "nas:{{type}}"
    }

    def "assumeExternalChanges of a SQL feature provider is replaced by datasetChanges"() {
        when:
        Upgraded upgraded = upgrade("providers", "db", '''---
id: db
providerType: FEATURE
providerSubType: SQL
connectionInfo:
  database: test
  assumeExternalChanges: true
types: {}
''')
        Map<String, Object> connectionInfo = upgraded.result.get("connectionInfo") as Map<String, Object>
        Map<String, Object> datasetChanges = upgraded.result.get("datasetChanges") as Map<String, Object>

        then:
        upgraded.migrations == ["feature provider option connectionInfo.assumeExternalChanges"]
        connectionInfo.get("database") == "test"
        !connectionInfo.containsKey("assumeExternalChanges")
        datasetChanges.get("mode") == "EXTERNAL"
    }

    def "seeding.maxThreads of a tile provider is removed"() {
        when:
        Upgraded upgraded = upgrade("providers", "tiles", '''---
id: tiles
providerType: TILE
providerSubType: FEATURES
seeding:
  maxThreads: 4
  runOnStartup: false
''')
        Map<String, Object> seeding = upgraded.result.get("seeding") as Map<String, Object>

        then:
        upgraded.migrations == ["tile provider option seeding.maxThreads"]
        !seeding.containsKey("maxThreads")
        seeding.get("runOnStartup") == false
    }
}
