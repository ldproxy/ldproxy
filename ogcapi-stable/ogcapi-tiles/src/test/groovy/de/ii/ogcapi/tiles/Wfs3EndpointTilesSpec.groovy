/*
 * Copyright 2022 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ogcapi.tiles

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import de.ii.ogcapi.tiles.infra.EndpointVectorTileSetsDataset
import de.ii.xtraplatform.tiles.domain.ImmutableMinMax
import de.ii.xtraplatform.tiles.domain.MinMax
import spock.lang.Ignore
import spock.lang.Specification

import jakarta.ws.rs.NotFoundException

@Ignore //TODO
class Wfs3EndpointTilesSpec extends Specification {

    def 'Check tiles support enabled in dataset with no enabled map'(){

        given: "enabledMap is not initialized "

        def enabledMap = null

        when: "checkTilesParameterCollection is called"

        EndpointVectorTileSetsDataset.checkTilesParameterDataset(enabledMap)

        then: thrown NotFoundException
    }

    def 'Check tiles support in dataset completely disabled '(){

        given: "enabledMap with 5 collections, each collection does not support the tiles"

        def enabledMap = new HashMap<String,Boolean>()

        enabledMap.put("collection0", false)
        enabledMap.put("collection1", false)
        enabledMap.put("collection2", false)
        enabledMap.put("collection3", false)
        enabledMap.put("collection4", false)

        when: "checkTilesParameterCollection is called"

        EndpointVectorTileSetsDataset.checkTilesParameterDataset(enabledMap)

        then: thrown NotFoundException
    }

    def 'Check tiles support enabled in dataset'(){

        given: "enabledMap with 3 collections, 2 without, 1 with tile support"

        def enabledMap = new HashMap<String,Boolean>()

        enabledMap.put("collection0", false)
        enabledMap.put("collection1", false)
        enabledMap.put("collection2", true)


        when: "checkTilesParameterCollection is called"

        def result = EndpointVectorTileSetsDataset.checkTilesParameterDataset(enabledMap)

        then: "it should return true"

        result
    }



    def 'get all Collection ids multiple tests with data table'(boolean mvtEnabled, boolean onlyJson, boolean startSeeding, Set<String>allCollectionIds, Map<String,Boolean> enabledMap, Map<String,List<String>> formatsMap, Map<String,Map<String, MinMax>> seedingMap, HashSet<String> result){

        expect: EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)==result

        where:

        mvtEnabled | onlyJson | startSeeding | allCollectionIds                         | enabledMap                                                | formatsMap                                                                                             | seedingMap                                                                                                                  || result
      /*-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/
        false      | false    | false        | null                                     | new HashMap<>()                                           | new HashMap<>()                                                                                        | new HashMap<>()                                                                                                             || new HashSet<>()
        false      | false    | false        | new HashSet<>()                          | new HashMap<>()                                           | new HashMap<>()                                                                                        | new HashMap<>()                                                                                                             || new HashSet<>()
        false      | false    | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | null                                                                                                   | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build()))) || new HashSet<>()
        false      | false    | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("unknownCollection", ImmutableList.of("application/json")))            | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || new HashSet<>()
        false      | false    | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",false))  | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))                   | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || new HashSet<>()
        false      | false    | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))                   | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || allCollectionIds
        false      | false    | true         | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))                   | new HashMap().put("collection",null)                                                                                                        || new HashSet<>()
        false      | false    | true         | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))                   | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || allCollectionIds
        false      | true     | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/vnd.mapbox-vector-tile"))) | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || new HashSet<>()
        false      | true     | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))                   | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || allCollectionIds
        true       | false    | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))                   | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || new HashSet<>()
        true       | false    | false        | new HashSet(Arrays.asList("collection")) | new HashMap(ImmutableMap.of("collection",true))   | new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/vnd.mapbox-vector-tile"))) | new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))  || allCollectionIds

    }


    //not tested in the data table

    def 'get all Collection ids with tiles parameter enabled - tiles support enabled in one collection' (){

        given: 'a enabled map, which has the tiles support for one entry enabled'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",false,
                "collection1",false,
                "collection2",true,
                "collection3", false))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set with one entry: collection2'

        result == new HashSet<String>(ImmutableList.of("collection2"))
    }

    def 'get all Collection ids with seeding parameter enabled - seeding parameter enabled in one collection' (){

        given: 'a seeding map, which has the seeding support for one entry enabled'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap()

        seedingMap.put("collection0",null)
        seedingMap.put("collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()))
        seedingMap.put("collection2",null)
        seedingMap.put("collection3",null)

        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = true

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set with one entry: collection1'

        result == new HashSet<String>(ImmutableList.of("collection1"))
    }

    def 'get all Collection ids with only JSON format enabled - one collection contains only the JSON format' (){
        given: 'a formats map, which has enabled both formats in one collection, only MVT in two collections and only JSON in one collection '

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json",),
                "collection1", ImmutableList.of("application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = true
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set with one entry: collection 0'

        result == new HashSet<String>(ImmutableList.of("collection0"))

    }

    def 'get all Collection ids with only MVT format enabled - one collection contains the MVT format' (){

        given: 'a formats map, which has only the MVT format enabled in one collection,  both formats enabled in one collection, only the JSON format enabled in two collections '

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json"),
                "collection1", ImmutableList.of("application/json"),
                "collection2", ImmutableList.of("application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = true
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set with two entries:\'collection2\' and \'collection3\''

        result == new HashSet<String>(ImmutableList.of("collection2","collection3"))
    }

    /* Tested with the data table, but only one collection in Sets and Maps

    def 'get all Collection ids with tiles parameter enabled - tiles extension set is null' (){

        given: 'a set with all collection Ids with the tiles extension enabled is null'

        def allCollectionIds = null
        def enabledMap = new HashMap()
        def formatsMap = new HashMap()
        def seedingMap = new HashMap()
        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result=EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()
    }

    def 'get all Collection ids with tiles parameter enabled - no collections with tiles extension' (){

        given: 'a set with all collection Ids with the tiles extension enabled, which is empty'

        def allCollectionIds = new HashSet()
        def enabledMap = new HashMap()
        def formatsMap = new HashMap()
        def seedingMap = new HashMap()
        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()

    }

    def 'get all Collection ids with tiles parameter enabled - one of the three input Maps is not initialized' (){

        given: 'one input map is null'

        def allCollectionIds = new HashSet(Arrays.asList("collection"))
        def enabledMap = new HashMap(ImmutableMap.of("collection",true))
        def formatsMap = null
        def seedingMap = new HashMap(ImmutableMap.of("collection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))
        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()
    }

    def 'get all Collection ids with tiles parameter enabled - one of the three input Maps does not contain the collection' (){

        given: 'one input map which does not contain the collection from allCollectionIds'

        def allCollectionIds = new HashSet(Arrays.asList("collection"))
        def enabledMap = new HashMap(ImmutableMap.of("collection",true))
        def formatsMap = new HashMap(ImmutableMap.of("collection", ImmutableList.of("application/json")))
        def seedingMap = new HashMap(ImmutableMap.of("unknownCollection", ImmutableMap.of("WebMercatorQuad",new ImmutableMinMax.Builder().max(10).min(6).build())))
        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()
    }


    def 'get all Collection ids with tiles parameter enabled - tiles support always disabled' (){

        given: 'a enabled map, which has the tiles support for all entries disabled'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",false,
                "collection1",false,
                "collection2",false,
                "collection3", false))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()
    }



    def 'get all Collection ids with tiles parameter enabled - tiles support enabled in all collections' (){

        given: 'a enabled map, which has the tiles support for all entries enabled'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3", true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set which is equal to the set \'allCollectionIds\''

        result == allCollectionIds

    }

    def 'get all Collection ids with seeding parameter enabled - seeding parameter always disabled' (){

        given: 'a seeding map, which has the seeding support for all entries disabled'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap()

        seedingMap.put("collection0",null)
        seedingMap.put("collection1",null)
        seedingMap.put("collection2",null)
        seedingMap.put("collection3",null)

        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = true

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()

    }

    def 'get all Collection ids with seeding parameter enabled - seeding parameter enabled in all collections' (){

        given: 'a seeding map, which has the seeding support for all entries enabled'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = false
        def startSeeding = true

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set which is equal to the set \'allCollectionIds\''

        result == allCollectionIds
    }



    def 'get all Collection ids with only JSON format enabled - every collection does not contain only the JSON format' (){

        given: 'a formats map, which has the JSON format disabled for all entries '

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = true
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set '

        result == new HashSet<String>()

    }


    def 'get all Collection ids with only JSON format enabled - every collection contains only the JSON format' (){

        given: 'a formats map, which has only the json format enabled for all entries '

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json"),
                "collection1", ImmutableList.of("application/json"),
                "collection2", ImmutableList.of("application/json"),
                "collection3", ImmutableList.of("application/json")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = false
        def onlyJson = true
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set which is equal to the set \'allCollectionIds\''

        result == allCollectionIds
    }


    def 'get all Collection ids with only MVT format enabled - every collection does not contain the MVT format' (){
        given: 'a formats map, which has the MVT format disabled for all entries'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json"),
                "collection1", ImmutableList.of("application/json"),
                "collection2", ImmutableList.of("application/json"),
                "collection3", ImmutableList.of("application/json")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = true
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return an empty Set'

        result == new HashSet<String>()
    }

    def 'get all Collection ids with only MVT format enabled - every collection contains the MVT format' (){

        given: 'a formats map, which has the mvt format enabled for all entries'

        def allCollectionIds = new HashSet(Arrays.asList("collection0","collection1", "collection2","collection3"))

        def enabledMap = new HashMap(ImmutableMap.of(
                "collection0",true,
                "collection1",true,
                "collection2",true,
                "collection3",true))

        def formatsMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection1", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection2", ImmutableList.of("application/json","application/vnd.mapbox-vector-tile"),
                "collection3", ImmutableList.of("application/json", "application/vnd.mapbox-vector-tile")))

        def seedingMap = new HashMap(ImmutableMap.of(
                "collection0", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection1", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection2", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build()),
                "collection3", ImmutableMap.of("WebMercatorQuad", new ImmutableMinMax.Builder().max(10).min(6).build())))

        def mvtEnabled = true
        def onlyJson = false
        def startSeeding = false

        when: 'getCollectionIdsDataset is called'

        def result = EndpointVectorTileSetsDataset.getCollectionIdsDataset(allCollectionIds,enabledMap,formatsMap,seedingMap,mvtEnabled,onlyJson,startSeeding)

        then: 'it should return a Set which is equal to the set \'allCollectionIds\''

        result == allCollectionIds

    }*/

}
