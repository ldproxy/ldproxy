/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.styles.app

import de.ii.ogcapi.foundation.domain.ImmutableFeatureTypeConfigurationOgcApi
import de.ii.ogcapi.foundation.domain.ImmutableOgcApiDataV2
import de.ii.ogcapi.foundation.domain.OgcApiDataV2
import de.ii.xtraplatform.entities.domain.EntityData
import de.ii.xtraplatform.entities.domain.EntityDataStore
import de.ii.xtraplatform.values.domain.Identifier
import spock.lang.PendingFeature
import spock.lang.Shared
import spock.lang.Specification

class MbStyleStylesheetGeneratorSpec extends Specification {

    @Shared
    EntityDataStore<EntityData> entityDataStore = Stub(EntityDataStore<EntityData>) {
        forType(OgcApiDataV2.class) >> Stub(EntityDataStore<OgcApiDataV2>) {
            hasAny(_) >> true
            fullIdentifier(_) >> Identifier.from("api")
            get(_) >> new ImmutableOgcApiDataV2.Builder().id("api")
                    .putCollections("collection1", new ImmutableFeatureTypeConfigurationOgcApi.Builder().id("collection1").label("collection1"))
                    .putCollections("collection2", new ImmutableFeatureTypeConfigurationOgcApi.Builder().id("collection2").label("collection2"))
                    .build()
        }
    }
    @Shared
    MbStyleStylesheetGenerator generator = new MbStyleStylesheetGenerator(entityDataStore)


    def 'analyze: should return collections for api name'() {

        given:

        when:

        def result = generator.analyze("api")

        then:

        result.keySet().sort() == ["collection1", "collection2"].sort()
        result.values().every { it.matches("#[0-9a-fA-F]{6}") }
    }

    def 'generate: should return style with layers for each collection'() {

        given:

        Map<String, String> collectionColors = generator.analyze("api")

        when:

        def result = generator.generate("api", collectionColors)

        then:

        result.layers.any { it.id.startsWith("collection1") }
        result.layers.any { it.id.startsWith("collection2") }

    }

    def 'generate: should return style with a source for the api'() {

        given:

        Map<String, String> collectionColors = generator.analyze("api")

        when:

        def result = generator.generate("api", collectionColors)

        then:

        result.sources.containsKey("api")

    }

    def 'analyze: should throw IllegalArgumentException if apiId does not exist'() {

        given:

        EntityDataStore<OgcApiDataV2> entityDataStoreV2 = Stub(EntityDataStore<OgcApiDataV2>) {
            has("api") >> false
        }
        EntityDataStore<EntityData> entityDataStore = Stub(EntityDataStore<EntityData>) {
            forType(OgcApiDataV2.class) >> entityDataStoreV2
        }

        MbStyleStylesheetGenerator generator = new MbStyleStylesheetGenerator(entityDataStore)

        when:

        generator.analyze("api")

        then:

        thrown(IllegalArgumentException)
    }

}
